package com.itsaky.androidide.repository.materials

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileItemTrailingProvider
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.widget.FileTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.BaseFragment
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.projects.materials.MaterialSourceType
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import com.itsaky.androidide.provider.IDEFileIconProvider
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectMaterialsFragment : BaseFragment() {
  private val viewModel: ProjectMaterialsViewModel by viewModels()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View =
      ComposeView(requireContext()).apply {
        setContent { ProjectMaterialsScreen(viewModel = viewModel, onOpenFile = ::openInEditor) }
      }

  override fun onStart() {
    super.onStart()
    viewModel.refresh()
  }

  private fun openInEditor(file: File) {
    // openFile() must run on the main thread because the editor view inflates a layout
    // that uses ValueAnimator (LinearProgressIndicator), which crashes on background
    // threads with "Animators may only be run on Looper threads".
    (activity as? EditorHandlerActivity)?.openFile(file)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectMaterialsScreen(viewModel: ProjectMaterialsViewModel, onOpenFile: (File) -> Unit) {
  val state by viewModel.uiState.collectAsState()
  val detail by viewModel.detailState.collectAsState()
  val context = LocalContext.current
  val infoBadge = remember(context) { ContextCompat.getDrawable(context, R.drawable.ic_info_badge) }

  var detailTarget by remember { mutableStateOf<ProjectMaterialItem?>(null) }
  var detailLoading by remember { mutableStateOf(false) }

  // Decompilation dialog state.
  var decompileTarget by remember { mutableStateOf<ArchiveEntryTarget?>(null) }
  var decompileState by remember { mutableStateOf<ClassDecompileState>(ClassDecompileState.Choosing) }
  var decompileFormat by remember { mutableStateOf<ClassDecompileFormat?>(null) }

  LaunchedEffect(detailTarget) {
    val target = detailTarget
    if (target == null) {
      detailLoading = false
      return@LaunchedEffect
    }
    detailLoading = true
    viewModel.loadDetail(target.path) { detailLoading = false }
  }

  val visibleItems = remember(state.items, state.category) {
    state.items.filter { state.category.matches(it) }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Project Materials") },
            actions = {
              IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )
      },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      CategoryToolbar(
          state = state,
          onCategorySelected = { viewModel.selectCategory(it) },
      )
      Box(modifier = Modifier.fillMaxSize()) {
        when {
          state.loading -> CenteredLoader(text = "Loading materials…")
          visibleItems.isEmpty() ->
              CenteredMessage(
                  title = "No materials in this category",
                  subtitle = "Try another category or refresh after rebuilding the project.",
              )
          else ->
              FileTreeArea(
                  items = visibleItems,
                  onOpenFile = onOpenFile,
                  onShowDetail = { detailTarget = it },
                  infoBadge = infoBadge,
                  onDecompileRequested = { target ->
                    decompileTarget = target
                    decompileState = ClassDecompileState.Choosing
                  },
              )
        }
      }
    }
  }

  detailTarget?.let { target ->
    MaterialDetailDialog(
        material = target,
        detail = detail,
        loading = detailLoading,
        onDismiss = { detailTarget = null },
    )
  }

  decompileTarget?.let { target ->
    ClassDecompileDialog(
        state = decompileState,
        target = ClassEntryTarget(
            archive = target.archive,
            entryName = target.entryName,
            displayName = target.displayName,
        ),
        onChoose = { format ->
          decompileFormat = format
          decompileState = ClassDecompileState.Running(format, "Preparing…")
        },
        onDismiss = {
          decompileTarget = null
          decompileState = ClassDecompileState.Choosing
          decompileFormat = null
        },
        onOpen = { file ->
          decompileTarget = null
          decompileState = ClassDecompileState.Choosing
          decompileFormat = null
          onOpenFile(file)
        },
    )
  }

  // Drive the decompilation work in a coroutine. We react to changes in the chosen
  // format and target, and update the dialog state from the main thread.
  LaunchedEffect(decompileTarget, decompileFormat) {
    val target = decompileTarget
    val format = decompileFormat
    if (target == null || format == null) return@LaunchedEffect
    // Move to the IO dispatcher for the actual decompilation; on completion update
    // the dialog state on the main thread.
    val newState =
        withContext(Dispatchers.IO) {
          runCatching {
                val entry =
                    ClassEntryTarget(
                        archive = target.archive,
                        entryName = target.entryName,
                        displayName = target.displayName,
                    )
                when (format) {
                  ClassDecompileFormat.JAVA -> ClassDecompilerService.decompileToJava(entry)
                  ClassDecompileFormat.SMALI -> ClassDecompilerService.decompileToSmali(entry)
                }
              }
              .fold(
                  onSuccess = { file -> ClassDecompileState.Success(format, file) as ClassDecompileState },
                  onFailure = { t ->
                    ClassDecompileState.Failure(format, t.message ?: t.javaClass.simpleName)
                  },
              )
        }
    decompileState = newState
  }
}

