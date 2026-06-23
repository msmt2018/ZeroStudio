# CFR decompiler is bundled as a binary JAR and is consumed by the
# decompiler module. None of CFR's internal classes leak to consumers.
-keep class com.zerostudio.decompiler.api.** { *; }
