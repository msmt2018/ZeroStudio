/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.itsaky.androidide.managers;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.ResourceUtils;
import com.itsaky.androidide.app.BaseApplication;
import com.itsaky.androidide.app.configuration.IDEBuildConfigProvider;
import com.itsaky.androidide.app.configuration.IJdkDistributionProvider;
import com.itsaky.androidide.utils.Environment;

import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import kotlin.io.ConstantsKt;
import kotlin.io.FilesKt;

public class ToolsManager {

  private static final Logger LOG = LoggerFactory.getLogger(ToolsManager.class);
  private static final Object TOOLING_API_LOCK = new Object();
  private static final Object INIT_EXECUTOR_LOCK = new Object();
  private static volatile CompletableFuture<Void> INIT_FUTURE;
  private static volatile ExecutorService INIT_EXECUTOR;

  public static String COMMON_ASSET_DATA_DIR = "data/common";

  public static void init(@NonNull BaseApplication app, Runnable onFinish) {
    initIfNeeded(app, onFinish);
  }

  public static CompletableFuture<Void> initIfNeeded(@NonNull BaseApplication app, Runnable onFinish) {
    if (INIT_FUTURE != null) {
      if (onFinish != null) {
        INIT_FUTURE.whenComplete((__, ___) -> onFinish.run());
      }
      return INIT_FUTURE;
    }
    synchronized (ToolsManager.class) {
      if (INIT_FUTURE != null) {
        if (onFinish != null) {
          INIT_FUTURE.whenComplete((__, ___) -> onFinish.run());
        }
        return INIT_FUTURE;
      }

      if (!IDEBuildConfigProvider.getInstance().supportsCpuAbi()) {
        LOG.error("Device not supported");
        final CompletableFuture<Void> f = CompletableFuture.completedFuture(null);
        if (onFinish != null) onFinish.run();
        return f;
      }
      final ExecutorService initExecutor = getOrCreateInitExecutor();

      INIT_FUTURE = CompletableFuture.runAsync(() -> {
        // Load installed JDK distributions only when tooling is actually initialized.
        IJdkDistributionProvider.getInstance().loadDistributions();

        writeNoMediaFile();
        extractAapt2();
        extractToolingApi();
        extractAndroidJar();
        extractColorScheme(app);
        writeInitScript();

        installExtraTools();

        deleteIdeenv();
      }, initExecutor).whenComplete((__, error) -> {
        if (error != null) {
          LOG.error("Error extracting tools", error);
        }
        releaseInitExecutor();
        if (onFinish != null) {
          onFinish.run();
        }
      });
      return INIT_FUTURE;
    }
  }

  private static ExecutorService getOrCreateInitExecutor() {
    synchronized (INIT_EXECUTOR_LOCK) {
      if (INIT_EXECUTOR == null || INIT_EXECUTOR.isShutdown() || INIT_EXECUTOR.isTerminated()) {
        INIT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ToolsManager-LazyInit"));
      }
      return INIT_EXECUTOR;
    }
  }

  private static void releaseInitExecutor() {
    synchronized (INIT_EXECUTOR_LOCK) {
      if (INIT_EXECUTOR != null) {
        INIT_EXECUTOR.shutdown();
        INIT_EXECUTOR = null;
      }
    }
  }
  
    /**
   * 专门用于集中管理 Assets 解压/安装的私有方法
   */
  private static void installExtraTools() {

   // PR-1: the legacy `logger-runtime.zip`, `plugin-api.jar` and
   // `zerostudio-gradle-plugin-1.0.0.jar` assets are removed. The new
   // `ide-log-plugin` AAR is published as a regular Gradle module and
   // resolved through `ide-log-plugin-1.0.0.aar` extracted from
   // `data/common/ide-log-plugin-1.0.0.aar`. PR-2 will additionally
   // install `data/common/ide-debugger-1.0.0.aar`.
   installAsset("data/common/ide-log-plugin-1.0.0.aar",
       new File(Environment.PLUGIN_HOME , "ide-log-plugin"), true, 0, null);
  }