@Composable
private fun CategoryToolbar(
    state: ProjectMaterialsUiState,
    onCategorySelected: (ProjectMaterialCategory) -> Unit,
) {
  val tabs = remember { ProjectMaterialCategory.TAB_CATEGORIES }
  val selectedIndex = tabs.indexOf(state.category).coerceAtLeast(0)

  Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
      tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        CategoryDropdown(
            current = state.category,
            options = ProjectMaterialCategory.entries.toList(),
            onSelected = onCategorySelected,
        )
        Spacer(Modifier.width(12.dp))
        val matching = state.items.count { state.category.matches(it) }
        Text(
            text = "$matching / ${state.items.size} entries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, category ->
          Tab(
              selected = selectedIndex == index,
              onClick = { onCategorySelected(category) },
              text = { Text(category.displayName, maxLines = 1) },
          )
        }
      }
    }
  }
}

@Composable
private fun CategoryDropdown(
    current: ProjectMaterialCategory,
    options: List<ProjectMaterialCategory>,
    onSelected: (ProjectMaterialCategory) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    TextButton(
        onClick = { expanded = true },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Text(
          text = current.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(4.dp))
      Icon(
          imageVector = Icons.Filled.ArrowDropDown,
          contentDescription = "Open category menu",
          tint = MaterialTheme.colorScheme.primary,
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { category ->
        DropdownMenuItem(
            text = {
              Text(
                  text = category.displayName,
                  fontWeight = if (category == current) FontWeight.SemiBold else FontWeight.Normal,
              )
            },
            onClick = {
              expanded = false
              onSelected(category)
            },
        )
      }
    }
  }
}

@Composable
private fun CenteredLoader(text: String) {
  Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.height(12.dp))
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun CenteredMessage(title: String, subtitle: String) {
  Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Lightweight description of a `.class` entry to decompile. */
data class ArchiveEntryTarget(
    val archive: File,
    val entryName: String,
    val displayName: String = entryName.substringAfterLast('/'),
)

@Composable
private fun FileTreeArea(
    items: List<ProjectMaterialItem>,
    onOpenFile: (File) -> Unit,
    onShowDetail: (ProjectMaterialItem) -> Unit,
    infoBadge: Drawable?,
    onDecompileRequested: (ArchiveEntryTarget) -> Unit,
) {
  // Recompute the root whenever the visible items change.
  val currentRoot: FileObject = remember(items) { buildMaterialsTree(items) }

  Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          FileTree(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setAutoExpandSingleChildFolders(GeneralPreferences.treeAutoExpandSingleChild)
            setIconProvider(IDEFileIconProvider(ctx))
            setTrailingProvider(MaterialInfoBadgeProvider(infoBadge) { node ->
              extractMaterial(node)?.let(onShowDetail)
            })
            setOnFileClickListener(
                object : FileClickListener {
                  override fun onClick(node: Node<FileObject>) {
                    handleNodeClick(
                        node = node,
                        onOpenFile = onOpenFile,
                        onShowDetail = onShowDetail,
                        onDecompileRequested = onDecompileRequested,
                    )
                  }
                },
            )
            loadFiles(currentRoot, true)
          }
        },
        update = { tree -> tree.loadFiles(currentRoot, true) },
    )
  }
}

/**
 * Centralised click handler for the file tree. The handler is broken into a top-level
 * function so that long-running work (zip reads, decompilation) can be performed on
 * `Dispatchers.IO` while the subsequent UI/editor interactions are always marshalled
 * back to the main thread.
 */
