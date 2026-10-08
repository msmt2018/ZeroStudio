package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.View
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentNonEditableEditorBinding
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.fragments.EmptyStateFragment
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.utils.jetbrainsMono
import io.github.rosemoe.sora.lang.EmptyLanguage

abstract class NonEditableEditorFragment :
    EmptyStateFragment<FragmentNonEditableEditorBinding>(
        R.layout.fragment_non_editable_editor,
        FragmentNonEditableEditorBinding::bind,
    ),
    ShareableOutputFragment {

  val editor: IDEEditor?
    get() = _binding?.editor

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emptyMessage = createEmptyStateMessage()

    binding.editor.apply {
      isEditable = false
      // FIX #1: Provide a Float literal (0f) instead of an Int (0).
      dividerWidth = 0f
      setEditorLanguage(EmptyLanguage())
      isWordwrap = false
      isUndoEnabled = false
      setTypefaceLineNumber(jetbrainsMono())
      setTypefaceText(jetbrainsMono())

      // FIX #2: Provide a Float literal (12f) as the argument.
      setTextSize(12f)

      colorScheme = SchemeAndroidIDE.newInstance(requireContext())
    }
  }

  private fun createEmptyStateMessage(): CharSequence? {
    return null
  }

  override fun getContent(): String {
    return editor?.text?.toString() ?: ""
  }

  override fun getFilename(): String = "build_output"

  override fun clearOutput() {
    editor?.let { editor ->
      // FIX: Using text.delete(0, text.length) can trigger an OutOfMemoryError
      // when the editor's content contains a single very long line (~140MB+).
      // sora editor's Content.delete() -> Content.deleteInternal() -> ContentLine.appendTo()
      // tries to allocate a StringBuilder for the entire line being removed.
      // Calling setText("") replaces the whole Content with a new empty one, which
      // is allocation-safe regardless of how much data was previously in the editor.
      runCatching { editor.setText("") }.onFailure { error ->
        // Last-resort safety net: as a true root-cause fix, if even setText fails
        // (e.g. extremely low memory during the clear call), surface the error
        // to the logger instead of letting it crash the whole app.
        android.util.Log.e(
          "NonEditableEditorFragment",
          "Failed to clear build output editor", error
        )
      }
      isEmpty = true
    }
  }
}