  private static void extractColorScheme(final BaseApplication app) {
    final var defPath = "editor/schemes";
    final var dir = new File(Environment.ANDROIDIDE_UI, defPath);
    try {
      for (final String asset : app.getAssets().list(defPath)) {

        final var prop = new File(dir, asset + "/" + "scheme.prop");
        if (prop.exists() && !shouldExtractScheme(app, new File(dir, asset),
            defPath + "/" + asset)) {
          continue;
        }

        final File schemeDir = new File(dir, asset);
        if (schemeDir.exists()) {
          schemeDir.delete();
        }

        ResourceUtils.copyFileFromAssets(defPath + "/" + asset, schemeDir.getAbsolutePath());
      }
    } catch (IOException e) {
      LOG.error("Failed to extract color schemes", e);
    }
  }

  private static boolean shouldExtractScheme(final BaseApplication app, final File dir,
      final String path) throws IOException {

    final var schemePropFile = new File(dir, "scheme.prop");
    if (!schemePropFile.exists()) {
      return true;
    }

    final var files = app.getAssets().list(path);
    if (Arrays.stream(files).noneMatch("scheme.prop"::equals)) {
      // no scheme.prop file
      return true;
    }

    try {
      final var props = new Properties();
      Reader reader = new InputStreamReader(app.getAssets().open(path + "/scheme.prop"));
      props.load(reader);
      reader.close();

      final var version = Integer.parseInt(props.getProperty("scheme.version", "0"));
      if (version == 0) {
        return true;
      }

      props.clear();

      reader = new FileReader(schemePropFile);
      props.load(reader);
      reader.close();

      final var fileVersion = Integer.parseInt(props.getProperty("scheme.version", "0"));
      if (fileVersion < 0) {
        return true;
      }

      return version > fileVersion;
    } catch (Throwable err) {
      LOG.error("Failed to read color scheme version for scheme '{}'", path, err);
      return false;
    }
  }

  private static void writeNoMediaFile() {
    final var noMedia = new File(BaseApplication.getBaseInstance().getProjectsDir(), ".nomedia");
    if (!noMedia.exists()) {
      try {
        if (!noMedia.createNewFile()) {
          LOG.error("Failed to create .nomedia file in projects directory");
        }
      } catch (IOException e) {
        LOG.error("Failed to create .nomedia file in projects directory");
      }
    }
  }

  private static void extractAndroidJar() {
    if (!Environment.ANDROID_JAR.exists()) {
      ResourceUtils.copyFileFromAssets(getCommonAsset("android.jar"),
          Environment.ANDROID_JAR.getAbsolutePath());
    }
  }

  private static void deleteIdeenv() {
    final var file = new File(Environment.BIN_DIR, "ideenv");
    if (file.exists() && !file.delete()) {
      LOG.warn("Unable to delete file: {}", file);
    }
  }

  @NonNull
  @Contract(pure = true)
  public static String getCommonAsset(String name) {
    return COMMON_ASSET_DATA_DIR + "/" + name;
  }

  private static void extractAapt2() {
    if (!Environment.AAPT2.exists()) {
      final var context = BaseApplication.getBaseInstance();
      final var nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
      final var sourceAapt2 = new File(nativeLibraryDir, "libaapt2.so");
      if (sourceAapt2.exists() && sourceAapt2.isFile()) {
        FilesKt.copyTo(sourceAapt2, Environment.AAPT2, true, ConstantsKt.DEFAULT_BUFFER_SIZE);
      } else {
        LOG.error("{} file does not exist! This can be problematic.", sourceAapt2);
      }
    }

    if (!Environment.AAPT2.canExecute() && !Environment.AAPT2.setExecutable(true)) {
      LOG.error("Cannot set executable permissions to AAPT2 binary");
    }
  }

  private static void extractToolingApi() {
    ensureToolingApiReady();
  }

