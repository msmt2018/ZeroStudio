package com.termix.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termix.libcommons.*
import com.termix.resources.strings
import com.termix.settings.Settings
import com.termix.App
import com.termix.ui.activities.terminal.MainActivity
import com.termix.model.WorkingMode
import com.termix.ui.screens.terminal.Rootfs
import com.termix.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var rootfsProgress by remember { mutableFloatStateOf(0f) }
    var rootfsSpeedBytes by remember { mutableLongStateOf(0L) }
    var rootfsDownloadedBytes by remember { mutableLongStateOf(0L) }
    var rootfsTotalBytes by remember { mutableLongStateOf(0L) }
    var activeFileName by remember { mutableStateOf("") }
    val installerLogs = remember { mutableStateListOf<String>() }
    val logScrollState = rememberScrollState()

    val installingStr = stringResource(strings.installing)
    val downloadingStr = stringResource(strings.downloading)
    val archInstallingRootfsStr = stringResource(strings.arch_installing_rootfs)
    val downloadPanelTitle = stringResource(strings.download_panel_title)
    val installLogTitle = stringResource(strings.install_logs_title)
    val installLogWaiting = stringResource(strings.install_logs_waiting)
    val overallProgressTitle = stringResource(strings.overall_progress_title)
    val rootfsProgressTitle = stringResource(strings.rootfs_progress_title)
    val currentFileTitle = stringResource(strings.current_file_title)
    val transferRateTitle = stringResource(strings.transfer_rate_title)
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    val retrySetupStr = stringResource(strings.retry_setup)
    var progressText by remember { mutableStateOf(installingStr) }
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    fun appendInstallerLog(line: String) {
        installerLogs += line
        while (installerLogs.size > MAX_INSTALL_LOG_LINES) {
            installerLogs.removeAt(0)
        }
    }

    LaunchedEffect(installerLogs.size) {
        if (installerLogs.isNotEmpty()) {
            logScrollState.scrollTo(logScrollState.maxValue)
        }
    }

    LaunchedEffect(retryToken) {
        progress = 0f
        rootfsProgress = 0f
        rootfsSpeedBytes = 0L
        rootfsDownloadedBytes = 0L
        rootfsTotalBytes = 0L
        activeFileName = ""
        installerLogs.clear()
        progressText = installingStr
        setupError = null
        appendInstallerLog("[boot] ${modeLabel(Settings.working_Mode)} environment initialization started")

        try {
            val workingMode = Settings.working_Mode
            if (!Rootfs.requiresRootfs(workingMode)) {
                Rootfs.isDownloaded.value = true
                isSetupComplete = true
                appendInstallerLog("[done] ${modeLabel(workingMode)} mode does not require rootfs download")
                return@LaunchedEffect
            }

            val abi = Build.SUPPORTED_ABIS.firstOrNull {
                it in abiMap
            } ?: throw RuntimeException("Unsupported CPU")

            val urls = abiMap[abi] ?: throw RuntimeException("Unsupported CPU ABI: $abi")
            val rootfsUrls = when (workingMode) {
                WorkingMode.ARCH,
                WorkingMode.ARCH_ROOT -> urls.arch ?: throw RuntimeException("Arch Linux is not supported for ABI: $abi")
                else -> listOf(urls.alpine)
            }

            val rootfsFileName = when (workingMode) {
                WorkingMode.ARCH,
                WorkingMode.ARCH_ROOT -> "arch.tar.gz"
                else -> "alpine.tar.gz"
            }

            val filesToDownload = listOf(
                DownloadFile(listOf(urls.talloc.url), Rootfs.reTerminal.child("libtalloc.so.2"), urls.talloc.sha256),
                DownloadFile(listOf(urls.proot.url), Rootfs.reTerminal.child("proot"), urls.proot.sha256),
                DownloadFile(rootfsUrls, Rootfs.reTerminal.child(rootfsFileName))
            )

            needsDownload = filesToDownload.any { !it.outputFile.exists() }
            progressText = if (needsDownload) downloadingStr.format(0) else installingStr

            setupEnvironment(
                filesToDownload,
                workingMode,
                archInstallStatus = archInstallingRootfsStr,
                onProgress = { snapshot ->
                    activeFileName = snapshot.currentFileName
                    progress = snapshot.overallProgress

                    if (snapshot.isRootfs) {
                        rootfsProgress = snapshot.fileProgress
                        rootfsSpeedBytes = snapshot.bytesPerSecond
                        rootfsDownloadedBytes = snapshot.downloadedBytes
                        rootfsTotalBytes = snapshot.totalBytes
                    }

                    if (needsDownload) {
                        progressText = downloadingStr.format((progress * 100).toInt())
                    }
                },
                onStatus = { status ->
                    progressText = status
                    appendInstallerLog("[status] $status")
                },
                onInstallLog = { line ->
                    appendInstallerLog("[install] $line")
                },
                onComplete = {
                    Rootfs.isDownloaded.value = Rootfs.isFilesDownloaded()
                    isSetupComplete = true
                    appendInstallerLog("[done] Setup completed, launching terminal")
                },
                onError = { error ->
                    val message = if (error is UnknownHostException) {
                        networkErrorStr
                    } else {
                        setupFailedStr.format(error.message ?: error.javaClass.simpleName)
                    }
                    setupError = message
                    progressText = message
                    appendInstallerLog("[error] $message")
                    toast(message)
                }
            )
        } catch (e: Exception) {
            val message = if (e is UnknownHostException) {
                networkErrorStr
            } else {
                setupFailedStr.format(e.message ?: e.javaClass.simpleName)
            }
            setupError = message
            progressText = message
            appendInstallerLog("[error] $message")
            toast(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = downloadPanelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = modeLabel(Settings.working_Mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (setupError == null) {
                            Text(
                                text = overallProgressTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )

                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (activeFileName.isNotBlank()) {
                                Text(
                                    text = "$currentFileTitle: $activeFileName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (rootfsDownloadedBytes > 0L) {
                                Text(
                                    text = rootfsProgressTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (rootfsTotalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = { rootfsProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (rootfsTotalBytes > 0L) {
                                            "${formatBytes(rootfsDownloadedBytes)} / ${formatBytes(rootfsTotalBytes)}"
                                        } else {
                                            formatBytes(rootfsDownloadedBytes)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$transferRateTitle: ${formatRate(rootfsSpeedBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = setupError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (setupError == null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = installLogTitle,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(logScrollState)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    if (installerLogs.isEmpty()) {
                                        Text(
                                            text = installLogWaiting,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        installerLogs.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (Settings.working_Mode == WorkingMode.ARCH || Settings.working_Mode == WorkingMode.ARCH_ROOT) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(archInstallingRootfsStr, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Button(onClick = { retryToken++ }) {
                        Text(retrySetupStr)
                    }
                }
            }
        } else {
            TerminalScreen(mainActivityActivity = mainActivity, navController = navController)
        }
    }
}

private data class DownloadFile(
    val urls: List<String>,
    val outputFile: File,
    val expectedSha256: String? = null
)

private suspend fun setupEnvironment(
    filesToDownload: List<DownloadFile>,
    workingMode: Int,
    archInstallStatus: String,
    onProgress: (DownloadProgressSnapshot) -> Unit,
    onStatus: (String) -> Unit,
    onInstallLog: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            var completedFiles = 0
            val totalFiles = filesToDownload.size
            val hasUsableArchRootfs = (workingMode == WorkingMode.ARCH || workingMode == WorkingMode.ARCH_ROOT) && hasExtractedArchRootfs()

            filesToDownload.forEach { file ->
                val outputFile = file.outputFile.apply { parentFile?.mkdirs() }
                val isRootfsFile = outputFile.name.endsWith(".tar.gz")
                val skipArchRootfsDownload = hasUsableArchRootfs && outputFile.name == "arch.tar.gz"
                var skippedDownload = false
                var downloadedNow = false

                runOnUiThread {
                    onStatus("Downloading ${outputFile.name}")
                }

                if (skipArchRootfsDownload) {
                    skippedDownload = true
                    runOnUiThread {
                        onInstallLog("Detected usable Arch rootfs; skipping arch.tar.gz download")
                    }
                } else {
                    if (outputFile.exists() && file.expectedSha256 != null && !outputFile.matchesSha256(file.expectedSha256)) {
                        runOnUiThread {
                            onInstallLog("Checksum mismatch for existing ${outputFile.name}; redownloading")
                        }
                        if (!outputFile.delete()) {
                            throw Exception("Checksum mismatch for ${outputFile.name} and failed to delete stale file")
                        }
                    }

                    if (!outputFile.exists()) {
                        val tempOutputFile = outputFile.parentFile!!.child("${outputFile.name}.part")
                        if (tempOutputFile.exists()) {
                            tempOutputFile.delete()
                        }

                        var lastSampleAt = System.nanoTime()
                        var lastDownloaded = 0L
                        var smoothedSpeed = 0.0

                        downloadFileWithFallback(file.urls, tempOutputFile) { downloaded, total ->
                            val now = System.nanoTime()
                            val deltaNanos = now - lastSampleAt
                            if (deltaNanos > 0) {
                                val deltaBytes = (downloaded - lastDownloaded).coerceAtLeast(0L)
                                val instantSpeed = (deltaBytes.toDouble() * NANOS_PER_SECOND) / deltaNanos.toDouble()
                                smoothedSpeed = if (smoothedSpeed == 0.0) {
                                    instantSpeed
                                } else {
                                    (smoothedSpeed * 0.75) + (instantSpeed * 0.25)
                                }
                                lastSampleAt = now
                                lastDownloaded = downloaded
                            }

                            val fileProgress = if (total > 0L) {
                                (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val overallProgress = ((completedFiles + fileProgress) / totalFiles.toFloat()).coerceIn(0f, 1f)
                            runOnUiThread {
                                onProgress(
                                    DownloadProgressSnapshot(
                                        currentFileName = outputFile.name,
                                        completedFiles = completedFiles,
                                        totalFiles = totalFiles,
                                        fileProgress = fileProgress,
                                        overallProgress = overallProgress,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        bytesPerSecond = smoothedSpeed.roundToLong().coerceAtLeast(0L),
                                        isRootfs = isRootfsFile
                                    )
                                )
                            }
                        }

                        if (outputFile.exists()) {
                            outputFile.delete()
                        }

                        file.expectedSha256?.let { expectedSha256 ->
                            verifySha256OrThrow(tempOutputFile, expectedSha256)
                        }

                        if (!tempOutputFile.renameTo(outputFile)) {
                            throw Exception("Failed to finalize download: ${outputFile.name}")
                        }
                        downloadedNow = true
                    }
                }
                completedFiles++
                runOnUiThread {
                    val outputSize = if (outputFile.exists()) outputFile.length() else 0L
                    onProgress(
                        DownloadProgressSnapshot(
                            currentFileName = outputFile.name,
                            completedFiles = completedFiles,
                            totalFiles = totalFiles,
                            fileProgress = 1f,
                            overallProgress = (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f),
                            downloadedBytes = outputSize,
                            totalBytes = outputSize,
                            bytesPerSecond = 0L,
                            isRootfs = isRootfsFile
                        )
                    )
                    when {
                        skippedDownload -> onInstallLog("Skipped ${outputFile.name} (existing Arch filesystem)")
                        downloadedNow -> onInstallLog("Downloaded ${outputFile.name}")
                        else -> onInstallLog("Reused ${outputFile.name}")
                    }
                }
                if (outputFile.exists()) {
                    outputFile.setExecutable(true, false)
                }
            }

            if (workingMode == WorkingMode.ARCH || workingMode == WorkingMode.ARCH_ROOT) {
                runOnUiThread { onStatus(archInstallStatus) }
                runOnUiThread { onInstallLog("Starting Arch rootfs extraction") }
                installArchRootfsIfNeeded { line ->
                    runOnUiThread { onInstallLog(line) }
                }
            }

            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            filesToDownload.forEach { file ->
                val tempOutputFile = file.outputFile.parentFile?.child("${file.outputFile.name}.part")
                if (tempOutputFile?.exists() == true) {
                    tempOutputFile.delete()
                }
            }
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

private fun installArchRootfsIfNeeded(onInstallLog: (String) -> Unit) {
    val localBase = localDir()
    val archDir = File(localBase, "arch")
    val markerFile = localBase.child(ARCH_READY_MARKER)
    val filesDir = application!!.filesDir
    val rootfsArchive = filesDir.child("arch.tar.gz")
    val hasEtc = archDir.child("etc").exists() || archDir.child("root").child("etc").exists()

    if (hasEtc && !markerFile.exists()) {
        normalizeArchResolvConf(archDir)
        markerFile.writeText("installed")
        cleanupArchArchiveIfPresent(rootfsArchive, onInstallLog)
        onInstallLog("Arch filesystem was already extracted; marker recreated")
        return
    }

    if (markerFile.exists() && hasEtc) {
        normalizeArchResolvConf(archDir)
        cleanupArchArchiveIfPresent(rootfsArchive, onInstallLog)
        onInstallLog("Arch filesystem already prepared")
        return
    }

    if (markerFile.exists()) {
        markerFile.delete()
    }

    if (archDir.exists() && archDir.listFiles().isNullOrEmpty().not()) {
        onInstallLog("Detected invalid existing Arch rootfs; cleaning and reinstalling")
        if (!archDir.deleteRecursively()) {
            throw Exception("Arch setup aborted: existing rootfs cleanup failed.")
        }
    }

    val prefix = filesDir.parentFile!!.absolutePath
    val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
    val prootBin = filesDir.child("proot")
    val stagingDir = File(localBase, "arch.installing")

    if (!prootBin.exists()) {
        throw Exception("Arch setup failed: missing proot binary")
    }

    if (!rootfsArchive.exists()) {
        throw Exception("Arch setup failed: missing arch.tar.gz")
    }

    val tempDir = App.getTempDir().child("arch-install")
    if (!tempDir.exists()) {
        tempDir.mkdirs()
    }

    var movedIntoPlace = false
    try {
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }

        val extractScript = """
            rm -rf "${stagingDir.absolutePath}" && mkdir -p "${stagingDir.absolutePath}" && \
            tar -xvf "${rootfsArchive.absolutePath}" -C "${stagingDir.absolutePath}"
        """.trimIndent()

        val process = ProcessBuilder(
            linker,
            prootBin.absolutePath,
            "--link2symlink",
            "-0",
            "-r", "/",
            "-w", "/",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/dev",
            "-b", "/data",
            "-b", "/storage",
            "-b", "/sdcard",
            "-b", prefix,
            "/system/bin/sh",
            "-c",
            extractScript
        ).redirectErrorStream(true)

        val env = process.environment()
        val existingLdPath = env["LD_LIBRARY_PATH"]
        val joinedLdPath = listOfNotNull(filesDir.absolutePath, existingLdPath).joinToString(":")
        env["LD_LIBRARY_PATH"] = joinedLdPath
        env["PROOT_TMP_DIR"] = tempDir.absolutePath
        env["PREFIX"] = prefix

        val output = StringBuilder()
        val running = process.start()
        onInstallLog("Extracting arch.tar.gz into staging directory")
        val outputCollector = Thread {
            running.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.appendLine(line)
                    onInstallLog(line)
                }
            }
        }.apply { start() }

        val finished = running.waitFor(ARCH_EXTRACT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        if (!finished) {
            running.destroy()
            if (running.isAlive) {
                running.destroyForcibly()
            }
            outputCollector.join(1000)
            throw Exception("Arch setup timed out during extraction after $ARCH_EXTRACT_TIMEOUT_MINUTES minutes")
        }

        outputCollector.join(1000)
        val exitCode = running.exitValue()
        if (exitCode != 0) {
            throw Exception("Arch setup failed during extraction: ${output.toString().trim()}")
        }

        val extracted = stagingDir.child("etc").exists() || stagingDir.child("root").child("etc").exists()
        if (!extracted) {
            throw Exception("Arch setup failed: extracted rootfs has no /etc")
        }

        normalizeArchResolvConf(stagingDir)

        if (archDir.exists()) {
            archDir.deleteRecursively()
        }

        if (!stagingDir.renameTo(archDir)) {
            throw Exception("Arch setup failed: cannot move staged rootfs into place")
        }
        movedIntoPlace = true

        markerFile.writeText("installed")
        cleanupArchArchiveIfPresent(rootfsArchive, onInstallLog)

        onInstallLog("Arch rootfs ready")
    } finally {
        if (!movedIntoPlace && stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}

private fun hasExtractedArchRootfs(): Boolean {
    val archDir = File(localDir(), "arch")
    return archDir.child("etc").exists() || archDir.child("root").child("etc").exists()
}

private fun cleanupArchArchiveIfPresent(rootfsArchive: File, onInstallLog: (String) -> Unit) {
    if (rootfsArchive.exists()) {
        if (rootfsArchive.delete()) {
            onInstallLog("Removed arch.tar.gz to save space")
        } else {
            onInstallLog("Warning: failed to delete arch.tar.gz")
        }
    }
}

private fun normalizeArchResolvConf(rootfsDir: File) {
    val etcDir = when {
        rootfsDir.child("etc").isDirectory -> rootfsDir.child("etc")
        rootfsDir.child("root").child("etc").isDirectory -> rootfsDir.child("root").child("etc")
        else -> return
    }

    val resolvConf = etcDir.child("resolv.conf")
    resolvConf.parentFile?.mkdirs()
    runCatching { resolvConf.delete() }
    resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
}

private suspend fun downloadFileWithFallback(urls: List<String>, outputFile: File, onProgress: (Long, Long) -> Unit) {
    var lastError: Exception? = null
    for (url in urls) {
        try {
            downloadFile(url, outputFile, onProgress)
            return
        } catch (e: Exception) {
            lastError = e
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }
    throw Exception("All download URLs failed: ${urls.joinToString(" | ")}", lastError)
}

private suspend fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
    withContext(Dispatchers.IO) {
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download file from $url: HTTP ${response.code}")

            val body = response.body ?: throw Exception("Empty response body from $url")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            var downloadedBytes = 0L

            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
        }
    }
}

private fun verifySha256OrThrow(file: File, expectedSha256: String) {
    val actualSha256 = file.sha256()
    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
        throw Exception(
            "Checksum mismatch for ${file.name}: expected $expectedSha256 but got $actualSha256"
        )
    }
}

private fun File.matchesSha256(expectedSha256: String): Boolean {
    return sha256().equals(expectedSha256, ignoreCase = true)
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val RUNTIME_RELEASE_BASE_URL =
    "https://github.com/jeffusion/termix-runtime-binaries/releases/download/runtime-v1.0.0"

private fun runtimeReleaseUrl(assetName: String): String {
    return "$RUNTIME_RELEASE_BASE_URL/$assetName"
}

private val abiMap = mapOf(
    "x86_64" to AbiUrls(
        talloc = RuntimeAsset(
            url = runtimeReleaseUrl("libtalloc.so.2-x86_64"),
            sha256 = "cfa05bd07fb7e7fe2adda4a0309275d4e03e0102be368b02b800bba4b0cad4ae"
        ),
        proot = RuntimeAsset(
            url = runtimeReleaseUrl("proot-x86_64"),
            sha256 = "eca2f07a87bd0ae4acb0547de867e43d08c176daa09ff9f59573e0a53c92011e"
        ),
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.0-x86_64.tar.gz",
        arch = null
    ),
    "arm64-v8a" to AbiUrls(
        talloc = RuntimeAsset(
            url = runtimeReleaseUrl("libtalloc.so.2-aarch64"),
            sha256 = "368262b345120a4e09ae961f4bda92e0cdfc18004145317f923abe403df6facf"
        ),
        proot = RuntimeAsset(
            url = runtimeReleaseUrl("proot-aarch64"),
            sha256 = "f25ac0a0258e18671c699154cdc88001fbd1cbe09df75c960a1ee8dad29d88ae"
        ),
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz",
        arch = listOf(
            "https://mirrors.dotsrc.org/archlinuxarm/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://ca.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        )
    ),
    "armeabi-v7a" to AbiUrls(
        talloc = RuntimeAsset(
            url = runtimeReleaseUrl("libtalloc.so.2-arm"),
            sha256 = "f7e46ad757494f73e1b2bfe4bab51b28176741e23c92be8924e4dc4a5ca6921e"
        ),
        proot = RuntimeAsset(
            url = runtimeReleaseUrl("proot-arm"),
            sha256 = "2c1e1a6008aabedeb8f9404091e39624bdc21b0640ed846e12b141966a43dc72"
        ),
        alpine = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armhf/alpine-minirootfs-3.21.0-armhf.tar.gz",
        arch = listOf(
            "https://mirrors.dotsrc.org/archlinuxarm/os/ArchLinuxARM-armv7-latest.tar.gz",
            "https://ca.us.mirror.archlinuxarm.org/os/ArchLinuxARM-armv7-latest.tar.gz"
        )
    )
)

private data class AbiUrls(
    val talloc: RuntimeAsset,
    val proot: RuntimeAsset,
    val alpine: String,
    val arch: List<String>?
)

private data class RuntimeAsset(
    val url: String,
    val sha256: String
)

private const val ARCH_EXTRACT_TIMEOUT_MINUTES = 30L
private const val ARCH_READY_MARKER = ".termix-arch-installed"
private const val MAX_INSTALL_LOG_LINES = 220
private const val NANOS_PER_SECOND = 1_000_000_000.0

private data class DownloadProgressSnapshot(
    val currentFileName: String,
    val completedFiles: Int,
    val totalFiles: Int,
    val fileProgress: Float,
    val overallProgress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val isRootfs: Boolean
)

private fun modeLabel(workingMode: Int): String = when (workingMode) {
    WorkingMode.ARCH -> "ARCH"
    WorkingMode.ARCH_ROOT -> "ARCH ROOT"
    WorkingMode.ALPINE_ROOT -> "ALPINE ROOT"
    else -> "ALPINE"
}

private fun formatRate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "--"
    return "${formatBytes(bytesPerSecond)}/s"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) {
        "${value.toInt()} ${units[index]}"
    } else {
        String.format("%.2f %s", value, units[index])
    }
}
