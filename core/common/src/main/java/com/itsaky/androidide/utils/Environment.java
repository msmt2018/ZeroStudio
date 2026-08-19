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

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.itsaky.androidide.app.BaseApplication;

/**
 * AndroidIDE Environment configuration.
 * Configures file paths and Java system properties to support global JDK and SDK access.
 *
 * <p>NOTE: This class no longer calls {@code android.system.Os.setenv}. The native environment
 * is intentionally <em>not</em> injected into the process — the only real consumer that needs
 * {@code JAVA_HOME}/{@code PATH} (the {@code IJdkDistributionProvider}) calls {@code Os.setenv}
 * itself when the JDK is selected. All other consumers build their env map via
 * {@link #putEnvironment(Map, boolean)} and pass it explicitly to {@code ProcessBuilder}.
 *
 * @author android_zero
 */
@SuppressLint("SdCardPath")
public final class Environment {
  // INITIALIZED 必须是 volatile，因为 init() 会在多线程（主线程 + Termux 启动时的
  // Background Thread + Firebase Worker + ContentProvider）中并发触发。
  // synchronized + volatile + 双重检查共同保证 happens-before 与可见性。
  private static volatile boolean INITIALIZED = false;

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
   * Initializes the environment paths and Java system properties used by the IDE and the
   * bundled build tools (gradle, java, cmake, etc.).
   *
   * <p>This method is idempotent and thread-safe: subsequent calls from any thread are O(1)
   * early-returns. {@code BaseApplication.onCreate()} is the canonical entry point and is
   * expected to call this once during process startup. Some early initialization paths
   * (e.g. {@code IDEDocumentsProvider} or test rules) may call it explicitly before
   * {@code Application.onCreate()}.
   *
   * <p><b>Thread-safety:</b> double-checked locking on {@link #INITIALIZED}.
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

      // 关键：在释放 synchronized 锁之前置位 INITIALIZED。
      // 任何并发调用的 ensureInitialized() / putEnvironment() 等都会看到 INITIALIZED == true，
      // 立即短路返回，避免重复执行 init() 体内的 setProperty / mkdir / setExecutable 等
      // 副作用。彻底消除了 1.5-4s 冷启动黑屏的根因（曾经的 inject 递归链）。
      INITIALIZED = true;
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
    // 项目的 PROJECTS 环境变量由 putEnvironment() 在调用方显式构建；
    // 这里不再 setenv 到 native 进程（与 injectNativeEnvironment 移除保持一致）。
  }

  public static void putEnvironment(Map<String, String> env, boolean forFailsafe) {
    ensureInitialized();
    env.putAll(createTerminalEnvironment(forFailsafe));
  }

  /**
   * Builds the Linux/rootfs environment shared by the IDE and ZeroStudio terminal.
   *
   * <p>The map intentionally mirrors Termux' shell environment model: start from Android process
   * variables, then override rootfs-critical paths (HOME/PREFIX/TMPDIR/PATH) and finally append IDE
   * development tool locations such as the SDK, NDK, Gradle, Java, CMake, Kotlin and protoc.
   */
  @NonNull
  public static Map<String, String> createTerminalEnvironment(boolean forFailsafe) {
    ensureInitialized();

    final var env = new LinkedHashMap<String, String>(System.getenv());
    final var prefix = PREFIX.getAbsolutePath();
    final var home = HOME.getAbsolutePath();
    final var javaBin = new File(JAVA_HOME, "bin").getAbsolutePath();
    final var androidTools = new File(ANDROID_HOME, "cmdline-tools/latest/bin").getAbsolutePath();
    final var platformTools = new File(ANDROID_HOME, "platform-tools").getAbsolutePath();
    final var cmakeBin = CMAKE_BIN.getAbsolutePath();

    env.put("HOME", home);
    env.put("PREFIX", prefix);
    env.put("ANDROID_HOME", ANDROID_HOME.getAbsolutePath());
    env.put("ANDROID_SDK_ROOT", ANDROID_HOME.getAbsolutePath());
    env.put("ANDROID_USER_HOME", home + "/.android");
    env.put("ANDROID_NDK_HOME", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("ANDROID_NDK_ROOT", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("ANDROID_NDK", ANDROID_NDK_HOME.getAbsolutePath());
    env.put("NDK_HOME", NDK_HOME.getAbsolutePath());
    env.put("CMAKE_HOME", CMAKE_HOME.getAbsolutePath());
    env.put("CMAKE_ROOT", CMAKE_HOME.getAbsolutePath());
    env.put("PROTOC_HOME", PROTOC_BIN.getAbsolutePath());
    env.put("KOTLINC_HOME", KOTLINC_HOME.getAbsolutePath());
    env.put("KOTLIN_LSP_HOME", KOTLIN_LSP_HOME.getAbsolutePath());
    env.put("JAVA_HOME", JAVA_HOME.getAbsolutePath());
    env.put("GRADLE_USER_HOME", GRADLE_USER_HOME.getAbsolutePath());
    env.put("SYSROOT", prefix);
    env.put("PROJECTS", PROJECTS_DIR.getAbsolutePath());
    env.put("TMPDIR", TMP_DIR.getAbsolutePath());
    env.put("PWD", home);
    env.put("TERM", env.getOrDefault("TERM", "xterm-256color"));
    env.put("COLORTERM", env.getOrDefault("COLORTERM", "truecolor"));
    env.put("LANG", env.getOrDefault("LANG", "en_US.UTF-8"));

    env.put("PATH", joinPaths(
        BIN_DIR.getAbsolutePath(),
        javaBin,
        androidTools,
        platformTools,
        cmakeBin,
        env.get("PATH")));

    env.put("LD_LIBRARY_PATH", joinPaths(
        LIB_DIR.getAbsolutePath(),
        new File(JAVA_HOME, "lib").getAbsolutePath(),
        env.get("LD_LIBRARY_PATH")));

    if (!forFailsafe) {
      env.put("TERMUX_PKG_NO_MIRROR_SELECT", "true");
    }
    return env;
  }

  private static String joinPaths(String... paths) {
    final var builder = new StringBuilder();
    for (String path : paths) {
      if (path == null || path.isBlank()) {
        continue;
      }
      if (builder.indexOf(path) >= 0) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(':');
      }
      builder.append(path);
    }
    return builder.toString();
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
