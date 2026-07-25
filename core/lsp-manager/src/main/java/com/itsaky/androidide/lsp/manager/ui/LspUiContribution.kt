package com.itsaky.androidide.lsp.manager.ui

import android.view.View
import io.github.rosemoe.sora.widget.CodeEditor

/** Pluggable UI extension point for IDEA/Android Studio-style LSP surfaces. */
interface LspUiContribution {
  val id: String
  val title: String
  val placement: LspUiPlacement
  fun createView(context: LspUiContext): View?
}

data class LspUiContext(
  val editor: CodeEditor?,
  val projectRoot: String,
  val languageId: String,
)

enum class LspUiPlacement {
  CompletionPopup,
  HoverPopup,
  SignaturePopup,
  DiagnosticsStripe,
  ProblemsToolWindow,
  StructureToolWindow,
  DocumentationToolWindow,
  SymbolsToolWindow,
  StatusBar,
}
