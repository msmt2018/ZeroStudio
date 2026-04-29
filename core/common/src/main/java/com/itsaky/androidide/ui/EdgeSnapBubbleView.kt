package com.itsaky.androidide.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * SlideBackView 一比一 Kotlin 版（按项目需求做最小改动）。
 */
class EdgeSnapBubbleView : View {

  /** backView 高度 */
  private var backViewHeight: Float = 0f

  /** 边缘触发距离 */
  private var backEdgeWidth: Float = 0f

  /** 最大返回触发距离 */
  private var backMaxWidth: Float = 0f

  private var thresholdLeft: Float = 0f
  private var thresholdRight: Float = 0f

  private var currentY: Float = 0f
  private var downX: Float = 0f
  private var isEdge: Boolean = false
  private var deltaX: Float = 0f
  private var mWidth: Int = 0
  private var isOnlyLeftBack: Boolean = false
  var left: Boolean = false
  var right: Boolean = false
  var forwardX: Float = 0f
  var bufferX: Float = 0f

  private var backPaint: Paint? = null
  private var arrowPaint: Paint? = null
  private var backPath: Path? = null
  private var arrowPath: Path? = null

  private var onBackListener: OnBackListener? = null
  private var side: Side = Side.LEFT
  private var showArrowUp: Boolean = true

  fun setOnBackListener(onBackListener: OnBackListener?) {
    this.onBackListener = onBackListener
  }

  fun setOnlyLeftBack(onlyLeftBack: Boolean) {
    isOnlyLeftBack = onlyLeftBack
  }

