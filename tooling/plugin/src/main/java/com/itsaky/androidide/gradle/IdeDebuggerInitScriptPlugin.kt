/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  Phase C5: the debugger init-script plugin. It is the IDE-side
 *  companion to the host application's JDWP server: it injects
 *  the :ide-log-plugin AAR (already wired by [IdeLogInitScriptPlugin])
 *  AND registers a synthetic ContentProvider whose only job is to
 *  start the JDWP server as early as possible during process
 *  startup.
 *
 *  The ContentProvider approach is used because Android creates
 *  ContentProviders before Application.onCreate(), which means we
 *  can attach the JDWP listener to the loopback interface before
 *  any user code runs. The synthetic provider is registered with
 *  a fixed authority "com.zerostudio.debugger.bootstrap" and
 *  exposes a no-op query() / insert() / update() / delete() that
 *  the OS calls once during instantiation.
 *
 *  The provider class itself lives in the :ide-log-plugin AAR
 *  (com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider)
 *  so this plugin only needs to register the manifest entry.
 */

package com.itsaky.androidide.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.itsaky.androidide.buildinfo.BuildInfo
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.logging.Logging

/**
 * -------------------------------------------------------------------------------------------------
 * 类名：IdeDebuggerInitScriptPlugin
 * 用途与功能：
 *   Init-script 插件，负责将 JDWP bootstrap ContentProvider 动态织入目标 Android 应用的 debug 变体中。
 *   适配 AGP 9.4.1 及 Kotlin 2.4.10+ 编译环境。
 * 工作流程线路图：
 *   1. apply() -> 检查是否应用了 Android 应用插件 (App Plugin)。
 *   2. registerBootstrapProvider() -> 获取 androidComponents 扩展。
 *   3. beforeVariants / onVariants -> 筛选 debuggable 变体。
 *   4. 注入 AAR 依赖 (:ide-log-plugin & :ide-debugger-host) -> 注入 Manifest Placeholders -> 生成并挂载源码。
 * 上下文关系与父类：
 *   实现 Gradle 的 [Plugin] 接口，作用域为 [Project]。
 * 使用方法教程文档：
 *   在根工程或子工程的 init-script 中通过 `apply<IdeDebuggerInitScriptPlugin>()` 引入。
 * -------------------------------------------------------------------------------------------------
 * @author android_zero
 * 历史贡献者：itsaky
 */
class IdeDebuggerInitScriptPlugin : Plugin<Project> {

