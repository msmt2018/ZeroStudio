package com.itsaky.androidide.fragments

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import com.smarttoolfactory.colorpicker.dialog.ColorPickerRingDiamondHEXDialog

class ColorQueryDialogFragment : DialogFragment() {
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val query = arguments?.getString(ARG_QUERY)
    val initial = try { Color.parseColor(query ?: "#FFFFFF") } catch (_: Throwable) { Color.WHITE }
    val composeView = ComposeView(requireContext()).apply {
      setContent {
        ColorPickerRingDiamondHEXDialog(
            initialColor = androidx.compose.ui.graphics.Color(initial),
            initialQueryText = query.orEmpty(),
        ) { _, _ -> dismissAllowingStateLoss() }
      }
    }
    return AlertDialog.Builder(requireContext()).setView(composeView).create()
  }

  companion object {
    private const val ARG_QUERY = "arg_query"
    fun newInstance(query: String?) = ColorQueryDialogFragment().apply {
      arguments = Bundle().apply { putString(ARG_QUERY, query) }
    }
  }
}
