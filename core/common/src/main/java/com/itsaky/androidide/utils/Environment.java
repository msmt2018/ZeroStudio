/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.itsaky.androidide.app.BaseApplication;

/**
 * AndroidIDE Environment configuration.
 * Configures file paths, system properties, and native environment variables (Os.setenv)
 * to support global JDK and SDK access.
 *
 * @author android_zero
 */
@SuppressLint("SdCardPath")
public final class Environment {
  // 关键：INITIALIZED 与 NATIVE_INJECTED 都必须是 volatile，
  // 因为 init() / injectNativeEnvironment() 会在多线程（主线程 + Termux 启动时
  // 的 Background Thread + Firebase Worker）中并发触发。
  private static volatile boolean INITIALIZED = false;
  // 幂等性旗标：保证 injectNativeEnvironment() 只执行一次 setenv，
  // 避免 putEnvironment() → ensureInitialized() → init() → inject 链路上
  // 重复注入造成无意义的 setenv 调用（性能开销 + 触发 logback 类加载风险）。
  private static volatile boolean NATIVE_INJECTED = false;

  public static final String PROJECTS_FOLDER = "AndroidIDEProjects";
  private static final Logger LOG = LoggerFactory.getLogger(Environment.class);
  public static File ROOT;
  public static File PREFIX;
  public static File HOME;
  public static File ANDROIDIDE_HOME;
  public static File ANDROID_NDK_HOME;
  public static File NDK_HOME;
  public static File ANDROIDIDE_UI;
  public static File JAVA_HOME;
  public static File ANDROID_HOME;
  public static File KOTLINC_HOME;
  public static File KOTLIN_LSP_HOME;
  public static File COMPOSE_HOME;
  public static File PLUGIN_HOME;
  public static File TMP_DIR;
  public static File BIN_DIR;
  public static File LIB_DIR;
  public static File PROJECTS_DIR;
  public static File REALM_DB_DIR;
  public static File MAVEN_REPOSITORY;
  public static File PROJETS_JAVA2KOTLIN_BAK;
  public static File PROTOC_BIN; // Protobuf 编译器
  public static File CMAKE_HOME;
  public static File CMAKE_BIN;
  
  // plugin
  public static File FORMAT_KOTLIN_KTFMT;


  public static final String PLUGIN_API_JAR_RELATIVE_PATH = "libs/plugin-api.jar";

    // Lottie 动画目录
  public static File LOTTIE_ANIMATION_DIR;
  public static File LOTTIE_EXPORT_DIR;

  /**
   * Used by Java LSP until the project is initialized.
   */
  public static File ANDROID_JAR;

  public static File TOOLING_API_JAR;

  public static File INIT_SCRIPT;
  public static File GRADLE_USER_HOME;
  public static File AAPT2;
  public static File JAVA;
  // public static File SHELL_KOTLIN_LSP;
  public static File BASH_SHELL;
  public static File LOGIN_SHELL;
  
  // KtLsp specific
  public static File KOTLIN_LSP_LIBS_JAR_DIR;
  public static File KOTLIN_LSP_LAUNCHER;
  public static File SERVERS_DIR;
  public static File SERVERS_C_CPP_DIR;
  public static File SERVERS_KOTLIN_DIR;
  public static File SERVER_CONFIG_DIR;
    
  public static File ANDROIDIDE;