  companion object {
    /** 综合引导 ContentProvider 的 Authority 常量。 */
    const val BOOTSTRAP_AUTHORITY = "com.zerostudio.debugger.bootstrap"

    /** 引导 ContentProvider 的完整类名。 */
    const val BOOTSTRAP_PROVIDER_CLASS =
        "com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider"

    /** 用于读取 JDWP 端口的 <meta-data> 名称。 */
    const val BOOTSTRAP_META_PORT = "com.zerostudio.debugger.PORT_HINT"

    /**
     * 子项目 9d: 新增 host ADRT AAR + Manifest placeholder 注入。
     *
     * IDE_LOG_PLUGIN_ARTIFACT: 注入 host 端 JdwpServer / LogCaptureService
     * IDE_DEBUGGER_HOST_ARTIFACT: 注入 host ADRT runtime
     *   - HostAttachAgent (app_process 入口)
     *   - HostAttachAgentBootstrap (ContentProvider)
     *   - HostPluginService (Shizuku InHostPlugin)
     *   - HostSocksServer (Socks 路径)
     */
    const val IDE_DEBUGGER_HOST_ARTIFACT = "ide-debugger-host"

    /** Manifest placeholder 键（用于注入 IDE LocalServerSocket 名称）。 */
    const val IDE_LOCAL_SERVER_NAME_PLACEHOLDER = "ideLocalServerName"

    private val logger = Logging.getLogger(IdeDebuggerInitScriptPlugin::class.java)
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：apply
   * 用途与功能：
   *   插件入口点。检查当前 Project 是否包含 Android Application 插件，如果是则注册调试引导相关逻辑。
   * 工作流程：
   *   [验证测试环境/日志输出] -> [检查 target 是否包含 APP_PLUGIN] -> [调用 registerBootstrapProvider]
   * -------------------------------------------------------------------------------------------------
   */
  override fun apply(target: Project) {
    if (target.isTestEnv) {
      logger.lifecycle("Applying ${javaClass.simpleName} to '${target.path}'")
    }

    target.run {
      if (!plugins.hasPlugin(APP_PLUGIN)) {
        logger.debug("Skipping ${target.path}: not an Android application")
        return@run
      }

      try {
        registerBootstrapProvider(target)
      } catch (e: Throwable) {
        logger.warn(
            "Could not register debugger bootstrap provider in ${target.path}: ${e.message}"
        )
      }
    }
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：computeLocalServerName
   * 用途与功能：
   *   计算 host 端要反连的 IDE LocalServerSocket 名字。纯函数设计，便于单元测试。
   * 返回值：
   *   固定常量 "ide-debug-bridge"，与 IDE 端 `HostBridgeServer.WELL_KNOWN_NAME` 保持严格对齐。
   * -------------------------------------------------------------------------------------------------
   */
  @Suppress("UNUSED_PARAMETER")
  internal fun computeLocalServerName(group: String?, name: String): String {
    return "ide-debug-bridge"
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：computeBootstrapPlaceholders
   * 用途与功能：
   *   子项目 10 (BuildTimeInjector): 计算 AGP 生成器所需的 placeholder 键值对映射。
   * -------------------------------------------------------------------------------------------------
   */
  internal fun computeBootstrapPlaceholders(
      group: String?,
      name: String,
      sdkInt: Int = 0,
      preheatBreakpoints: String? = null,
  ): Map<String, String> {
    val localServerName = computeLocalServerName(group, name)
    val extras = "sdk=$sdkInt"
    return mapOf(
        "${BOOTSTRAP_PROVIDER_CLASS}.AUTHORITY" to BOOTSTRAP_AUTHORITY,
        "${BOOTSTRAP_PROVIDER_CLASS}.META_PORT" to "0",
        IDE_LOCAL_SERVER_NAME_PLACEHOLDER to localServerName,
        "ideDebuggerExtras" to extras,
        "ideDebuggerPreheatBreakpointsRaw" to (preheatBreakpoints ?: ""),
    )
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：registerBootstrapProvider
   * 用途与功能：
   *   核心注册逻辑。通过 AGP 9.4.1 的 `ApplicationAndroidComponentsExtension` 遍历目标 Variant，
   *   完成运行时 AAR 依赖注入、Manifest Placeholders 注入以及引导源码生成。
   * -------------------------------------------------------------------------------------------------
   */
  private fun registerBootstrapProvider(project: Project) {
    val ext = project.extensions.findByName("androidComponents")
        as? ApplicationAndroidComponentsExtension
        ?: throw GradleException(
            "androidComponents extension not found; " +
                "is the Android Gradle plugin applied?"
        )

    val debuggableBuilds = hashSetOf<String>()
    ext.beforeVariants { variantBuilder ->
      if (variantBuilder.debuggable) {
        debuggableBuilds.add(variantBuilder.name)
      }
    }

    ext.onVariants { variant: ApplicationVariant ->
      if (variant.name !in debuggableBuilds) return@onVariants

      // 1. 注入 :ide-log-plugin 与 :ide-debugger-host 运行时 AAR 依赖
      try {
        variant.withRuntimeConfiguration {
          listOf(
              IdeLogInitScriptPlugin.IDE_LOG_PLUGIN_ARTIFACT,
              IDE_DEBUGGER_HOST_ARTIFACT,
          ).forEach { artifact ->
            val dep = project.dependencies.ideDependency(
                LIB_GROUP_TOOLING, artifact, project.isTestEnv
            )
            if (dep is ExternalModuleDependency) {
              dep.isChanging = false
              dep.version { it.strictly(BuildInfo.VERSION_NAME) }
            }
            dependencies.add(dep)
          }
        }
      } catch (e: Throwable) {
        logger.warn("runtime classpath injection failed: ${e.message}")
      }

      // 2. 注入 Manifest Placeholders
      try {
        val placeholders = computeBootstrapPlaceholders(
            group = project.group?.toString(),
            name = project.name,
            sdkInt = project.findProperty("ideDebuggerSdkInt")?.toString()?.toIntOrNull() ?: 0,
            preheatBreakpoints = project.findProperty("ideDebuggerPreheatBreakpoints")?.toString(),
        )
        variant.withManifestPlaceholders(project, placeholders)
      } catch (e: Throwable) {
        logger.warn("manifest placeholder injection failed: ${e.message}")
      }

      // 3. 子项目 10: 生成 IdeDebuggerBootstrap.kt 并挂载至现代化 Kotlin/Java 源码集
      try {
        generateIdeDebuggerBootstrapSource(variant, project)
      } catch (e: Throwable) {
        logger.warn("IdeDebuggerBootstrap.kt generation failed: ${e.message}")
      }
    }
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：generateIdeDebuggerBootstrapSource
   * 用途与功能：
   *   生成 `IdeDebuggerBootstrap.kt` 源文件至 build 目录下，并适配 AGP 9.4.1 的源码集管理方式挂载。
   * -------------------------------------------------------------------------------------------------
   */
  private fun generateIdeDebuggerBootstrapSource(
      variant: ApplicationVariant,
      project: Project,
  ) {
    val sdkInt = project.findProperty("ideDebuggerSdkInt")?.toString()?.toIntOrNull() ?: 0
    val preheatRaw = project.findProperty("ideDebuggerPreheatBreakpoints")?.toString()
    val preheatBps = try {
      parsePreheatBreakpoints(preheatRaw)
    } catch (e: IllegalArgumentException) {
      logger.warn("ideDebuggerPreheatBreakpoints format error, using empty list: ${e.message}")
      emptyList()
    }
    val localServerName = computeLocalServerName(
        group = project.group?.toString(),
        name = project.name,
    )
    val content = renderIdeDebuggerBootstrapKt(
        ideVersion = BuildInfo.VERSION_NAME,
        localServerName = localServerName,
        extras = "sdk=$sdkInt",
        buildTimestampMs = System.currentTimeMillis(),
        preheatBreakpoints = preheatBps,
    )

    val buildDir = project.layout.buildDirectory.asFile.get()
    val generatedDir = File(buildDir, "generated/source/ide_debugger/${variant.name}/kotlin")
    val pkgPath = "com/itsaky/androidide/zerostudio/ide/debugger/host/generated"
    val targetFile = File(generatedDir, "$pkgPath/IdeDebuggerBootstrap.kt")
    targetFile.parentFile.mkdirs()
    targetFile.writeText(content)

    // 适配 AGP 9.4.1: 使用现代 Variant Sources API 挂载生成的 Kotlin 源码目录
    try {
      val sourcesMethod = variant::class.java.methods.firstOrNull { it.name == "getSources" }
      val sourcesObj = sourcesMethod?.invoke(variant)
      val javaSourcesObj = sourcesObj?.let {
        it::class.java.methods.firstOrNull { m -> m.name == "getJava" }?.invoke(it)
      }
      val addMethod = javaSourcesObj?.let {
        it::class.java.methods.firstOrNull { m -> m.name == "addStaticSourceDirectory" || m.name == "add" }
      }
      if (addMethod != null) {
        addMethod.invoke(javaSourcesObj, generatedDir)
      } else {
        // 兼容降级：直接向通用源码集注册
        project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
            ?.sourceSets?.findByName(variant.name)
            ?.java?.srcDir(generatedDir)
      }
    } catch (t: Throwable) {
      logger.debug("Modern variant sources registration fallback triggered: ${t.message}")
    }

    logger.lifecycle(
        "Generated IdeDebuggerBootstrap.kt for variant '${variant.name}' " +
            "in ${project.path}: $targetFile (extras=sdk=$sdkInt, " +
            "preheatBpCount=${preheatBps.size})"
    )
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：withRuntimeConfiguration
   * 用途与功能：
   *   跨 AGP 版本兼容扩展函数，用于安全获取变体的运行时配置（Runtime Configuration）。
   * -------------------------------------------------------------------------------------------------
   */
  private fun ApplicationVariant.withRuntimeConfiguration(action: org.gradle.api.artifacts.Configuration.() -> Unit) {
    when (this) {
      is ApplicationVariantImpl -> variantDependencies.runtimeClasspath.action()
      else -> {
        try {
          val m = this::class.java.methods.firstOrNull {
            it.name in arrayOf(
                "getRuntimeConfiguration",
                "getRuntimeElements",
                "getRuntimeClasspath",
            )
          }
          val cfg = m?.invoke(this) as? org.gradle.api.artifacts.Configuration
          cfg?.action()
        } catch (t: Throwable) {
          logger.debug("withRuntimeConfiguration fallback failed: ${t.message}")
        }
      }
    }
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：withManifestPlaceholders
   * 用途与功能：
   *   将 Manifest Placeholders 注入到 AGP 变体默认配置中。
   * -------------------------------------------------------------------------------------------------
   */
  private fun ApplicationVariant.withManifestPlaceholders(
      project: Project,
      values: Map<String, String>
  ) {
    if (values.isEmpty()) return
    try {
      val placeholdersMap = resolveManifestPlaceholdersMap(project)
      if (placeholdersMap == null) {
        logger.warn("manifest placeholders map not found for variant '${name}'; placeholders NOT injected. " +
                "Check AGP version compatibility.")
        return
      }
      synchronized(placeholdersMap) {
        for ((k, v) in values) {
          placeholdersMap[k] = v
        }
      }
      logger.lifecycle(
          "Injected ${values.size} manifest placeholder(s) for variant '${name}' " +
              "in ${project.path}: ${values.keys.joinToString()}"
      )
    } catch (t: Throwable) {
      logger.warn("manifest placeholder injection failed for ${name} in ${project.path}: ${t.message}")
    }
  }

  /**
   * -------------------------------------------------------------------------------------------------
   * 方法名：resolveManifestPlaceholdersMap
   * 用途与功能：
   *   通过反射机制跨 AGP 9.4.1 版本安全提取 `defaultConfig.manifestPlaceholders` 可变映射。
   * -------------------------------------------------------------------------------------------------
   */
  private fun resolveManifestPlaceholdersMap(project: Project): MutableMap<String, Any>? {
    return runCatching {
      val androidExt = project.extensions.findByName("android")
        ?: return@runCatching null
      val defaultConfig = androidExt::class.java.methods
          .firstOrNull { it.name == "getDefaultConfig" && it.parameterCount == 0 }
          ?.invoke(androidExt)
        ?: return@runCatching null
      defaultConfig::class.java.methods
          .firstOrNull { it.name == "getManifestPlaceholders" && it.parameterCount == 0 }
          ?.invoke(defaultConfig) as? MutableMap<String, Any>
    }.getOrNull()
  }
}