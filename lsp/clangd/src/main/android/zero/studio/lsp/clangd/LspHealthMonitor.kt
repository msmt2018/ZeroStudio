package android.zero.studio.lsp.clangd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * LSP 健康监控
 * 
 * 监听 LSP 运行状况并在必要时给出即时反馈
 */
object LspHealthMonitor : LspService.HealthListener {

    private const val TAG = "LspHealthMonitor"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    fun start(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        LspService.addHealthListener(this)
    }

    override fun onHealthEvent(type: LspService.HealthEventType, message: String) {
        val ctx = appContext ?: return
        val toastText = when (type) {
            LspService.HealthEventType.INIT_FAILURE -> "LSP 初始化失败：$message"
            LspService.HealthEventType.CHANNEL_ERROR -> "LSP 通信异常：$message"
            LspService.HealthEventType.TRANSPORT_ERROR -> "LSP 传输异常：$message"
            LspService.HealthEventType.IO_ERROR -> "LSP I/O 异常：$message"
            LspService.HealthEventType.CLANGD_EXIT -> "clangd 进程已退出：$message"
        }
        Log.w(TAG, "Health event $type: $message")
        mainHandler.post {
            Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
        }
    }
}