  /**
   * Initializes the environment paths, system properties, and injects variables into the native OS environment.
   * This ensures that subprocesses (like Runtime.exec or ProcessBuilder) inherit the JDK environment.
   *
   * <p><b>Thread-safety:</b> double-checked locking on {@link #INITIALIZED}. The method is
   * safe to call concurrently from any thread. Re-entrant calls (e.g. from inside
   * {@link #putEnvironment(Map, boolean)} triggered by {@link #injectNativeEnvironment()}) are
   * short-circuited by setting {@link #INITIALIZED} to {@code true} <em>before</em> calling
   * {@link #injectNativeEnvironment()}.
   *
   * @param context Application context
   */
  public static void init(Context context) {
    if (INITIALIZED && ROOT != null) {
      return;
    }
    synchronized (Environment.class) {
      if (INITIALIZED && ROOT != null) {
        return;
      }
      ROOT = context.getFilesDir();
      PREFIX = mkdirIfNotExits(new File(ROOT, "usr"));
      HOME = mkdirIfNotExits(new File(ROOT, "home"));
      ANDROIDIDE_HOME = mkdirIfNotExits(new File(HOME, ".androidide"));
      TMP_DIR = mkdirIfNotExits(new File(PREFIX, "tmp"));
      BIN_DIR = mkdirIfNotExits(new File(PREFIX, "bin"));
      LIB_DIR = new File(PREFIX, "lib");
      PROJETS_JAVA2KOTLIN_BAK = new File(PROJECTS_FOLDER, ".j2k_bak");
      PROJECTS_DIR = mkdirIfNotExits(new File(FileUtil.getExternalStorageDir(), PROJECTS_FOLDER));
      ANDROID_JAR = new File(ANDROIDIDE_HOME, "android.jar");
      TOOLING_API_JAR = new File(new File(ANDROIDIDE_HOME, "tooling-api"), "tooling-api-all.jar");
      AAPT2 = new File(ANDROIDIDE_HOME, "aapt2");
      ANDROIDIDE_UI = new File(ANDROIDIDE_HOME, "ui");
      REALM_DB_DIR = new File(ROOT, "realm-dbs");
      COMPOSE_HOME = new File(ANDROIDIDE_HOME, "compose");

      INIT_SCRIPT = new File(new File(ANDROIDIDE_HOME, "init"), "init.gradle");
      GRADLE_USER_HOME = new File(HOME, ".gradle");
      MAVEN_REPOSITORY = new File(HOME, ".m2");

       // 初始化Lottie动画目录
      LOTTIE_ANIMATION_DIR = new File(ANDROIDIDE_HOME, "LottieAnimation");
      LOTTIE_EXPORT_DIR = new File(PROJECTS_DIR, "LottieAnimation");

       File java17Home = new File(PREFIX, "lib/jvm/java-17-openjdk");
       File java21Home = new File(PREFIX, "lib/jvm/java-21-openjdk");


      ANDROID_HOME = new File(HOME, "android-sdk");

      // 交叉编译变量环境
      ANDROID_NDK_HOME = new File(ANDROID_HOME, "ndk");
      NDK_HOME = ANDROID_NDK_HOME;
      CMAKE_HOME = new File(ANDROID_HOME, "cmake");
      CMAKE_BIN = new File(CMAKE_HOME, "bin");
      // Protobuf (protoc) 路径
      PROTOC_BIN = new File(PREFIX, "bin");


      KOTLINC_HOME = new File(HOME, ".kotlinc");

      //plugin
      File idePluginDir = new File(ANDROIDIDE_HOME, "ideplugin");
      PLUGIN_HOME = new File(ANDROIDIDE_HOME, "plugin");

      KOTLIN_LSP_HOME = new File(idePluginDir, "kotlinLanguageServices");
      KOTLIN_LSP_LAUNCHER = new File(KOTLIN_LSP_HOME, "bin/kotlin-language-server");
      KOTLIN_LSP_LIBS_JAR_DIR = new File(KOTLIN_LSP_HOME, "lib");
      SERVERS_KOTLIN_DIR = KOTLIN_LSP_HOME;
      SERVER_CONFIG_DIR = new File(HOME, ".config/kotlin-language-server");
      //格式化插件
      FORMAT_KOTLIN_KTFMT = new File(idePluginDir, "ktfmt");

      JAVA_HOME = new File(PREFIX, "opt/openjdk");
      ANDROIDIDE = new File(PREFIX, "share/AndroidIDE.properties");

           // SHELL_KOTLIN_LSP = new File(KOTLIN_LSP_HOME, "bin/kotlin-language-server");
      JAVA = new File(JAVA_HOME, "bin/java");
      BASH_SHELL = new File(BIN_DIR, "bash");
      LOGIN_SHELL = new File(BIN_DIR, "login");

      setExecutable(JAVA);
      setExecutable(BASH_SHELL);
      setExecutable(new File(CMAKE_BIN, "cmake"));
      setExecutable(new File(CMAKE_BIN, "ninja")); // CMake 通常配合 ninja
      setExecutable(new File(PROTOC_BIN, "protoc"));

      // 设置 Java System Properties (供 JVM 内部使用)
      System.setProperty("user.home", HOME.getAbsolutePath());
      System.setProperty("android.home", ANDROID_HOME.getAbsolutePath());
      System.setProperty("ANDROID_HOME", ANDROID_HOME.getAbsolutePath());
      System.setProperty("ANDROID_NDK", ANDROID_NDK_HOME.getAbsolutePath());
      System.setProperty("ANDROID_NDK_ROOT", ANDROID_NDK_HOME.getAbsolutePath());
      System.setProperty("ANDROID_NDK_HOME", ANDROID_NDK_HOME.getAbsolutePath());
      System.setProperty("NDK_HOME", ANDROID_NDK_HOME.getAbsolutePath());
      System.setProperty("cmake.dir", CMAKE_HOME.getAbsolutePath());
      System.setProperty("CMAKE_HOME", CMAKE_HOME.getAbsolutePath());
      // 如果使用了 Proto 插件，有时需要指定 protoc 路径
      System.setProperty("protoc", new File(PROTOC_BIN, "protoc").getAbsolutePath());
      System.setProperty("gradle.user.home", GRADLE_USER_HOME.getAbsolutePath());
      System.setProperty("kotlin.home", KOTLINC_HOME.getAbsolutePath());
      System.setProperty("kotlin.lsp.home", KOTLIN_LSP_HOME.getAbsolutePath());
      System.setProperty("java.io.tmpdir", TMP_DIR.getAbsolutePath());

      // 关键修复：必须把 INITIALIZED 置为 true 之后再 injectNativeEnvironment()。
      //
      // 之前的实现把这个赋值放在 finally 块中、inject 之后，这会导致死循环：
      //   init() -> injectNativeEnvironment() -> putEnvironment() -> ensureInitialized()
      //     -> init() -> injectNativeEnvironment() -> putEnvironment() -> ensureInitialized()
      //     -> ...
      // 原因：ensureInitialized() 的守卫是 `!INITIALIZED || ROOT == null`。
      // ROOT 在 init() 早期（第 127 行）就被赋值了，但 INITIALIZED 仍为 false，
      // 所以 `!false || false == true` 永远成立 → 重入 init()。
      //
      // 现在 INITIALIZED 在 inject 之前置位。即便 injectNativeEnvironment() 抛异常，
      // 静态路径与 System properties 已经完全初始化，应用其余部分可以正常启动。
      // 真正失败的 setenv 会被 injectNativeEnvironment() 内部的 try/catch 吞掉并写
      // System.err，不会影响主流程。
      INITIALIZED = true;

      //  注入 Native 环境变量 (供 ProcessBuilder, Runtime.exec, Terminal 使用)
      // 该方法内部已对失败情形做了保护，此处不再包 try/finally 重新设置 INITIALIZED。
      injectNativeEnvironment();
    }
  }

