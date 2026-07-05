package android.zero.studio.termux.crashhandler

import android.os.Looper
import android.zero.studio.termux.libcommons.application
import android.zero.studio.termux.libcommons.child
import android.zero.studio.termux.libcommons.createFileIfNot
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        runCatching {

        }.onFailure {
            it.printStackTrace()
            exitProcess(1)
        }

        if (Looper.myLooper() != null) {
            while (true) {
                try {
                    Looper.loop()
                    return
                } catch (t: Throwable) {
                    Thread{
                        t.printStackTrace()
                        logErrorOrExit(t)
                    }.start()
                }
            }
        }
    }
}

fun logErrorOrExit(throwable: Throwable){
    runCatching {
        application!!.filesDir.child("crash.log").createFileIfNot().appendText(throwable.toString())
    }.onFailure { it.printStackTrace();exitProcess(-1) }
}
