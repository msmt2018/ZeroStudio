# modules/web-preview consumer ProGuard rules

# WebView 调试相关反射 (DevToolsBridge 扫描 /proc/net/unix)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# androidx.webkit
-keep class androidx.webkit.** { *; }
