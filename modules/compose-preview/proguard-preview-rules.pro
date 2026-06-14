# v2.4 P0: Compose Preview 专用 R8 / ProGuard 规则.
#
# 配合 modules/compose-preview/build.gradle.kts 的:
#   buildTypes.release { isMinifyEnabled = true; isShrinkResources = true }
#
# 目标: 保留 Preview 运行时所需的所有反射目标 / 注解 / 静态字段, 同时让 R8 删 unused.

# === 1. 保留 Compose 注解 (反射扫描) ===
# LiveEditCoordinator / PreviewSourceParser 等都反射读 @Preview / @Composable
-keep,allowobfuscation @interface androidx.compose.runtime.Composable
-keep,allowobfuscation @interface androidx.compose.ui.tooling.preview.Preview
-keep,allowobfuscation @interface androidx.compose.ui.tooling.preview.PreviewParameter

# === 2. 保留 LiveLiterals 静态 int 字段 (v2.2 P0+) ===
# Compose Compiler 1.5+ 生成 LiveLiterals$KtFileName 类的 static int 字段, 反射写值
-keepclassmembers class **$LiveLiterals* {
    public static <fields>;
    public static <methods>;
}
# 兜底: LiveLiterals 命名空间下所有类
-keep class **.LiveLiterals** { *; }

# === 3. 保留 @Preview 标记的 Composable 函数签名 ===
# v2.2 P3 ComposableRenderer 通过 Class.getMethod() / MethodHandle 调, 需保留签名
-keepclassmembers,allowobfuscation class * {
    @androidx.compose.runtime.Composable <methods>;
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
# 保持方法参数名 (调试错误堆栈可读)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes MethodParameters

# === 4. 保留 PreviewParameterProvider 及其 values() 字段 ===
# v2.3 P1 反射调用 provider.getValues()
-keep,allowobfuscation interface androidx.compose.ui.tooling.preview.PreviewParameterProvider
-keepclassmembers,allowobfuscation class * implements androidx.compose.ui.tooling.preview.PreviewParameterProvider {
    public <fields>;
    public <methods>;
}

# === 5. 保留 ViewModel 构造 (ComposePreviewViewModel 反射 instantiate) ===
# Hilt / Compose ViewModel 不需要, 但保守保留
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# === 6. 保留 Coroutines internal 符号 ===
# Compose runtime + flow 内部类被 kotlinx-coroutines 引用
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# === 7. 保留 LiveLiterals 反射 helper 用到的 Class.getDeclaredConstructor ===
# v2.2 P0 LiveLiteralScanner 反射 newInstance()
-keepclassmembers,allowobfuscation class * {
    public <init>();
    public <init>(...);
}

# === 8. 关闭 R8 fullMode 警告 (compose-compiler 生成代码) ===
-dontwarn com.itsaky.androidide.compose.preview.**
-dontwarn org.slf4j.**
-dontwarn org.ow2.asm.**

# === 9. ASM 反射 (P2-BLD-01 AsmComposeBinder 用) ===
-keep class org.ow2.asm.** { *; }
-keep class org.ow2.asm.tree.** { *; }
-keep class org.ow2.asm.commons.** { *; }

# === 10. R8 不要优化掉 throw / catch (调试时需要 stack) ===
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
