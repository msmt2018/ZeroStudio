# Add project specific ProGuard rules here.
# By default, the flags in this file are applied to other files as well.
# You should enable this in build.gradle.kts: isMinifyEnabled = true

# WebView — keep JS interface (addJavascriptInterface 注入对象)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# DevToolsBridge — 反射读 /proc/net/unix (无反射, 但保留 LocalSocket 相关)
-keep class android.net.LocalSocket { *; }
-keep class android.net.LocalSocketAddress { *; }