private fun handleNodeClick(
    node: Node<FileObject>,
    onOpenFile: (File) -> Unit,
    onShowDetail: (ProjectMaterialItem) -> Unit,
    onDecompileRequested: (ArchiveEntryTarget) -> Unit,
) {
  when (val obj = node.value) {
    is ArchiveBackedMaterialFileObject -> {
      // The FileTreeAdapter already toggles expansion for directory nodes. Inline
      // expansion is the desired action for archive files, so we do nothing here and
      // let the row click be handled by the tree itself. Tapping the info badge
      // will still open the material detail dialog.
    }
    is MaterialTreeFileObject -> {
      val material = obj.material
      val p = material?.path?.let(::File)
      when {
        p != null && p.isFile -> onOpenFile(p)
        material != null -> onShowDetail(material)
      }
    }
    is ArchiveEntryFileObject -> {
      if (obj.isFile() && obj.getName().endsWith(".class", ignoreCase = true)) {
        onDecompileRequested(
            ArchiveEntryTarget(
                archive = obj.archive,
                entryName = obj.entryPath,
                displayName = obj.getName(),
            ),
        )
      } else if (obj.isFile()) {
        // Non-class entry: extract to a temp file and open it in the editor.
        val extracted = runCatching { obj.extractToTemp() }.getOrNull()
        if (extracted != null) onOpenFile(extracted)
      }
      // Directories are expanded by the FileTree itself – no work to do here.
    }
  }
}

private fun isArchive(file: File): Boolean =
    file.extension.lowercase() in setOf("jar", "zip", "srcjar", "aar")

private fun extractMaterial(node: Node<FileObject>): ProjectMaterialItem? =
    when (val value = node.value) {
      is MaterialTreeFileObject -> value.material
      is ArchiveEntryFileObject ->
          ProjectMaterialItem(
              id = "archive:${value.absolutePath}",
              title = value.getName(),
              sourceType = MaterialSourceType.PROJECT_FILE,
              apiName = "archive-entry",
              description = "Entry inside ${value.archive.name}",
              path = value.absolutePath,
          )
      else -> null
    }

private class MaterialInfoBadgeProvider(
    private val badge: Drawable?,
    private val onClick: (Node<FileObject>) -> Unit,
) : FileItemTrailingProvider {
  override fun getTrailingDrawable(node: Node<FileObject>): Drawable? {
    val value = node.value
    val showBadge =
        when (value) {
          is MaterialTreeFileObject -> value.material != null && !value.isDirectory()
          is ArchiveEntryFileObject -> value.isFile()
          else -> false
        }
    return if (showBadge) badge else null
  }

  override fun onTrailingClick(node: Node<FileObject>) {
    onClick(node)
  }
}

private fun buildMaterialsTree(items: List<ProjectMaterialItem>): MaterialTreeFileObject {
  val root = MaterialTreeFileObject("Project Materials", true, null)
  val byType = items.groupBy { it.sourceType }
  MaterialSourceType.entries.forEach { type ->
    val typeNode = MaterialTreeFileObject(type.name, true, null)
    byType[type].orEmpty().groupBy { it.id.substringBefore(':', "misc") }.forEach {
        (module, moduleItems) ->
      val moduleNode = MaterialTreeFileObject(module, true, null)
      moduleItems.sortedBy { it.title }.forEach {
        val child = wrapMaterialItem(it)
        moduleNode.children += child
      }
      typeNode.children += moduleNode
    }
    root.children += typeNode
  }
  return root
}

/**
 * Wraps a [ProjectMaterialItem] in a [FileObject] and, if the item is backed by a JAR
 * or ZIP, lazily exposes the archive entries as children of the same node. This lets
 * the user expand a JAR in-place without leaving the materials tree.
 */
private fun wrapMaterialItem(item: ProjectMaterialItem): FileObject {
  val path = item.path?.let(::File)
  if (path != null && path.isFile && isArchive(path)) {
    return ArchiveBackedMaterialFileObject(name = item.title, archive = path, material = item)
  }
  return MaterialTreeFileObject(name = item.title, isDir = false, material = item)
}

private class MaterialTreeFileObject(
    private val name: String,
    private val isDir: Boolean,
    val material: ProjectMaterialItem?,
    val children: MutableList<FileObject> = mutableListOf(),
) : FileObject, Serializable {
  override fun listFiles(): List<FileObject> = children
  override fun isDirectory() = isDir
  override fun isFile() = !isDir
  override fun getName() = name
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = material?.id ?: "virtual://$name"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MaterialTreeFileObject) return false
    return getAbsolutePath() == other.getAbsolutePath() &&
        getName() == other.getName() &&
        isDirectory() == other.isDirectory()
  }

  override fun hashCode(): Int {
    var result = getAbsolutePath().hashCode()
    result = 31 * result + getName().hashCode()
    result = 31 * result + isDirectory().hashCode()
    return result
  }
}

