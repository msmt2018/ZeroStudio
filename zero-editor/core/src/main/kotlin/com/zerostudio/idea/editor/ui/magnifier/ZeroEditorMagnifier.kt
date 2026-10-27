package com.zerostudio.idea.editor.ui.magnifier

import android.os.Build
import android.view.View
import android.widget.Magnifier
import androidx.annotation.RequiresApi

/**
 * 工业级代码编辑高精度放大镜调度器
 */
class ZeroEditorMagnifier(private val targetView: View) {

    private var activeMagnifier: Any? = null
    private var isMagnifying = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            initSystemMagnifier()
        }
    }

    private fun initSystemMagnifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activeMagnifier = Magnifier.Builder(targetView)
                .setSize(420, 180)
                .setInitialZoom(1.4f)
                .setCornerRadius(24f)
                .setElevation(16f)
                .build()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activeMagnifier = Magnifier(targetView)
        }
    }

    /**
     * 激活并更新放大镜位置（自动限制在 View 物理边界内）
     */
    fun showOrMove(sourceXPx: Float, sourceYPx: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val magnifier = activeMagnifier as? Magnifier ?: return
        val clampedX = sourceXPx.coerceIn(0f, targetView.width.toFloat())
        val clampedY = sourceYPx.coerceIn(0f, targetView.height.toFloat())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 支持源坐标与投影目标坐标解耦
            magnifier.show(clampedX, clampedY)
        } else {
            magnifier.show(clampedX, clampedY)
        }
        isMagnifying = true
    }

    /**
     * 隐藏并释放放大镜
     */
    fun dismiss() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isMagnifying) {
            (activeMagnifier as? Magnifier)?.dismiss()
            isMagnifying = false
        }
    }
}