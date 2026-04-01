package com.itsaky.androidide.fragments.git.tree

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.rk.filetree.model.TreeNode
import androidx.annotation.ColorInt
import androidx.core.widget.ImageViewCompat
import com.blankj.utilcode.util.SizeUtils
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.LayoutRecyclerviewFiletreeItemBinding
import com.itsaky.androidide.models.FileExtension
import java.io.File
import kotlin.math.max

class FileTreeViewHolder(context: Context) : TreeNode.BaseNodeViewHolder<File>(context) {

  private var binding: LayoutRecyclerviewFiletreeItemBinding? = null

  @ColorInt private var highlightColor: Int = 0
  @ColorInt private var transparentColor: Int = 0

  init {
    initColors()
  }

  private fun initColors() {
    val typedValue = TypedValue()
    highlightColor =
        if (
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer,
                typedValue,
                true,
            )
        ) {
          typedValue.data
        } else {
          Color.parseColor("#30D0BCFF")
        }
    transparentColor = Color.TRANSPARENT
  }

  override fun createNodeView(node: TreeNode, file: File): View {
    val inflater = LayoutInflater.from(context)
    // Inflate 新的布局
    val binding = LayoutRecyclerviewFiletreeItemBinding.inflate(inflater)
    this.binding = binding

    val dp15 = SizeUtils.dp2px(15f)
    val ext = FileExtension.Factory.forFile(file)

    binding.filetreeName.text = file.name
    binding.filetreeIcon.setImageResource(ext.icon)
    applyIconTint(binding.filetreeIcon, ext)

    val root = applyPadding(node, binding, dp15)

    if (file.isDirectory) {
      binding.filetreeChevron.visibility = View.VISIBLE
      binding.filetreeChevron.rotation = if (node.isExpanded) 90f else 0f
    } else {
      binding.filetreeChevron.visibility = View.INVISIBLE
    }

    if (node.isHighlighted) {
      root.setBackgroundColor(highlightColor)
    } else {
      root.setBackgroundColor(transparentColor)
    }

    return root
  }

  private fun applyIconTint(imageView: ImageView, ext: FileExtension) {
    if (ext.tintAttr != 0) {
      val typedValue = TypedValue()
      if (context.theme.resolveAttribute(ext.tintAttr, typedValue, true)) {
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(typedValue.data))
      } else {
        ImageViewCompat.setImageTintList(imageView, null)
      }
    } else {
      ImageViewCompat.setImageTintList(imageView, null)
    }
  }

  override fun toggle(active: Boolean) {
    binding
        ?.filetreeChevron
        ?.animate()
        ?.rotation(if (active) 90f else 0f)
        ?.setDuration(150)
        ?.start()
  }

  override fun toggleHighlight(highlighted: Boolean) {
    val root = binding?.root ?: return
    root.animate().cancel()

    val startColor = if (highlighted) transparentColor else highlightColor
    val endColor = if (highlighted) highlightColor else transparentColor

    ObjectAnimator.ofObject(root, "backgroundColor", ArgbEvaluator(), startColor, endColor)
        .setDuration(200)
        .start()
  }

  private fun applyPadding(
      node: TreeNode,
      binding: LayoutRecyclerviewFiletreeItemBinding,
      padding: Int,
  ): View {
    val root = binding.root as LinearLayout
    val level = max(0, node.level - 1)
    val leftPadding = padding * level
    root.setPaddingRelative(leftPadding, root.paddingTop, root.paddingRight, root.paddingBottom)
    return root
  }

  fun setLoading(loading: Boolean) {
    val viewIndex = if (loading) 1 else 0
    if (binding?.chevronLoadingSwitcher?.displayedChild != viewIndex) {
      binding?.chevronLoadingSwitcher?.displayedChild = viewIndex
    }
  }

  fun updateChevron(expanded: Boolean) {
    setLoading(false)
    toggle(expanded)
  }
}
