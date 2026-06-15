# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# keep kotlinx serializable classes
-keep @kotlinx.serialization.Serializable class * {*;}

# keep jlatexmath
-keep class org.scilab.forge.jlatexmath.** {*;}

-dontwarn com.google.re2j.**
-dontobfuscate

# Ktor 在 Android 上引用了仅 JVM 可用的 java.lang.management 类（IntellijIdeaDebugDetector）
# Android 不包含这些类，需要告知 R8 忽略
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# java.beans is not available on Android; Jackson references it only on JVM
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# auth0/jackson: TypeReference subclasses rely on runtime generic signatures.
# R8 strips Signature/InnerClasses/EnclosingMethod by default, and its class
# merging/inlining optimizations can destroy the anonymous class hierarchy that
# TypeReference.<init> depends on via getClass().getGenericSuperclass().
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.fasterxml.jackson.** { *; }
-keep class com.auth0.jwt.** { *; }

# ---------------------------------------------------------------------------
# RikkaHub Koin bootstrap (被 :core:app IDEApplication 反射调用)
# ---------------------------------------------------------------------------
# chatai/app 合并进 :core:app 后, 自己的 AndroidManifest 没有注册
#   android:name=".RikkaHubApp"
# (否则会与 :core:app 的 IDEApplication 冲突, 见
#  scripts/chatai/reapply-integrations.sh)。 因此 R8 看不到
# RikkaHubApp.onCreate → RikkaHubRuntime.ensureKoinStarted 的调用链,
# 会把 RikkaHubRuntime 整包当作死代码剥离。
# :core:app 的 release 也是 isMinifyEnabled = false, 不会在消费方再跑 R8,
# 所以 keep 规则必须放在 chatai/app 这一侧 (即本文件), 而不是
# consumer-rules.pro (后者只在消费方跑 R8 时生效)。

# 整个 RikkaHub 包: 包含被外部反射调用的入口 (RikkaHubRuntime) 与
# Koin 模块 (appModule / viewModelModule / dataSourceModule /
# repositoryModule) 及其 single { ... } 工厂 lambda 内引用的所有绑定类型。
# 不保留此包会导致 NoClassDefFoundError 链式蔓延。
-keep class me.rerere.rikkahub.** { *; }
-keep interface me.rerere.rikkahub.** { *; }

# Koin 反射式 DI: Module 实例 + 工厂 lambda 的 KFunction / KClass 反射
-keep class org.koin.core.** { *; }
-keep class org.koin.dsl.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * extends org.koin.core.module.Module { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn org.koin.**
-dontwarn kotlin.reflect.jvm.internal.**

# kotlinx.coroutines 内 Koin scope 用到的协程服务
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# kotlinx.serialization: RikkaHub 大量使用 @Serializable
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class me.rerere.rikkahub.**$$serializer { *; }
-keepclassmembers class me.rerere.rikkahub.** {
    *** Companion;
}
-keepclasseswithmembers class me.rerere.rikkahub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
