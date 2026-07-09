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
 * <p>当 prebuilt native libs 缺失时，{@code System.loadLibrary} 会抛
 * {@link UnsatisfiedLinkError}。本类用 try/catch 逐个加载，记录失败状态，
 * 使类初始化不会失败——上层可通过 {@link #isLoaded()} 检测并在 UI 层优雅降级，
 * 而非让 {@code <clinit>} 抛出导致 {@link NoClassDefFoundError} 崩溃。
 */
public class LibLoader {
    private static final String TAG = "LibLoader";

    /** native 库是否全部加载成功。volatile：供其他线程通过 {@link #isLoaded()} 读取。 */
    private static volatile boolean loaded = false;

    /** 首个加载失败的库名（用于诊断），全部成功时为 null。 */
    private static volatile String failedLib = null;

    static {
        Log.d(TAG, "loading c libs...");

        String firstFailure = null;
        boolean allOk = true;

        // 逐个加载：任一失败记录原因但不中断后续尝试，
        // 避免类初始化抛异常导致后续引用抛 NoClassDefFoundError。
        String[] libs = {"crypto", "ssl", "ssh2", "git2", "puppygit"};
        for (String lib : libs) {
            try {
                System.loadLibrary(lib);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "failed to load lib" + lib + ": " + e.getMessage());
                if (firstFailure == null) {
                    firstFailure = lib;
                }
                allOk = false;
            }
        }

        failedLib = firstFailure;
        loaded = allOk;

        if (allOk) {
            Log.d(TAG, "c libs loaded");
        } else {
            Log.w(TAG, "some c libs failed to load (first failure: " + firstFailure
                    + "). Git features will be unavailable.");
        }
    }

    /**
     * native 库是否全部加载成功。
     *
     * @return {@code true} 所有库加载成功，可正常使用 puppygit；{@code false} 至少一个库缺失，
     *         上层应禁用 git 功能并提示用户。
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 返回首个加载失败的库名，用于诊断。
     *
     * @return 首个失败库名（如 "crypto"），全部成功时返回 {@code null}。
     */
    public static String getFailedLib() {
        return failedLib;
    }

    /**
     * 触发类加载（执行静态块）。加载结果通过 {@link #isLoaded()} 查询。
     */
    public static void load() {
        Log.d(TAG, "load() called, loaded=" + loaded);
    }
}
