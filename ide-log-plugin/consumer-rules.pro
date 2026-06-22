# Keep all plugin classes so that they can be loaded by reflection
# when injected into a debug variant of the host application.
-keep class com.zerostudio.logplugin.** { *; }
-keep class com.zerostudio.logplugin.api.** { *; }
-keep class com.zerostudio.logplugin.capture.** { *; }
-keep class com.zerostudio.logplugin.jdwp.** { *; }
-keep class com.zerostudio.logplugin.transport.** { *; }
-keep class com.zerostudio.logplugin.plugin.** { *; }
-keep class com.zerostudio.logplugin.util.** { *; }

# Keep loggers
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }

# Keep JDWP packet constants
-keepclassmembers class com.zerostudio.logplugin.jdwp.** {
    public static final ** COMMAND_SET_*;
    public static final ** ERROR_*;
}
