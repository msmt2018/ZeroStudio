package android.zero.studio.termux.ui.tabs.utils

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Helper class for creating gradient backgrounds with blur effects
 *
 * @author android_zero
 */
object GradientBlurHelper {

  /** Predefined gradient color schemes for tabs */
  val GRADIENT_SCHEMES =
      listOf(
          intArrayOf(0xFFFFB3BA.toInt(), 0xFFFFDFBA.toInt()), // Pink to Peach
          intArrayOf(0xFFBAE1FF.toInt(), 0xFFBBE3FF.toInt()), // Light Blue
          intArrayOf(0xFFFFBABA.toInt(), 0xFFBABAFF.toInt()), // Pink to Purple
          intArrayOf(0xFFBAFFE9.toInt(), 0xFFBAE1FF.toInt()), // Cyan to Blue
          intArrayOf(0xFFFFDBAC.toInt(), 0xFFFFC8A2.toInt()), // Orange gradient
          intArrayOf(0xFFD4BAFF.toInt(), 0xFFC9BAFF.toInt()), // Purple gradient
          intArrayOf(0xFFBAFFC9.toInt(), 0xFFBAFFBA.toInt()), // Green gradient
          intArrayOf(0xFFFFF6BA.toInt(), 0xFFFFEBA4.toInt()), // Yellow gradient
      )

  /** Creates a gradient drawable */
  fun createGradientDrawable(
      colors: IntArray,
      cornerRadius: Float = 24f,
      orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT,
  ): GradientDrawable {
    return GradientDrawable(orientation, colors).apply { this.cornerRadius = cornerRadius }
  }

  /** Applies gradient background to a view */
  fun applyGradientToView(view: View, colorSchemeIndex: Int) {
    val colors = GRADIENT_SCHEMES[colorSchemeIndex % GRADIENT_SCHEMES.size]
    view.background = createGradientDrawable(colors)
  }

  /** Applies blur effect to a view (Android 12+) */
  @RequiresApi(Build.VERSION_CODES.S)
  fun applyBlurEffect(view: View, blurRadius: Float = 10f) {
    view.setRenderEffect(
        RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
    )
  }

  /** Gets a color scheme by index */
  fun getColorScheme(index: Int): IntArray {
    return GRADIENT_SCHEMES[index % GRADIENT_SCHEMES.size]
  }
}
