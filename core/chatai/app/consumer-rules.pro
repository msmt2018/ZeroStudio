# consumer-rules.pro
# ---------------------------------------------------------------------------
# 这些规则会随 AAR 一起发布到消费方 (:core:app), 当消费方启用
# isMinifyEnabled = true 时生效。 当前 :core:app 的 release 仍为
# isMinifyEnabled = false, 所以这文件暂时不生效; 但写在这里能保证
# 一旦 :core:app 启用 R8, chatai/app 的反射入口仍然能保留。
# ---------------------------------------------------------------------------

# 外部反射入口
-keep class me.rerere.rikkahub.RikkaHubRuntime { *; }
-keep class me.rerere.rikkahub.RikkaHubRuntime$* { *; }

# DI 模块
-keep class me.rerere.rikkahub.di.** { *; }
-keep class me.rerere.rikkahub.di.**$* { *; }

# Koin 反射式 DI
-keep class org.koin.core.** { *; }
-keep class org.koin.dsl.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * extends org.koin.core.module.Module { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn org.koin.**
-dontwarn kotlin.reflect.jvm.internal.**
