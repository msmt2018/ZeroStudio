# 保留 JNI 入口 (Fragment 的 native 方法 + System.loadLibrary 名)
-keep class com.zerostudio.preview.UniversalPreviewEngineFragment {
    native <methods>;
}