  private static Context resolveContext() {
    final var app = BaseApplication.getBaseInstance();
    if (app == null) {
      throw new IllegalStateException("BaseApplication is not ready yet");
    }
    return app.getApplicationContext();
  }

  private static void ensureInitialized() {
    if (!INITIALIZED || ROOT == null) {
      synchronized (Environment.class) {
        if (!INITIALIZED || ROOT == null) {
          init(resolveContext());
        }
      }
    }
  }

  public static void initSecondaryDirs() {
    ensureInitialized();
    mkdirIfNotExits(ANDROIDIDE_UI);
    mkdirIfNotExits(REALM_DB_DIR);
    mkdirIfNotExits(COMPOSE_HOME);
    mkdirIfNotExits(INIT_SCRIPT.getParentFile());
    mkdirIfNotExits(KOTLINC_HOME);
    mkdirIfNotExits(PLUGIN_HOME);
    mkdirIfNotExits(KOTLIN_LSP_HOME);
    mkdirIfNotExits(SERVER_CONFIG_DIR);
    mkdirIfNotExits(FORMAT_KOTLIN_KTFMT);
    mkdirIfNotExits(LOTTIE_ANIMATION_DIR);
    mkdirIfNotExits(LOTTIE_EXPORT_DIR);
    createFileIfNotExists(ANDROIDIDE);
  }