  public static boolean ensureToolingApiReady() {
    synchronized (TOOLING_API_LOCK) {
      if (isValidJar(Environment.TOOLING_API_JAR)) {
        return true;
      }

      if (Environment.TOOLING_API_JAR.exists()) {
        FileUtils.delete(Environment.TOOLING_API_JAR);
      }

      final var parent = Environment.TOOLING_API_JAR.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        LOG.error("Failed to create tooling-api directory: {}", parent.getAbsolutePath());
        return false;
      }
      if (parent == null) {
        LOG.error("Unable to resolve parent directory for {}", Environment.TOOLING_API_JAR);
        return false;
      }

      final var tmpJar = new File(parent, Environment.TOOLING_API_JAR.getName() + ".tmp");
      try {
        if (tmpJar.exists()) {
          FileUtils.delete(tmpJar);
        }
        if (!ResourceUtils.copyFileFromAssets(getCommonAsset("tooling-api-all.jar"),
            tmpJar.getAbsolutePath())) {
          LOG.error("Unable to copy tooling-api-all.jar from assets");
          return false;
        }

        if (!isValidJar(tmpJar)) {
          LOG.error("Extracted tooling-api-all.jar is invalid: {}", tmpJar.getAbsolutePath());
          FileUtils.delete(tmpJar);
          return false;
        }

        try {
          Files.move(
              tmpJar.toPath(),
              Environment.TOOLING_API_JAR.toPath(),
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.ATOMIC_MOVE
          );
        } catch (IOException moveError) {
          LOG.warn("Atomic move failed for tooling-api-all.jar, falling back to copy", moveError);
          Files.copy(
              tmpJar.toPath(),
              Environment.TOOLING_API_JAR.toPath(),
              StandardCopyOption.REPLACE_EXISTING
          );
          FileUtils.delete(tmpJar);
        }
      } catch (IOException ioException) {
        LOG.error("Failed to extract tooling-api-all.jar", ioException);
        FileUtils.delete(tmpJar);
        return false;
      }

      return isValidJar(Environment.TOOLING_API_JAR);
    }
  }

  private static boolean isValidJar(File file) {
    if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
      return false;
    }

    try (JarFile jarFile = new JarFile(file)) {
      return jarFile.entries().hasMoreElements();
    } catch (IOException ioException) {
      LOG.warn("Jar validation failed for {}", file.getAbsolutePath(), ioException);
      return false;
    }
  }

  private static void writeInitScript() {
    final var initScript = Environment.INIT_SCRIPT;
    final var initScriptBak = new File(initScript.getParentFile(), initScript.getName() + ".bak");
    final var contents = readInitScript();

    // 治本：父目录不一定存在（首次启动时，init/ 是新创建的），要先 mkdirs 再写文件，
    // 否则 FileOutputStream 会抛 FileNotFoundException("open failed: ENOENT")。
    // 进一步加固：同时为 initScript 自身也建好父目录（虽然 exists() 守卫兜底，
    // 但如果未来重构把 exists() 守卫挪走就可能再次踩雷）。
    final var parent = initScriptBak.getParentFile();
    Environment.mkdirIfNotExits(parent);  // null 容忍 + 内部 try-createOrExistsDir
    if (parent != null && !parent.isDirectory()) {
      LOG.warn("Failed to create parent dir for init script: {}", parent.getAbsolutePath());
      return;
    }

    // 原子写：先写到 .tmp，再 rename 到目标。
    // 相比 commit e935009 的"直接写"修复，原子写的好处：
    //   1. 写过程崩溃不会留下半截 init.gradle（哪怕电源被拔）；
    //   2. 减少进程竞争——即便有其它协程同时跑 writeInitScript（理论上不应该，
    //      但本方法历史上曾被多线程触发），rename 是 POSIX 原子操作。
    //   3. 性能：与原版差异可忽略（一次额外的 rename() 系统调用，单次 < 1ms）。
    atomicWriteUtf8(initScriptBak, contents);
    if (!initScript.exists()) {
      atomicWriteUtf8(initScript, contents);
    }
  }

  /**
   * 原子写入 UTF-8 文本到目标文件。
   *
   * <p>实现：先写到 {@code <target>.tmp}，fsync，然后 {@code rename} 到 {@code target}。
   * rename 在同一文件系统上是原子操作，所以读取方要么看到旧文件、要么看到完整新文件，
   * 不会读到半截内容（这是 commit e935009 "直接覆盖" 修复的潜在风险）。
   *
   * <p>失败时清理 .tmp 临时文件，避免垃圾残留。
   */
  private static void atomicWriteUtf8(@NonNull File target, @NonNull String contents) {
    final var tmp = new File(target.getAbsolutePath() + ".tmp");
    try (var out = new java.io.FileOutputStream(tmp)) {
      out.write(contents.getBytes(StandardCharsets.UTF_8));
      out.getFD().sync();  // fsync：保证内容落盘后再 rename
      // rename 在 Linux/Android 上是原子的；Windows 上 renameTo 在目标存在时返回 false，
      // 所以先 delete 兜底（注意：这会留下一个"无文件"的瞬间，因此仅用于本地一致性场景）。
      if (!tmp.renameTo(target)) {
        target.delete();
        if (!tmp.renameTo(target)) {
          // 最后兜底：直接写（非原子，但能写入）
          FilesKt.writeText(target, contents, StandardCharsets.UTF_8);
        }
      }
    } catch (java.io.IOException e) {
      // 清理临时文件；target 可能不存在，本次失败不影响下次重试
      try { tmp.delete(); } catch (Throwable ignored) {}
      // 抛回原异常，让 writeInitScript 上层决定是否要 LOG.warn / 重试
      throw new java.io.UncheckedIOException(
          "atomicWriteUtf8 failed for " + target.getAbsolutePath(), e);
    }
  }

  @NonNull
  private static String readInitScript() {
    return ResourceUtils.readAssets2String(getCommonAsset("androidide.init.gradle"));
  }

  /**
   * Universal method to install/extract assets to a specified destination.
   * Handles files, directories, and zip extraction with path stripping and filtering.
   *
   * @param assetPath     The relative path inside the APK assets (e.g., "plugins/myplugin.zip").
   * @param outputDir     The destination directory.
   * @param isUnzip       If true, treats the asset as a zip file.
   * @param unzipDepth    How many directory levels to strip from the zip entry path.
   *                      Example: Entry is "root/sub/file.txt". Depth 2 -> "file.txt".
   * @param zipInnerPath  (Optional) Only extract entries starting with this path inside the zip.
   *                      Pass null or "" to extract everything.
   *                      Example: "lib/arm64/" to only extract that folder from the zip.
   *@author android_zero
   */
  public static void installAsset(String assetPath, File outputDir, boolean isUnzip, int unzipDepth, String zipInnerPath) {
    if (outputDir == null || assetPath == null) {
      LOG.error("installAsset: assetPath or outputDir is null");
      return;
    }

    // Create output dir if it doesn't exist
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      LOG.error("installAsset: Failed to create output directory: " + outputDir.getAbsolutePath());
      return;
    }

    try {
      if (isUnzip) {
        // Zip Extraction Logic with Filter and Depth
        unzipAsset(assetPath, outputDir, unzipDepth, zipInnerPath);
      } else {
        // Standard File/Directory Copy Logic
        copyAssetSmart(assetPath, outputDir);
      }
    } catch (Exception e) {
      LOG.error("installAsset: Failed to install " + assetPath, e);
    }
  }

  /**
   * Overload for convenience if no zip filtering is needed.
   */
  public static void installAsset(String assetPath, File outputDir, boolean isUnzip, int unzipDepth) {
      installAsset(assetPath, outputDir, isUnzip, unzipDepth, null);
  }

  /**
   * Helper to copy asset file or directory recursively.
   * Checks existence: Folders skip if exist, Files skip if exist, otherwise create/copy.
   */
  private static void copyAssetSmart(String assetPath, File parentDir) throws IOException {
    String[] list = BaseApplication.getBaseInstance().getAssets().list(assetPath);

    if (list != null && list.length > 0) {
      // It is a directory
      String dirName = new File(assetPath).getName();
      File targetDir = new File(parentDir, dirName);

      // Rule: If folder exists, direct use and no operation.
      if (targetDir.exists()) {
        LOG.info("installAsset: Directory exists, skipping: " + targetDir.getAbsolutePath());
        return;
      }

      if (!targetDir.mkdirs()) {
        throw new IOException("Failed to create dir: " + targetDir);
      }

      for (String file : list) {
        // Recursively copy contents. Passing the new targetDir as parent.
        String childAsset = assetPath.equals("") ? file : assetPath + "/" + file;
        // Recursive call needs to know this is an internal copy, so we implement a recursive helper
        // But here we can just reuse ResourceUtils for simplicity if we are at leaf,
        // however ResourceUtils doesn't handle the "Folder exists -> skip" logic for sub-folders well
        // if we use copyFileFromAssets recursively.
        // Let's call copyRecursiveInternal to handle the structure.
        copyAssetRecursiveInternal(childAsset, targetDir);
      }
    } else {
      // It is a file
      String fileName = new File(assetPath).getName();
      File targetFile = new File(parentDir, fileName);
      
      // Rule: 检查文件是否存在，存在则跳过，不存在则输出复制
      if (targetFile.exists()) {
        LOG.info("installAsset: File exists, skipping: " + targetFile.getAbsolutePath());
      } else {
        boolean success = ResourceUtils.copyFileFromAssets(assetPath, targetFile.getAbsolutePath());
        if (!success) {
          LOG.error("installAsset: Failed to copy file: " + assetPath);
        }
      }
    }
  }

  private static void copyAssetRecursiveInternal(String assetPath, File parentDir) throws IOException {
    String[] list = BaseApplication.getBaseInstance().getAssets().list(assetPath);
    if (list != null && list.length > 0) {
      // Directory inside recursive copy
      String dirName = new File(assetPath).getName();
      File targetDir = new File(parentDir, dirName);
      if (!targetDir.exists() && !targetDir.mkdirs()) return;
      for (String file : list) {
        copyAssetRecursiveInternal(assetPath + "/" + file, targetDir);
      }
    } else {
      // File inside recursive copy
      String fileName = new File(assetPath).getName();
      File targetFile = new File(parentDir, fileName);
      
      // Rule: 检查文件是否存在，存在则跳过，不存在则输出复制
      if (!targetFile.exists()) {
        ResourceUtils.copyFileFromAssets(assetPath, targetFile.getAbsolutePath());
      }
    }
  }

  /**
   * Helper to unzip an asset stream with depth stripping and path filtering.
   */
  private static void unzipAsset(String assetPath, File destDir, int stripDepth, String targetInnerPath) throws IOException {
    try (InputStream is = BaseApplication.getBaseInstance().getAssets().open(assetPath);
         ZipInputStream zis = new ZipInputStream(is)) {

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String entryName = entry.getName();

        // 1. Filter: Check if this entry matches the targetInnerPath (if provided)
        if (targetInnerPath != null && !targetInnerPath.isEmpty()) {
            // Normalize path separators just in case
            if (!entryName.startsWith(targetInnerPath)) {
                continue; // Skip this file as it is not in the target folder
            }
        }

        // 2. Strip Depth: Remove leading directories from the path
        if (stripDepth > 0) {
          String[] parts = entryName.split("/");
          if (parts.length <= stripDepth) {
            // Path is too shallow (e.g. it is the folder we are stripping), skip
            continue;
          }
          // Reconstruct path excluding the first 'stripDepth' components
          StringBuilder sb = new StringBuilder();
          for (int i = stripDepth; i < parts.length; i++) {
            sb.append(parts[i]);
            if (i < parts.length - 1) sb.append("/");
          }
          entryName = sb.toString();
        // } else if (stripDepth == 0 && entry.isDirectory()) {
           // // If depth is 0 (root), we keep structure.
        }

        // If entryName became empty after stripping (e.g. root folder), skip
        if (entryName.isEmpty()) continue;

        File outFile = new File(destDir, entryName);

        if (entry.isDirectory()) {
          if (!outFile.exists()) outFile.mkdirs();
          continue;
        }

        // Rule: 检查解压出的文件是否已存在，如果已存在则直接跳过，避免应用每次启动都覆盖文件
        if (outFile.exists()) {
          continue;
        }

        // Ensure parent exists
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
          parent.mkdirs();
        }

        // Write file (Only executed if the file does not exist)
        try (OutputStream os = new FileOutputStream(outFile)) {
          byte[] buffer = new byte[ConstantsKt.DEFAULT_BUFFER_SIZE];
          int length;
          while ((length = zis.read(buffer)) > 0) {
            os.write(buffer, 0, length);
          }
        }
      }
    }
  }
  }
