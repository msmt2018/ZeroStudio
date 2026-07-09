package com.catpuppyapp.puppygit.jni;

import android.util.Log;

/**
 * Loads the JNI native libraries used by the puppygit module.
 *
 * <p>All five shared objects (libcrypto, libssl, libssh2, libgit2, libpuppygit)
 * are expected to ship as prebuilt artifacts under
 * {@code src/main/jniLibs/<abi>/} in this module. The
 * {@code core/git/build.gradle.kts} skips the CMake build (which would
 * link {@code libpuppygit.so} from these inputs) when the prebuilt
 * {@code libgit2.so} for any ABI is missing from the checkout.
 *
 * <p>Consequence: in builds where the prebuilt native libs are absent,
 * every {@code System.loadLibrary} call below will throw
 * {@link UnsatisfiedLinkError}. The Kotlin/Java layer still compiles,
 * but any code path that actually hits puppygit (e.g. opening a repo,
 * reading a commit) will crash at runtime.
 */
public class LibLoader {
    private static final String TAG="LibLoader";

    static {
        Log.d(TAG, "loading c libs...");

        System.loadLibrary("crypto");
        System.loadLibrary("ssl");
        System.loadLibrary("ssh2");
        System.loadLibrary("git2");
//        System.loadLibrary("git24j");
        System.loadLibrary("puppygit");

        Log.d(TAG, "c libs loaded");

    }


    public static void load() {
        //象征性的空方法，没必要实现，加载这个类的class的时候就会执行静态代码块加载动态库了
        Log.d(TAG, "load() is a stub method");
    }
}
