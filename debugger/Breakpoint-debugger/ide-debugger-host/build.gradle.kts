import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.NoDesugarPlugin

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

apply { plugin(NoDesugarPlugin::class.java) }

description =
    "ide-debugger-host: Host-side Android Debug Runtime (ADRT). Runs INSIDE " +
        "the host application process to bridge its JDWP socket to the IDE. " +
        "This module is referenced by ide-log-plugin (which is auto-injected " +
        "into the debug variant of every project built with ZeroStudio) and " +
        "by Shizuku's host-plugin / Root attach-agent paths."

android {
  namespace = "${BuildConfig.packageName}.zerostudio.ide.debugger.host"

  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
    // 提供 manifest placeholder 默认值, 这样 AAR 的 merged manifest 在库构建时
    // 就把 ${ideLocalServerName} 替换为字面值 "ide-debug-bridge", 消费方 (用户
    // app) 不需要再自己提供这个 placeholder.
    //
    // 之前这个默认值只在 IdeDebuggerInitScriptPlugin 里通过 reflection 注入到
    // host app 的 defaultConfig.manifestPlaceholders, 但 IDE 写给用户工程的
    // init script (GradleBuildService.createLoggerInitScript) 并没有 apply 这个
    // plugin, 导致 ${ideLocalServerName} 在用户 app 构建时无人解析, 报:
    //   "requires a placeholder substitution but no value for <ideLocalServerName>
    //    is provided"
    //
    // 值 "ide-debug-bridge" 与 IdeDebuggerInitScriptPlugin.computeLocalServerName()
    // 返回的固定常量一致, 也与 IDE 端 HostBridgeServer.WELL_KNOWN_NAME 对齐.
    manifestPlaceholders["ideLocalServerName"] = "ide-debug-bridge"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    aidl = false
    viewBinding = false
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

dependencies {
  // AndroidX annotation / coroutines (host process may not have full IDE deps)
  compileOnly(libs.androidx.core.ktx)
  compileOnly(libs.androidx.annotation)
  implementation(libs.common.kotlin.coroutines.android)

  // unit tests
  testImplementation(libs.tests.junit)
}
