-ignorewarnings

#-dontwarn **
#-dontnote **
#-dontobfuscate

-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.script.**
-dontwarn javax.servlet.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn jdk.internal.misc.**
-dontwarn org.joni.ast.QuantifierNode

# H2 数据库依赖了 JTS (Java Topology Suite)，它也不在 Android SDK 中
-dontwarn org.locationtech.jts.**

# 某些IDE相关的工具类可能会引用这些
-dontwarn com.sun.jdi.**
-dontwarn org.jdesktop.**


# ===================================================================
# 2. 保留 Kotlin 语言特性所需的规则 (非常重要)
#    这些规则确保 Kotlin 的反射、元数据、协程和数据类在混淆后能正常工作。
# ===================================================================
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep public class * extends kotlin.coroutines.jvm.internal.BaseContinuationImpl

# 保留所有Kotlin元数据注解，这是Kotlin正常工作的核心
-keep @kotlin.Metadata class * {
    *;
}

# 保留 data 类的 componentN 和 copy 方法
-keepclassmembers class * extends kotlin.jvm.internal.KotlinBase {
    public <init>(...);
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# 保留所有 Parcelable 实现，防止 Android 系统序列化失败
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
  public <init>(android.os.Parcel);
}

# H2 Database
-keep class org.h2.** { *; }
-dontwarn org.h2.**

# Logback
-keep class ch.qos.logback.** { *; }
-dontwarn ch.qos.logback.**

# SLF4J helper classes used at runtime by SLF4J's MessageFormatter to perform parameter
# substitution. R8 has stripped these in past builds, causing
# "java.lang.NoClassDefFoundError: org.slf4j.helpers.FormattingTuple" crashes at runtime when
# the IDE tries to format a log message. Keep them explicitly.
-keep class org.slf4j.helpers.FormattingTuple { *; }
-keep class org.slf4j.helpers.MessageFormatter { *; }
-keep class org.slf4j.helpers.FormattingTuple$** { *; }
-keep class org.slf4j.helpers.MessageFormatter$** { *; }
-dontwarn org.slf4j.helpers.**

# JGit
#-dontwarn org.eclipse.jgit.**

-dontwarn javassist.**
-keep class javassist.** { *; }

# Gson / Moshi 等 JSON 库 - 如果您在使用，请保留模型类
# 这是一个通用规则，可以防止所有 `models` 包下的类被混淆
-keep class **.models.** { *; }

# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义 View 的构造函数，以便布局加载器可以实例化它们
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 保留枚举类的 values() 和 valueOf() 方法
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn java.lang.module.**

-dontwarn com.intellij.**
-dontwarn java.lang.instrument.**
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn com.sun.**
-dontwarn org.jline.**
-dontwarn kotlin.annotations.**
-dontwarn java.util.**
-dontwarn com.google.**
-dontwarn org.jdom.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**

# 某些库可能需要保留其注解，以防被 R8 移除。
-keep @com.google.auto.service.AutoService class *
-keepclassmembers class ** {
    @com.google.auto.service.AutoService <methods>;
}

-keep class com.facebook.ktfmt.** { *; }
-keep class org.javacs.kt.** { *; }

-keep class javax.** { *; }
-keep class jdkx.** { *; }

# keep javac classes
-keep class openjdk.** { *; }

# Android builder model interfaces
#-keep class com.android.** { *; }

# Tooling API classes
-keep class com.itsaky.androidide.tooling.** { *; }

# Builder model implementations
-keep class com.itsaky.androidide.builder.model.** { *; }

# Eclipse
-keep class org.eclipse.** { *; }

# JAXP
-keep class jaxp.** { *; }
-keep class org.w3c.** { *; }
-keep class org.xml.** { *; }

# Services
-keep @com.google.auto.service.AutoService class ** {
}
-keepclassmembers class ** {
    @com.google.auto.service.AutoService <methods>;
}

# EventBus
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

# Accessed reflectively
-keep class io.github.rosemoe.sora.widget.component.EditorAutoCompletion {
    io.github.rosemoe.sora.widget.component.EditorCompletionAdapter adapter;
    int currentSelection;
}
-keep class com.itsaky.androidide.projects.util.StringSearch {
    packageName(java.nio.file.Path);
}
-keep class * implements org.antlr.v4.runtime.Lexer {
    <init>(...);
}
-keep class * extends com.itsaky.androidide.lsp.java.providers.completion.IJavaCompletionProvider {
    <init>(...);
}
-keep class com.itsaky.androidide.editor.api.IEditor { *; }
-keep class * extends com.itsaky.androidide.inflater.IViewAdapter { *; }
-keep class * extends com.itsaky.androidide.inflater.drawable.IDrawableParser {
    <init>(...);
    android.graphics.drawable.Drawable parse();
    android.graphics.drawable.Drawable parseDrawable();
}
-keep class com.itsaky.androidide.utils.DialogUtils {  public <methods>; }

# APK Metadata
-keep class com.itsaky.androidide.models.ApkMetadata { *; }
-keep class com.itsaky.androidide.models.ArtifactType { *; }
-keep class com.itsaky.androidide.models.MetadataElement { *; }

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

# Used in preferences
-keep enum org.eclipse.lemminx.dom.builder.EmptyElements { *; }
-keep enum com.itsaky.androidide.xml.permissions.Permission { *; }

# There are some fields as well that are accessed from native field
-keepclasseswithmembers class ** {
    native <methods>;
}


# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Stat uploader
-keep class com.itsaky.androidide.stats.** { *; }

# Gson
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

## Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

## Themes
-keep enum com.itsaky.androidide.ui.themes.IDETheme {
  *;
}

## Contributor models - deserialized with GSON
-keep class * implements com.itsaky.androidide.contributors.Contributor {
  *;
}

# Suppress wissing class warnings
## These are used in annotation processing process in the Java Compiler
-dontwarn sun.reflect.annotation.AnnotationParser
-dontwarn sun.reflect.annotation.AnnotationType
-dontwarn sun.reflect.annotation.EnumConstantNotPresentExceptionProxy
-dontwarn sun.reflect.annotation.ExceptionProxy

## Used in Logback. We do not need this though.
-dontwarn jakarta.servlet.ServletContainerInitializer

## These are used in JGit
## TODO(itsaky): Verify if it is safe to ignore these warnings
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid


-keep class kotlin.** { *; }
-keep class io.realm.** { *; }
-keep @io.realm.annotations.RealmClass class * { *; }
-keep class com.fasterxml.jackson.** { *; }

-dontwarn java.lang.Runtime$Version
-dontwarn org.jetbrains.kotlin.**
-dontwarn kotlin.annotations.**
-dontwarn kotlin.script.experimental.**
-dontwarn kotlin.reflect.**
-dontwarn kotlinx.collections.immutable.**
-dontwarn com.intellij.**

-dontwarn openjdk.tools.jdeps.**
-dontwarn org.jdesktop.**
-dontwarn org.locationtech.jts.**
-dontwarn sun.reflect.annotation.**

-dontwarn kotlin.Cloneable$DefaultImpls
#-dontwarn com.android.builder.model.**
-dontwarn io.opentelemetry.**

-dontwarn javax.inject.**
-dontwarn org.gradle.internal.impldep.**
-dontwarn org.picocontainer.**

#-keep class com.itsaky.androidide.** { *; }

-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}


# Protobuf generated classes used by the AAPT resource pipeline.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# 忽略 BlockHound 的缺失引用
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
