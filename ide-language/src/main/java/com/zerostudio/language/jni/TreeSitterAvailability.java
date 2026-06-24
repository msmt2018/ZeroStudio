package com.zerostudio.language.jni;

import android.util.Log;

/**
 * Detects whether the native Tree-Sitter runtime (and the C / C++ grammars)
 * is loadable in the current process.
 *
 * <p>The ide-language library is happy to run with just the hand-written
 * token-based parsers; this class is a hint that {@link com.zerostudio.language.parser.CParser}
 * and {@link com.zerostudio.language.parser.CppParser} can use to switch to a
 * higher-fidelity path when the native libraries are present.
 *
 * <h2>Wiring it up</h2>
 *
 * The Tree-Sitter JNI library is shipped by the
 * {@code :editor:tree-sitter-ndk:android-tree-sitter} module. The C / C++
 * grammars are loaded from {@code libtree-sitter-c.so} and
 * {@code libtree-sitter-cpp.so} which the NDK build places in
 * {@code jniLibs/<abi>/}.
 *
 * To enable the high-fidelity path:
 *
 * <ol>
 *   <li>Add a Gradle dependency on
 *       {@code :editor:tree-sitter-ndk:cpp} (and the equivalent for
 *       {@code tree-sitter-c} if you ship it). Those modules contain
 *       the JNI entry points and the prebuilt grammar shared libraries.</li>
 *   <li>Set the {@code ide.language.useNativeTreeSitter} system property
 *       to {@code true} at app start, or call
 *       {@link #forceEnable(boolean)} to override the auto-detection.</li>
 * </ol>
 */
public final class TreeSitterAvailability {

    private static final String TAG = "ide-language";
    private static volatile Boolean cached;
    private static volatile boolean forced = false;

    private TreeSitterAvailability() {}

    /**
     * @return {@code true} if the Tree-Sitter JNI library and the C and C++
     *         grammars can be loaded in this process. Result is cached.
     */
    public static boolean isAvailable() {
        Boolean c = cached;
        if (c != null) return c;
        synchronized (TreeSitterAvailability.class) {
            if (cached != null) return cached;
            boolean detected = forced || detect();
            cached = detected;
            return detected;
        }
    }

    /** Force-enable (or force-disable) the native path. Useful for tests. */
    public static void forceEnable(boolean enabled) {
        forced = enabled;
        cached = enabled;
    }

    private static boolean detect() {
        // Step 1: check the system property override.
        String prop = System.getProperty("ide.language.useNativeTreeSitter");
        if (prop != null) {
            if ("true".equalsIgnoreCase(prop) || "1".equals(prop)) {
                Log.i(TAG, "Tree-Sitter enabled via system property.");
                return true;
            }
            if ("false".equalsIgnoreCase(prop) || "0".equals(prop)) {
                Log.i(TAG, "Tree-Sitter disabled via system property.");
                return false;
            }
        }
        // Step 2: try to load the JNI library. We use Class.forName because
        // System.loadLibrary requires a class from the same package as the
        // JNI registration; the class is in the tree-sitter-ndk module.
        try {
            Class.forName("com.itsaky.androidide.treesitter.TSLanguage");
            // Probe the grammar libraries explicitly.
            System.loadLibrary("tree-sitter");
            System.loadLibrary("tree-sitter-c");
            System.loadLibrary("tree-sitter-cpp");
            Log.i(TAG, "Tree-Sitter JNI + C/C++ grammars loaded successfully.");
            return true;
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Tree-Sitter JNI not on classpath; using token-based fallback.");
            return false;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Tree-Sitter JNI present but .so not loadable: " + e.getMessage());
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "Tree-Sitter availability check failed: " + t);
            return false;
        }
    }

    /** For tests: forget the cached value so detection runs again. */
    public static void resetForTests() {
        synchronized (TreeSitterAvailability.class) {
            cached = null;
        }
    }
}
