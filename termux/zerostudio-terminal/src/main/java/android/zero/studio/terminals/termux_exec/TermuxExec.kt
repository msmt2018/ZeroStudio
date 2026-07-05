package com.termix.termux_exec

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.termix.libcommons.TerminalCommand
import com.termix.libcommons.application
import com.termix.libcommons.pendingCommand
import com.termix.libcommons.toast
import com.termix.resources.getString
import com.termix.ui.activities.terminal.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val TERMUX_PKG = "com.termux"

@SuppressLint("SdCardPath")
const val TERMUX_PREFIX = "/data/data/$TERMUX_PKG/files/usr"

fun isTermuxInstalled(): Boolean {
    val packageManager: PackageManager = application!!.packageManager
    val intent = packageManager.getLaunchIntentForPackage(TERMUX_PKG) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return list.size > 0
}

private fun checkTermuxInstall() {
    if (isTermuxInstalled().not()) {
        throw RuntimeException("Termux not installed")
    }
    if (isTermuxCompatible().not()) {
        throw RuntimeException("Termux not compatible")
    }
    if (isExecPermissionGranted().not()) {
        throw RuntimeException("Termux exec permission denied")
    }
}

fun isTermuxCompatible(): Boolean {
    val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
        setPackage(TERMUX_PKG)
    }
    val activities = application!!.packageManager.queryIntentServices(intent, 0)
    return activities.isNotEmpty()
}

fun testExecPermission(): Pair<Boolean, Exception?> {
    try {
        checkTermuxInstall()
        runCommandTermux(application!!, "$TERMUX_PREFIX/bin/echo", arrayOf(), background = true, isTesting = true)
        return Pair(true, null)
    } catch (e: Exception) {
        return Pair(false, e)
    }
}


fun isExecPermissionGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
        application!!, "com.termux.permission.RUN_COMMAND"
    ) == PackageManager.PERMISSION_GRANTED
}

@OptIn(DelicateCoroutinesApi::class)
fun runCommandTermux(
    context: Context,
    exe: String,
    args: Array<String>,
    background: Boolean = true,
    cwd: String? = null,
    isTesting: Boolean = false
) {
    runCatching { checkTermuxInstall() }.onFailure { toast(it.message) }.onSuccess {
        GlobalScope.launch(Dispatchers.Main) {
            if (isTesting.not()) {
                runCatching { launchTermux() }
                delay(200)
            }
            val intent = Intent("$TERMUX_PKG.RUN_COMMAND").apply {
                setClassName(TERMUX_PKG, "$TERMUX_PKG.app.RunCommandService")
                putExtra("$TERMUX_PKG.RUN_COMMAND_PATH", exe)
                putExtra("$TERMUX_PKG.RUN_COMMAND_ARGUMENTS", args)
                putExtra("$TERMUX_PKG.RUN_COMMAND_BACKGROUND", background)
                cwd?.let { cwd ->
                    putExtra("$TERMUX_PKG.RUN_COMMAND_SERVICE.EXTRA_WORKDIR", cwd)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            }else{
                context.startService(intent)
            }
        }
    }
}


fun runBashScript(
    context: Context, script: String, workingDir: String? = null, background: Boolean = false
) {
    runCommandTermux(
        context = context,
        exe = "$TERMUX_PREFIX/bin/bash",
        arrayOf("-c", script),
        background = background,
        cwd = workingDir
    )
}

fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    pendingCommand = terminalCommand
    context.startActivity(Intent(context, MainActivity::class.java))
}

fun launchTermux(): Boolean {
    if (isTermuxInstalled().not()) {
        return false
    }
    application!!.startActivity(application!!.packageManager.getLaunchIntentForPackage(TERMUX_PKG))
    return true
}
