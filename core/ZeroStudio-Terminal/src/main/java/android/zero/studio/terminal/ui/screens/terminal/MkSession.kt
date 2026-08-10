package android.zero.studio.termux.ui.screens.terminal

import android.os.Environment
import android.os.Build
import android.zero.studio.termux.libcommons.alpineDir
import android.zero.studio.termux.libcommons.alpineHomeDir
import android.zero.studio.termux.libcommons.archHomeDir
import android.zero.studio.termux.libcommons.application
import android.zero.studio.termux.libcommons.child
import android.zero.studio.termux.libcommons.createFileIfNot
import android.zero.studio.termux.libcommons.localBinDir
import android.zero.studio.termux.libcommons.localDir
import android.zero.studio.termux.libcommons.localLibDir
import android.zero.studio.termux.libcommons.pendingCommand
import android.zero.studio.termux.settings.Settings
import android.zero.studio.termux.App
import android.zero.studio.termux.App.Companion.getTempDir
import android.zero.studio.termux.BuildConfig
import android.content.Context
import android.zero.studio.termux.model.WorkingMode
import android.zero.studio.termux.ui.screens.settings.ShellType
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.FileOutputStream

object MkSession {
    fun createSession(
        context: Context, sessionClient: TerminalSessionClient, session_id: String,workingMode:Int
    ): TerminalSession {
        with(context) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val ubuntuRootfsAbi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86" }
                ?.let { if (it == "arm64-v8a") "arm64" else if (it == "armeabi-v7a") "armhf" else "i386" }
                ?: "unknown"
            val ubuntuRootfsId = "ubuntu-${Settings.linux_distribution_version.lowercase().replace(" ", "-")}-$ubuntuRootfsAbi"
            val defaultWorkingDir = when (workingMode) {
                WorkingMode.ARCH,
                WorkingMode.ARCH_ROOT -> archHomeDir().path
                WorkingMode.UBUNTU,
                WorkingMode.UBUNTU_ROOT -> filesDir.path
                else -> alpineHomeDir().path
            }
            val workingDir = pendingCommand?.workingDir ?: defaultWorkingDir

            val initFile: File = localBinDir().child("init-host")

            if (initFile.exists().not()){
                initFile.createFileIfNot()
                initFile.writeText(assets.open("init-host.sh").bufferedReader().use { it.readText() })
            }


            localBinDir().child("init").apply {
                if (exists().not()){
                    createFileIfNot()
                    writeText(assets.open("init.sh").bufferedReader().use { it.readText() })
                }
                setExecutable(true)
            }

            localBinDir().child("init-root").apply {
                if (exists().not()){
                    createFileIfNot()
                    writeText(assets.open("init-root.sh").bufferedReader().use { it.readText() })
                }
                setExecutable(true)
            }

            localBinDir().child("init-arch").apply {
                createFileIfNot()
                writeText(assets.open("init-arch.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }

            localBinDir().child("init-arch-host").apply {
                createFileIfNot()
                writeText(assets.open("init-arch-host.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }

            localBinDir().child("init-arch-root").apply {
                createFileIfNot()
                writeText(assets.open("init-arch-root.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }

            localBinDir().child("init-ubuntu-host").apply {
                createFileIfNot()
                writeText(assets.open("init-ubuntu-host.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }


            val sessionTmpDir = getTempDir().child(session_id).also {
                if (it.exists()) {
                    it.deleteRecursively()
                }
                it.mkdirs()
            }

            val env = mutableListOf(
                "PATH=${System.getenv("PATH")}:/sbin:${localBinDir().absolutePath}",
                "HOME=/sdcard",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=${localBinDir()}",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "TERMIX_ROOTFS_ID=$ubuntuRootfsId",
                "TERMIX_ROOTFS_ARCHIVE=$ubuntuRootfsId.tar.gz",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
                "PROOT_TMP_DIR=${sessionTmpDir.absolutePath}",
                "TMPDIR=${getTempDir().absolutePath}"
            )

            // Do NOT set PROOT_LOADER/PROOT_LOADER32 — let proot use its embedded loader.
            // External loaders from jniLibs conflict with proot's ashmem_memfd extension
            // and fail on Android 10+ due to W^X (Write XOR Execute) policy.

            val shellPath = when (Settings.default_shell) {
                ShellType.BASH -> "/bin/bash"
                ShellType.ZSH -> "/bin/zsh"
                ShellType.ASH -> "/bin/ash"
                else -> "/bin/ash"
            }
            env.add("TERMIX_SHELL=$shellPath")

            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            localDir().child("stat").apply {
                if (exists().not()){
                    writeText(stat)
                }
            }

            localDir().child("vmstat").apply {
                if (exists().not()){
                    writeText(vmstat)
                }
            }

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val args: Array<String>

            val shell = if (pendingCommand == null) {
                args = when (workingMode) {
                    WorkingMode.ALPINE -> arrayOf("-c", initFile.absolutePath)
                    WorkingMode.ALPINE_ROOT -> arrayOf("-c", localBinDir().child("init-root").absolutePath)
                    WorkingMode.ARCH -> arrayOf("-c", localBinDir().child("init-arch-host").absolutePath)
                    WorkingMode.ARCH_ROOT -> arrayOf("-c", localBinDir().child("init-arch-root").absolutePath)
                    WorkingMode.UBUNTU,
                    WorkingMode.UBUNTU_ROOT -> arrayOf("-c", localBinDir().child("init-ubuntu-host").absolutePath)
                    else -> arrayOf()
                }
                "/system/bin/sh"
            } else{
                args = pendingCommand!!.args
                pendingCommand!!.shell
            }

            pendingCommand = null
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                Settings.scrollback_lines,
                sessionClient,
            )
        }

    }
}