/**
 * Specialised [FileObject] that represents an archive file (JAR / ZIP / AAR / srcjar) but
 * exposes its entries as if they were ordinary children of the archive. The archive
 * itself stays a single node in the materials tree, so it can be expanded inline and
 * the user can also see other files alongside it.
 *
 * The archive is opened lazily the first time [listFiles] is called. Subsequent calls
 * return the same children list, so the FileTree's "remember expanded state" feature
 * still works correctly across re-layouts.
 *
 * The node reports itself as a directory so that the FileTree shows the expansion
 * chevron and treats a row click as expand/collapse. The click handler in
 * [handleNodeClick] still uses the actual on-disk file path of the archive for
 * "open in editor" actions, so users can still double-tap (or use the info badge) to
 * open the raw archive.
 */
private class ArchiveBackedMaterialFileObject(
    private val name: String,
    val archive: File,
    val material: ProjectMaterialItem,
) : FileObject, Serializable {

  @Transient
  private var cached: MutableList<ArchiveEntryFileObject>? = null

  override fun listFiles(): List<FileObject> {
    val list = cached ?: loadEntries().also { cached = it }
    return list
  }

  // The archive is reported as a directory so the FileTree shows the expansion chevron
  // and expands the JAR in-place without leaving the tree. This matches the user's
  // requested UX of "directly expand at the JAR's position instead of entering the JAR".
  override fun isDirectory() = true
  override fun isFile() = false
  override fun getName() = name
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = archive.absolutePath

  private fun loadEntries(): MutableList<ArchiveEntryFileObject> {
    val result = mutableListOf<ArchiveEntryFileObject>()
    runCatching {
          ZipFile(archive).use { zip ->
            // Two passes: first, build a directory tree from entries, then populate
            // intermediate directory nodes. This handles the case where a single
            // archive contains both `META-INF/services/com.foo` and a file at
            // `META-INF/services/` – we want one directory `META-INF/` with the file
            // underneath.
            val byPath = linkedMapOf<String, ArchiveEntryFileObject>()
            val topLevel = linkedMapOf<String, ArchiveEntryFileObject>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
              val entry = entries.nextElement()
              val normalized = entry.name.trim('/')
              if (normalized.isEmpty()) continue
              val parts = normalized.split('/')
              var currentPath = ""
              var parent: ArchiveEntryFileObject? = null
              for ((idx, part) in parts.withIndex()) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                val isLast = idx == parts.lastIndex
                val isDir = !isLast || entry.isDirectory
                val existing = byPath[currentPath]
                val node =
                    when {
                      existing == null ->
                          ArchiveEntryFileObject(archive, currentPath, isDir, if (isDir) null else entry.name)
                      existing.isDirectory() && !isDir -> {
                        // Promote the directory node to a file with the entry's name.
                        ArchiveEntryFileObject(archive, currentPath, isDir, entry.name)
                      }
                      else -> existing
                    }
                byPath[currentPath] = node
                if (parent == null) {
                  topLevel[currentPath] = node
                } else if (!parent.children.contains(node)) {
                  parent.children += node
                }
                parent = node
              }
            }
            result += topLevel.values
          }
        }
        .onFailure {
          // If the archive can't be opened, fall back to an empty list so the user
          // can still see the entry and use the info badge to inspect its metadata.
        }
    // If the archive was empty or unreadable, add a single placeholder so the user
    // still sees something they can click/tap for the detail dialog.
    if (result.isEmpty()) {
      result += ArchiveEntryFileObject(
          archive = archive,
          entryPath = "(empty)",
          dir = true,
          actualEntry = null,
      )
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ArchiveBackedMaterialFileObject) return false
    return archive.absolutePath == other.archive.absolutePath && name == other.name
  }

  override fun hashCode(): Int = 31 * archive.absolutePath.hashCode() + name.hashCode()
}

internal data class ArchiveEntryFileObject(
    val archive: File,
    val entryPath: String,
    private val dir: Boolean,
    private val actualEntry: String?,
    val children: MutableList<ArchiveEntryFileObject> = mutableListOf(),
) : FileObject, Serializable {
  override fun listFiles(): List<FileObject> = children
  override fun isDirectory() = dir
  override fun isFile() = !dir
  override fun getName(): String = entryPath.substringAfterLast('/').ifBlank { archive.name }
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = "${archive.absolutePath}!/$entryPath"

  fun extractToTemp(): File {
    val out = File.createTempFile("material_", "_" + getName())
    ZipFile(archive).use { zip ->
      val entry = actualEntry?.let { zip.getEntry(it) }
          ?: zip.getEntry(entryPath)
          ?: error("Entry $entryPath not found in ${archive.name}")
      zip.getInputStream(entry).use { input ->
        out.outputStream().use { input.copyTo(it) }
      }
    }
    return out
  }
}