  /**
   * Injects environment variables into the native process environment using android.system.Os.setenv.
   * This is critical for making 'java', 'javac', and other tools available globally to child processes.
   *
   * <p><b>Important:</b> this method must NEVER call {@link #LOG} (SLF4J) for normal-path logging
   * because the logback pipeline can, in certain initialization orderings, eventually call
   * {@link #putEnvironment(Map, boolean)} on a logger that uses a layout which formats a message
   * that triggers a recursive call back into {@link #ensureInitialized()} (which calls this method
   * again), producing an unbounded recursion and a {@link StackOverflowError} deep inside
   * {@code org.slf4j.helpers.FormattingTuple.<clinit>}. When that happens, the Android runtime
   * hard-crashes (SIGSEGV) and the process dies.
   *
   * <p>To completely break that cycle, this method writes any informational or error output
   * directly to {@link System#err} and swallows all logging-framework related failures.
   *
   * <p><b>Idempotency:</b> the method uses the volatile flag {@link #NATIVE_INJECTED} to ensure
   * it only runs its body once per process lifetime. Subsequent calls are O(1) early-returns,
   * which both improves performance (no repeated setenv / map allocation) and guarantees that
   * logback class loading is only ever touched once.
   */
  private static void injectNativeEnvironment() {
    // 幂等性快速路径：已注入则直接返回，避免重复 setenv + 避免再次触发 logback 类加载。
    if (NATIVE_INJECTED) {
      return;
    }
    try {
        Map<String, String> env = new HashMap<>();
        putEnvironment(env, false);

        // 遍历并设置到 OS 层
        for (Map.Entry<String, String> entry : env.entrySet()) {
            try {
                // overwrite = true
                Os.setenv(entry.getKey(), entry.getValue(), true);
            } catch (ErrnoException e) {
                // Use System.err to avoid logback re-entrancy.
                System.err.println("Environment: failed to setenv " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // 特别处理 PATH，确保 java 和 bin 目录在最前面
        String currentPath = System.getenv("PATH");
        if (currentPath == null) currentPath = "";

        String newPath = BIN_DIR.getAbsolutePath() + ":" +
                         new File(JAVA_HOME, "bin").getAbsolutePath() + ":" +
                         currentPath;

        try {
            Os.setenv("PATH", newPath, true);
        } catch (ErrnoException e) {
            System.err.println("Environment: failed to update PATH: " + e.getMessage());
        }

        // Informational message: System.err only, never LOG, to avoid the logback recursion
        // described in the Javadoc above.
        System.err.println("Environment: global native environment injected. JAVA_HOME=" + JAVA_HOME);

    } catch (Throwable t) {
        // Last-resort error path. Do not use LOG here.
        try {
            System.err.println("Environment: critical error injecting native environment: " + t);
            t.printStackTrace(System.err);
        } catch (Throwable ignored) {
            // Nothing more we can do; swallowing is the only safe option here.
        }
    } finally {
        // 无论成功或异常，都把旗标置位，防止反复重试触发的资源浪费。
        // 真正的初始化失败应当在上层通过 ENV 变量缺失来发现，无需重试。
        NATIVE_INJECTED = true;
    }
  }
  

  public static File mkdirIfNotExits(File in) {
    if (in != null && !in.exists()) {
      FileUtils.createOrExistsDir(in);
    }

    return in;
  }

    public static File createFileIfNotExists(File in) {
        if (in != null && !in.exists()) {
            FileUtils.createOrExistsFile(in);
        }
        return in;
    }

  public static void setExecutable(@NonNull final File file) {
    if (!file.exists() || !file.isFile()) {
      return;
    }
    if (!file.canExecute() && !file.setExecutable(true)) {
      LOG.error("Unable to set executable permissions to file: {}", file);
    }
  }

  public static void setProjectDir(@NonNull File file) {
    ensureInitialized();
    PROJECTS_DIR = new File(file.getAbsolutePath());
    // 如果项目目录变更，可能需要更新环境变量 PROJECT (虽然 Native 层已经设置了，但如果是动态变更需重新 setenv)
    try {
        Os.setenv("PROJECTS", PROJECTS_DIR.getAbsolutePath(), true);
    } catch (ErrnoException ignored) {}
  }

  public static void putEnvironment(Map<String, String> env, boolean forFailsafe) {
    ensureInitialized();

    env.put("HOME", HOME.getAbsolutePath());
    env.put("ANDROID_HOME", ANDROID_HOME.getAbsolutePath());
    env.put("ANDROID_NDK_HOME", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("ANDROID_NDK_ROOT", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("ANDROID_NDK", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("NDK_HOME", NDK_HOME.getAbsolutePath());
    env.put("CMAKE_HOME", CMAKE_HOME.getAbsolutePath());
    env.put("CMAKE_ROOT", CMAKE_HOME.getAbsolutePath());
    env.put("PROTOC_HOME", PROTOC_BIN.getParent()); 
    env.put("KOTLINC_HOME", KOTLINC_HOME.getAbsolutePath());
    env.put("KOTLIN_LSP_HOME", KOTLIN_LSP_HOME.getAbsolutePath());
    env.put("ANDROID_SDK_ROOT", ANDROID_HOME.getAbsolutePath());
    env.put("ANDROID_USER_HOME", HOME.getAbsolutePath() + "/.android");
    env.put("JAVA_HOME", JAVA_HOME.getAbsolutePath());
    env.put("GRADLE_USER_HOME", GRADLE_USER_HOME.getAbsolutePath());
    env.put("SYSROOT", PREFIX.getAbsolutePath());
    env.put("PROJECTS", PROJECTS_DIR.getAbsolutePath());
    env.put("TMPDIR", TMP_DIR.getAbsolutePath());
    
    // LD_LIBRARY_PATH is crucial for binaries linked against libs in usr/lib
    env.put("LD_LIBRARY_PATH", LIB_DIR.getAbsolutePath() + ":" + 
                new File(JAVA_HOME, "lib").getAbsolutePath());
 
    // add user envs for non-failsafe sessions
    if (!forFailsafe) {
      // No mirror select
      env.put("TERMUX_PKG_NO_MIRROR_SELECT", "true");
    }
  }

  @NonNull
  public static File getProjectsDir() {
    ensureInitialized();
    return PROJECTS_DIR;
  }

  public static File getProjectCacheDir(File projectDir) {
    return new File(projectDir, ".androidide");
  }

  @NonNull
  public static File createTempFile() {
    var file = newTempFile();
    while (file.exists()) {
      file = newTempFile();
    }

    return file;
  }

  @NonNull
  private static File newTempFile() {
    return new File(TMP_DIR, "temp_" + UUID.randomUUID().toString().replace('-', 'X'));
  }
}
