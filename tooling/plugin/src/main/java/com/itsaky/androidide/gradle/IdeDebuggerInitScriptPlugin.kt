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
 * Init-script plugin that wires the JDWP bootstrap ContentProvider
 * into the target application's debug variant.
 *
 * @author ZeroStudio
 */
class IdeDebuggerInitScriptPlugin : Plugin<Project> {

  companion object {
    /** Authority of the synthetic bootstrap ContentProvider. */
    const val BOOTSTRAP_AUTHORITY = "com.zerostudio.debugger.bootstrap"

    /** Full class name of the bootstrap ContentProvider. */
    const val BOOTSTRAP_PROVIDER_CLASS =
        "com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider"

    /** <meta-data> name that the provider reads to learn the JDWP port. */
    const val BOOTSTRAP_META_PORT = "com.zerostudio.debugger.PORT_HINT"

    /**
     * 子项目 9d: 新增 host ADRT AAR + Manifest placeholder 注入。
     *
     * IDE_LOG_PLUGIN_ARTIFACT: 注入 host 端 JdwpServer / LogCaptureService (已有)
     * IDE_DEBUGGER_HOST_ARTIFACT: 注入 host ADRT runtime (子项目 8 新建)
     *   - HostAttachAgent (app_process 入口)
     *   - HostAttachAgentBootstrap (ContentProvider, 子项目 9c)
     *   - HostPluginService (Shizuku InHostPlugin)
     *   - HostSocksServer (Socks 路径)
     *   - 配套的 Manifest placeholder + provider (子项目 9c)
     */
    const val IDE_DEBUGGER_HOST_ARTIFACT = "ide-debugger-host"

    /** Manifest placeholder key (用于注入 IDE LocalServerSocket 名字) */
    const val IDE_LOCAL_SERVER_NAME_PLACEHOLDER = "ideLocalServerName"

    private val logger = Logging.getLogger(IdeDebuggerInitScriptPlugin::class.java)
  }

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
   * 子项目 9d: 计算 host 端要反连的 IDE LocalServerSocket 名字。
   * 名字 = "ide-debug-bridge-{group}-{name}", 唯一避免冲突。
   *
   * 纯函数 (不依赖 Project), 便于单测。
   */
  internal fun computeLocalServerName(group: String?, name: String): String {
    val safeGroup = (group?.takeIf { it.isNotBlank() } ?: "default").take(64)
    val safeName = (name.takeIf { it.isNotBlank() } ?: "app").take(64)
    return "ide-debug-bridge-${safeGroup}-${safeName}".lowercase()
  }

