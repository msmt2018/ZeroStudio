package com.itsaky.androidide.lsp.manager.ui

import java.util.concurrent.CopyOnWriteArrayList

/** Registry for replaceable LSP widgets and tool windows. */
class LspUiRegistry {
  private val contributions = CopyOnWriteArrayList<LspUiContribution>()

  fun register(contribution: LspUiContribution) {
    require(contribution.id.isNotBlank()) { "UI contribution id must not be blank." }
    unregister(contribution.id)
    contributions += contribution
  }

  fun unregister(id: String): Boolean = contributions.removeIf { it.id == id }

  fun all(): List<LspUiContribution> = contributions.sortedBy { it.id }

  fun forPlacement(placement: LspUiPlacement): List<LspUiContribution> = all().filter { it.placement == placement }
}
