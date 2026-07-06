-keep class com.zerostudio.debugger.** { *; }
-keepclassmembers class com.zerostudio.debugger.jdwp.** {
    public static final ** COMMAND_SET_*;
    public static final int ERROR_*;
    public static final int EVENT_KIND_*;
    public static final int SUSPEND_POLICY_*;
    public static final int STEP_*;
    public static final int MOD_KIND_*;
}
