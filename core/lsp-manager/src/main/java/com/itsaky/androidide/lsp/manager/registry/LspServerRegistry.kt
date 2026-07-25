package com.itsaky.androidide.lsp.manager.registry

import com.itsaky.androidide.lsp.manager.server.LspServerDescriptor
import java.util.concurrent.CopyOnWriteArrayList

/** Thread-safe plug-in registry used by language modules and user-installed LSP providers. */
class LspServerRegistry {
  private val descriptors = CopyOnWriteArrayList<LspServerDescriptor>()

  fun register(descriptor: LspServerDescriptor) {
    require(descriptor.id.isNotBlank()) { "Language server id must not be blank." }
    unregister(descriptor.id)
    descriptors += descriptor
  }

  fun unregister(id: String): Boolean = descriptors.removeIf { it.id == id }

  fun all(): List<LspServerDescriptor> = descriptors.sortedWith(compareByDescending<LspServerDescriptor> { it.priority }.thenBy { it.id })

  fun findByLanguageId(languageId: String): List<LspServerDescriptor> = all().filter { languageId in it.languageIds }

  fun findByFileName(fileName: String): List<LspServerDescriptor> = all().filter { descriptor ->
    descriptor.fileExtensions.any { fileName.endsWith(it, ignoreCase = true) }
  }
}
