package android.zero.studio.termux.ui.tabs.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/** Helper class for tab drag and reorder animations */
object TabDragAnimationHelper {

  private const val ANIMATION_DURATION = 250L
  private const val ELEVATION_DRAG = 8f
  private const val ELEVATION_NORMAL = 2f
  private const val SCALE_DRAG = 1.05f
  private const val SCALE_NORMAL = 1.0f

  /** Animates a tab being picked up for dragging */
  fun animatePickUp(view: View, onComplete: (() -> Unit)? = null) {
    view
        .animate()
        .scaleX(SCALE_DRAG)
        .scaleY(SCALE_DRAG)
        .translationZ(ELEVATION_DRAG)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(FastOutSlowInInterpolator())
        .setListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
              }
            }
        )
        .start()
  }

  /** Animates a tab being dropped after dragging */
  fun animateDrop(view: View, onComplete: (() -> Unit)? = null) {
    view
        .animate()
        .scaleX(SCALE_NORMAL)
        .scaleY(SCALE_NORMAL)
        .translationZ(ELEVATION_NORMAL)
        .translationX(0f)
        .translationY(0f)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(OvershootInterpolator(1.2f))
        .setListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
              }
            }
        )
        .start()
  }

  /** Animates a tab moving to make space for another tab */
  fun animateMove(view: View, translationX: Float, onComplete: (() -> Unit)? = null) {
    view
        .animate()
        .translationX(translationX)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .setListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
              }
            }
        )
        .start()
  }

  /** Animates a tab being inserted (new session) */
  fun animateInsert(view: View, onComplete: (() -> Unit)? = null) {
    view.alpha = 0f
    view.scaleX = 0.5f
    view.scaleY = 0.5f

    view
        .animate()
        .alpha(1f)
        .scaleX(SCALE_NORMAL)
        .scaleY(SCALE_NORMAL)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(OvershootInterpolator(1.5f))
        .setListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                onComplete?.invoke()
              }
            }
        )
        .start()
  }

  /** Animates a tab being removed (closed) */
  fun animateRemove(view: View, onComplete: (() -> Unit)? = null) {
    view
        .animate()
        .alpha(0f)
        .scaleX(0.5f)
        .scaleY(0.5f)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .setListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                view.visibility = View.GONE
                onComplete?.invoke()
              }
            }
        )
        .start()
  }

  /** Animates the active tab indicator */
  fun animateActiveIndicator(view: View) {
    val scaleAnimator = ValueAnimator.ofFloat(1.0f, 1.1f, 1.0f)
    scaleAnimator.duration = 300L
    scaleAnimator.addUpdateListener { animator ->
      val scale = animator.animatedValue as Float
      view.scaleX = scale
      view.scaleY = scale
    }
    scaleAnimator.start()
  }

  /** Cancels all animations on a view */
  fun cancelAnimations(view: View) {
    view.animate().cancel()
    view.clearAnimation()
  }
}
