# Add project specific ProGuard rules here.
# -keep class com.itsaky.androidide.zerostudio.ide.debugger.host.** { *; }
# host ADRT 的类需要在混淆后仍能被动态加载 / 反射调用
-keep class com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgent { *; }
-keep class com.itsaky.androidide.zerostudio.ide.debugger.host.HostPluginService { *; }
