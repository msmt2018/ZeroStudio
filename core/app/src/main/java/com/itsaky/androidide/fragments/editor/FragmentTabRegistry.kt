package com.itsaky.androidide.fragments.editor

/**
 * Registry for managing fragment tabs in EditorActivityKt.
 *
 * This registry provides a pluggable way to register and manage fragment tabs
 * similar to how EditorToolboxActions manages toolbox entries.
 *
 * Usage:
 * ```
 * // Register a fragment tab entry
 * FragmentTabRegistry.register(
 *   FragmentTabEntry(
 *     id = "markdown_preview",
 *     title = "Markdown Preview",
 *     iconRes = R.drawable.ic_markdown_preview,
 *     fragmentClass = MarkdownPreviewFragment::class.java,
 *     fileExtension = "md",
 *     order = 100
 *   )
 * )
 *
 * // Get a registered entry by id
 * val entry = FragmentTabRegistry.get("markdown_preview")
 *
 * // Get entries by file extension
 * val entries = FragmentTabRegistry.getByFileExtension("md")
 * ```
 *
 * @author ZeroStudio
 */
object FragmentTabRegistry {

  private val mEntries = mutableListOf<FragmentTabEntry>()

  /**
   * All registered fragment tab entries.
   */
  val entries: List<FragmentTabEntry>
    get() = mEntries.toList()

  /**
   * Registers a new fragment tab entry.
   *
   * @param entry The FragmentTabEntry to register
   * @throws IllegalArgumentException if an entry with the same id already exists
   */
  fun register(entry: FragmentTabEntry) {
    if (mEntries.any { it.id == entry.id }) {
      throw IllegalArgumentException("Fragment tab entry with id '${entry.id}' already registered")
    }
    mEntries.add(entry)
    mEntries.sortBy { it.order }
  }

  /**
   * Unregisters a fragment tab entry by its id.
   *
   * @param id The id of the entry to unregister
   * @return true if the entry was found and removed, false otherwise
   */
  fun unregister(id: String): Boolean {
    return mEntries.removeIf { it.id == id }
  }

  /**
   * Gets a fragment tab entry by its id.
   *
   * @param id The id of the entry to retrieve
   * @return The entry if found, null otherwise
   */
  fun get(id: String): FragmentTabEntry? {
    return mEntries.find { it.id == id }
  }

  /**
   * Gets all fragment tab entries that match the given file extension.
   *
   * @param extension The file extension to match (without the dot)
   * @return A list of matching entries, or empty list if none found
   */
  fun getByFileExtension(extension: String): List<FragmentTabEntry> {
    return mEntries.filter { it.matchesExtension(extension) }
  }

  /**
   * Checks if a fragment tab entry with the given id is registered.
   *
   * @param id The id to check
   * @return true if registered, false otherwise
   */
  fun isRegistered(id: String): Boolean {
    return mEntries.any { it.id == id }
  }

  /**
   * Clears all registered entries. Mainly used for testing.
   */
  fun clear() {
    mEntries.clear()
  }
}