  /**
   * 子项目 10 (BuildTimeInjector): 算生成器用的 placeholder 值。
   * 纯函数, 便于单测。
   *
   * @param group Project.group (可能为 null/空, 走默认)
   * @param name Project.name
   * @param sdkInt Build.VERSION.SDK_INT 或 0 (不期望)
   * @param preheatBreakpoints 预热 bp 列表 (字符串格式, 走 parsePreheatBreakpoints)
   * @return placeholder key -> value map, 调 withManifestPlaceholders 写到 AGP
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
        // 子项目 10: 注入器生成器用的额外 placeholder
        "ideDebuggerExtras" to extras,
        "ideDebuggerPreheatBreakpointsRaw" to (preheatBreakpoints ?: ""),
    )
  }

  /**
   * Merge a provider entry into the variant's main manifest. The
   * <provider> element points at the synthetic bootstrap class
   * that lives in :ide-log-plugin; the manifest is regenerated
   * only when the user adds a manifest placeholder or a content
   * provider, so this is a no-op for projects that already use
   * AGP's standard manifest merger.
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

      // 1. Add :ide-log-plugin AAR (defensive: IdeLogInitScriptPlugin
      //    usually also does this; re-adding is harmless because
      //    the dep is the same artifact).
      // 子项目 9d: 同样注入 :ide-debugger-host AAR (host ADRT runtime)
      try {
        variant.withRuntimeConfiguration {
          listOf(
              IdeLogInitScriptPlugin.IDE_LOG_PLUGIN_ARTIFACT,
              IdeLogInitScriptPlugin.IDE_DEBUGGER_ARTIFACT,
              IDE_DEBUGGER_HOST_ARTIFACT,  // 子项目 9d: host ADRT
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

      // 2. Add a manifest placeholder so the user-visible
      //    AndroidManifest.xml doesn't need to declare the
      //    bootstrap provider. The placeholder name is the
      //    fully-qualified provider class.
      // 子项目 9d: 同样注入 ideLocalServerName placeholder, 给 HostAttachAgentBootstrap
      //   ContentProvider 读 (走 meta-data android:name="ide_local_server_name")
      // 子项目 10: 额外注入 ideDebuggerExtras + ideDebuggerPreheatBreakpointsRaw,
      //   给生成器使用 (但这些不在 host app manifest 里用, 仅供 IdeDebuggerInitScriptPlugin
      //   自己读 back 来生成 IdeDebuggerBootstrap.kt)
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

      // 3. 子项目 10: 生成 IdeDebuggerBootstrap.kt 到 generated-sources,
      //    并加到 Kotlin source set. 这样 host app build 时会编译这个 .kt,
      //    拿到最新 IDE debugger 配置 + BreakpointLocation 类型 + init API.
      try {
        generateIdeDebuggerBootstrapSource(variant, project)
      } catch (e: Throwable) {
        logger.warn("IdeDebuggerBootstrap.kt generation failed: ${e.message}")
      }
    }
  }

  /**
   * 子项目 10: 生成 IdeDebuggerBootstrap.kt 源文件到 host app 的
   * `build/generated/source/ide_debugger/{variant}/kotlin/` 目录,
   * 并加到 variant 的 Kotlin source set.
   *
   * 生成参数:
   *   - ideVersion: BuildInfo.VERSION_NAME (IDE 端版本)
   *   - localServerName: per-project 唯一 (computeLocalServerName)
   *   - extras: "sdk=<sdkInt>" (从 project property 读)
   *   - buildTimestampMs: System.currentTimeMillis()
   *   - preheatBreakpoints: 走 parsePreheatBreakpoints 解析 property
   *     (格式错 throw IAE, 这里 catch degrade 到 emptyList)
   *
   * 失败: logger.warn, 不抛 (跟其他注入一致).
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

    // 把 generatedDir 加到 Kotlin source set
    variant.sources.kotlin?.addStaticSourceDirectory(generatedDir.absolutePath)

    logger.lifecycle(
        "Generated IdeDebuggerBootstrap.kt for variant '${variant.name}' " +
            "in ${project.path}: $targetFile (extras=sdk=$sdkInt, " +
            "preheatBpCount=${preheatBps.size})"
    )
  }

  private fun ApplicationVariant.withRuntimeConfiguration(action: org.gradle.api.artifacts.Configuration.() -> Unit) {
    when (this) {
      is ApplicationVariantImpl -> variantDependencies.runtimeClasspath.action()
      else -> {
        // Fallback for AGP 8+: find a method that exposes the
        // runtime configuration without using internal APIs.
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
   * 子项目 9d + 10: 真写 manifest placeholders 到 AGP 的 manifestPlaceholders map.
   *
   * 实现: 通过 reflection 找 `defaultConfig.manifestPlaceholders` map 然后 put.
   * AGP 8.x 公开 API 不在 onVariants 块里直接暴露 defaultConfig.manifestPlaceholders
   * (改用 internal API), 走 reflection 是兼容性最好的路径。
   *
   * 失败: logger.warn, 不抛 (跟其他注入一致, 不阻塞 build).
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
   * 子项目 9d: 通过 reflection 找 `defaultConfig.manifestPlaceholders` map.
   * 路径: project.extensions.getByName("android") -> getDefaultConfig() -> getManifestPlaceholders()
   *
   * @return MutableMap (可写) 或 null (找不到时)
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
