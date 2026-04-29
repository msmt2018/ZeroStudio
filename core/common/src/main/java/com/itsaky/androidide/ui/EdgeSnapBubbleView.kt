package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Fixed-position bubble for page-switch/IDE drawer gestures.
 * Removed: physical-back edge logic & left/right edge snapping behavior.
 */
class EdgeSnapBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

  private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xAA000000.toInt()
    style = Paint.Style.FILL_AND_STROKE
  }
  private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 6f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val backPath = Path()
  private val arrowPath = Path()

  private val backViewHeight = 220f * resources.displayMetrics.density
  private val backMaxWidth = 56f * resources.displayMetrics.density

  private var currentY: Float = backViewHeight / 2f
  private var downRawY = 0f
  private var showArrowUp = true

  private var verticalDragListener: ((Float) -> Unit)? = null

  override fun onTouchEvent(event: MotionEvent): Boolean {
    currentY = event.y
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawY = event.rawY
        verticalDragListener?.invoke(event.rawY)
      }
      MotionEvent.ACTION_MOVE -> verticalDragListener?.invoke(event.rawY)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (abs(event.rawY - downRawY) < resources.displayMetrics.density * 8f) {
          performClick()
        }
      }
    }
    invalidate()
    return true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val deltaY = currentY - backViewHeight / 2f
    val drawDelta = backMaxWidth * 0.35f

    backPath.reset()
    arrowPath.reset()
    backPath.moveTo(0f, deltaY)
    backPath.quadTo(0f, backViewHeight / 4 + deltaY, drawDelta / 3, backViewHeight * 3 / 8 + deltaY)
    backPath.quadTo(drawDelta * 5 / 8, backViewHeight / 2 + deltaY, drawDelta / 3, backViewHeight * 5 / 8 + deltaY)
    backPath.quadTo(0f, backViewHeight * 6 / 8 + deltaY, 0f, backViewHeight + deltaY)
    canvas.drawPath(backPath, backPaint)

    val midX = drawDelta / 6f
    val topY = backViewHeight * 15f / 32f + deltaY
    val centerY = backViewHeight * 16f / 32f + deltaY
    val bottomY = backViewHeight * 17f / 32f + deltaY
    val tipOffset = 15f
    if (showArrowUp) {
      arrowPath.moveTo(midX, centerY)
      arrowPath.lineTo(midX + tipOffset, topY)
      arrowPath.moveTo(midX, centerY)
      arrowPath.lineTo(midX + tipOffset, bottomY)
    } else {
      arrowPath.moveTo(midX + tipOffset, centerY)
      arrowPath.lineTo(midX, topY)
      arrowPath.moveTo(midX + tipOffset, centerY)
      arrowPath.lineTo(midX, bottomY)
    }
    canvas.drawPath(arrowPath, arrowPaint)
    alpha = 1f
  }

  fun setOnVerticalDragListener(listener: ((Float) -> Unit)?) {
    verticalDragListener = listener
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun setArrowExpanded(expanded: Boolean) {
    showArrowUp = expanded
    invalidate()
  }
}
