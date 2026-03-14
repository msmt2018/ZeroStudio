# ============================================================================
# android-tree-sitter ProGuard/R8 Consumer Rules
# ============================================================================
# These rules are automatically applied to consumers of this library.
# JNI native code directly accesses Java fields by name, so they must not be renamed.

# Keep all tree-sitter classes and their members
-keep class com.itsaky.androidide.treesitter.** { *; }

# Keep all native methods
-keepclasseswithmembernames class com.itsaky.androidide.treesitter.** {
    native <methods>;
}

# TSNode - JNI directly accesses these fields by name
-keepclassmembers class com.itsaky.androidide.treesitter.TSNode {
    protected int context0;
    protected int context1;
    protected int context2;
    protected int context3;
    protected long id;
    protected long tree;
    protected com.itsaky.androidide.treesitter.TSTree mTree;
}

# TSNode$Native - inner class with native methods
-keep class com.itsaky.androidide.treesitter.TSNode$Native { *; }

# TSNativeObject - base class for native objects
-keepclassmembers class com.itsaky.androidide.treesitter.TSNativeObject {
    protected final java.util.concurrent.atomic.AtomicLong pointer;
}

# TSTree - JNI accesses pointer field
-keepclassmembers class com.itsaky.androidide.treesitter.TSTree {
    <fields>;
}

# TSParser - JNI accesses pointer field
-keepclassmembers class com.itsaky.androidide.treesitter.TSParser {
    <fields>;
}

# TSQuery - JNI accesses pointer field
-keepclassmembers class com.itsaky.androidide.treesitter.TSQuery {
    <fields>;
}

# TSQueryCursor - JNI accesses pointer field
-keepclassmembers class com.itsaky.androidide.treesitter.TSQueryCursor {
    <fields>;
}

# TSLanguage - JNI accesses pointer field
-keepclassmembers class com.itsaky.androidide.treesitter.TSLanguage {
    <fields>;
}

# TSTreeCursor - JNI accesses fields
-keepclassmembers class com.itsaky.androidide.treesitter.TSTreeCursor {
    <fields>;
}

# Keep all inner Native classes (they contain JNI method declarations)
-keep class com.itsaky.androidide.treesitter.**$Native { *; }

# Keep factory classes used by reflection
-keep class com.itsaky.androidide.treesitter.DefaultObjectFactory { *; }
-keep class com.itsaky.androidide.treesitter.internal.NativeObjectFactory { *; }
-keep class com.itsaky.androidide.treesitter.util.TSObjectFactoryProvider { *; }

# Keep data classes that may be created by JNI
-keep class com.itsaky.androidide.treesitter.TSPoint { *; }
-keep class com.itsaky.androidide.treesitter.TSRange { *; }
-keep class com.itsaky.androidide.treesitter.TSInputEdit { *; }
-keep class com.itsaky.androidide.treesitter.TSQueryCapture { *; }
-keep class com.itsaky.androidide.treesitter.TSQueryMatch { *; }
-keep class com.itsaky.androidide.treesitter.TSQueryPredicateStep { *; }

# Keep enums
-keepclassmembers enum com.itsaky.androidide.treesitter.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Don't warn about missing classes
-dontwarn com.itsaky.androidide.treesitter.**