  constructor(context: Context) : this(context, null)

  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    // 原始源码通过 styleable/dimen 读取。当前模块未定义对应 attrs，改为等价默认值。
    val density = resources.displayMetrics.density
    backViewHeight = 220f * density
    backEdgeWidth = 12f * density
    backMaxWidth = 56f * density
    init()
  }

  private fun init() {
    backPath = Path()
    arrowPath = Path()

    backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    backPaint!!.color = 0xAA000000.toInt()
    backPaint!!.style = Paint.Style.FILL_AND_STROKE

    arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    arrowPaint!!.color = Color.WHITE
    arrowPaint!!.style = Paint.Style.STROKE
    arrowPaint!!.strokeWidth = 6f
    arrowPaint!!.strokeCap = Paint.Cap.ROUND
    arrowPaint!!.strokeJoin = Paint.Join.ROUND
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    val currentX = ev.x
    currentY = ev.y
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        downX = ev.x
        forwardX = downX
        // 原始逻辑保留：边缘触发。
        if (downX <= backEdgeWidth) {
          isEdge = true
          left = true
          right = false
        } else if (downX >= width - backEdgeWidth) {
          isEdge = true
          right = true
          left = false
        }
        // 项目需求：固定吸附在 page_switch_container 顶部边缘可直接显示与点击。
        // 因此若未命中边缘，也允许进入左侧形态绘制和点击，不隐藏。
        if (!isEdge) {
          isEdge = true
          left = true
          right = false
        }
      }

      MotionEvent.ACTION_MOVE -> {
        deltaX = currentX - downX
        val diff = forwardX - currentX
        if (diff > 0) {
          if (currentX < thresholdLeft && left) {
            deltaX = backMaxWidth
            deltaX -= (thresholdLeft - currentX) / 2
            bufferX = deltaX
            if (deltaX < 0) {
              deltaX = 0f
              bufferX = 0f
            }
          } else if (currentX > thresholdRight && right) {
            bufferX -= diff
            deltaX = bufferX
          }
        } else {
          if (currentX < thresholdLeft && left) {
            bufferX += abs(diff)
            deltaX = bufferX
          } else if (currentX > thresholdRight && right) {
            deltaX = -backMaxWidth
            deltaX += (currentX - thresholdRight) / 2
            bufferX = deltaX
            if (deltaX < -backMaxWidth) {
              deltaX = -backMaxWidth
              bufferX = -backMaxWidth
            }
          }
        }
        forwardX = currentX
        if (isEdge) {
          invalidate()
        }
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isEdge) {
          if (deltaX >= backMaxWidth && left) {
            back()
          } else if (abs(deltaX) >= backMaxWidth && right) {
            if (!isOnlyLeftBack) {
              back()
            }
          }
          // 轻触也触发点击切换。
          if (abs(deltaX) < backMaxWidth * 0.2f) {
            performClick()
          }
          deltaX = 0f
          invalidate()
        }
        left = false
        right = false
        isEdge = false
        bufferX = 0f
      }
    }
    return isEdge
  }

  private fun back() {
    if (onBackListener != null) {
      onBackListener!!.onBack()
    } else {
      @Suppress("DEPRECATION")
      (context as? Activity)?.onBackPressed()
      performClick()
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    mWidth = MeasureSpec.getSize(widthMeasureSpec) + 1
    thresholdLeft = (mWidth / 3).toFloat()
    thresholdRight = thresholdLeft * 2
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (deltaX > backMaxWidth && left) {
      deltaX = backMaxWidth
    } else if (deltaX < -backMaxWidth && right) {
      deltaX = -backMaxWidth
    }
    Log.e("onDraw", "" + deltaX)
    val deltaY = currentY - backViewHeight / 2
    backPath!!.reset()
    arrowPath!!.reset()

    // 保留默认可见山峰（无拖动时）
    val drawDelta = if (deltaX == 0f) backMaxWidth * 0.35f else abs(deltaX)

    if ((deltaX > 0 && left) || (deltaX == 0f && !right)) {
      backPath!!.moveTo(0f, deltaY)
      backPath!!.quadTo(0f, backViewHeight / 4 + deltaY, drawDelta / 3, backViewHeight * 3 / 8 + deltaY)
      backPath!!.quadTo(drawDelta * 5 / 8, backViewHeight / 2 + deltaY, drawDelta / 3, backViewHeight * 5 / 8 + deltaY)
      backPath!!.quadTo(0f, backViewHeight * 6 / 8 + deltaY, 0f, backViewHeight + deltaY)
      canvas.drawPath(backPath!!, backPaint!!)

      val midX = drawDelta / 6f
      val topY = backViewHeight * 15f / 32f + deltaY
      val centerY = backViewHeight * 16f / 32f + deltaY
      val bottomY = backViewHeight * 17f / 32f + deltaY
      val tipOffset = 15f * (drawDelta / (mWidth / 6f))
      if (showArrowUp) {
        arrowPath!!.moveTo(midX, centerY)
        arrowPath!!.lineTo(midX + tipOffset, topY)
        arrowPath!!.moveTo(midX, centerY)
        arrowPath!!.lineTo(midX + tipOffset, bottomY)
      } else {
        arrowPath!!.moveTo(midX + tipOffset, centerY)
        arrowPath!!.lineTo(midX, topY)
        arrowPath!!.moveTo(midX + tipOffset, centerY)
        arrowPath!!.lineTo(midX, bottomY)
      }
      canvas.drawPath(arrowPath!!, arrowPaint!!)
    } else if (deltaX < 0 && right) {
      if (!isOnlyLeftBack) {
        deltaX = abs(deltaX)
        backPath!!.moveTo(mWidth.toFloat(), deltaY)
        backPath!!.quadTo(mWidth.toFloat(), backViewHeight / 4 + deltaY, mWidth - deltaX / 3, backViewHeight * 3 / 8 + deltaY)
        backPath!!.quadTo(mWidth - deltaX * 5 / 8, backViewHeight / 2 + deltaY, mWidth - deltaX / 3, backViewHeight * 5 / 8 + deltaY)
        backPath!!.quadTo(mWidth.toFloat(), backViewHeight * 6 / 8 + deltaY, mWidth.toFloat(), backViewHeight + deltaY)
        canvas.drawPath(backPath!!, backPaint!!)

        arrowPath!!.moveTo(mWidth - deltaX / 6 - 15 * (deltaX / (mWidth / 6f)), backViewHeight * 15 / 32 + deltaY)
        arrowPath!!.lineTo(mWidth - deltaX / 6, backViewHeight * 16.1f / 32 + deltaY)
        arrowPath!!.moveTo(mWidth - deltaX / 6, backViewHeight * 15.9f / 32 + deltaY)
        arrowPath!!.lineTo(mWidth - deltaX / 6 - 15 * (deltaX / (mWidth / 6f)), backViewHeight * 17 / 32 + deltaY)
        canvas.drawPath(arrowPath!!, arrowPaint!!)
      }
    }
    alpha = (drawDelta / backMaxWidth).coerceIn(0.35f, 1f)
  }

  fun setBackViewHeight(backViewHeight: Float) {
    this.backViewHeight = backViewHeight
  }

  fun setBackEdgeWidth(backEdgeWidth: Float) {
    this.backEdgeWidth = backEdgeWidth
  }

  fun setBackMaxWidth(backMaxWidth: Float) {
    this.backMaxWidth = backMaxWidth
  }

  fun getBackEdgeWidth(): Float = backEdgeWidth

  fun getBackMaxWidth(): Float = backMaxWidth

  fun getBackViewHeight(): Float = backViewHeight

  fun attachToSide(newSide: Side) {
    side = newSide
    val parentView = parent as? View ?: return
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
  }

  fun restorePosition() {
    val parentView = parent as? View ?: return
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun setArrowExpanded(expanded: Boolean) {
    // expanded=true 表示容器显示中，箭头朝上（点击后收起）
    showArrowUp = expanded
    invalidate()
  }

  enum class Side { LEFT, RIGHT }

  interface OnBackListener {
    fun onBack()
  }
}
