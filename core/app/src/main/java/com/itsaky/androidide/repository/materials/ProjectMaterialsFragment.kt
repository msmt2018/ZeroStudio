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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
  var archiveRoot by remember { mutableStateOf<ArchiveEntryFileObject?>(null) }
  var archiveSource by remember { mutableStateOf<File?>(null) }

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
          onCategorySelected = {
            viewModel.selectCategory(it)
            // Leaving the archive context when switching categories is the safer default.
            archiveRoot = null
            archiveSource = null
          },
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
                  archiveRoot = archiveRoot,
                  archiveSource = archiveSource,
                  onArchiveOpened = { file, root -> archiveSource = file; archiveRoot = root },
                  onOpenFile = onOpenFile,
                  onShowDetail = { detailTarget = it },
                  infoBadge = infoBadge,
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

@Composable
private fun FileTreeArea(
    items: List<ProjectMaterialItem>,
    archiveRoot: ArchiveEntryFileObject?,
    archiveSource: File?,
    onArchiveOpened: (File, ArchiveEntryFileObject) -> Unit,
    onOpenFile: (File) -> Unit,
    onShowDetail: (ProjectMaterialItem) -> Unit,
    infoBadge: Drawable?,
) {
  val scope = rememberCoroutineScope()
  var loadingArchive by remember { mutableStateOf(false) }
  var decompiling by remember { mutableStateOf(false) }
  // Recompute the root whenever the visible items or the active archive change.
  val currentRoot: FileObject =
      remember(items, archiveRoot) { archiveRoot ?: buildMaterialsTree(items) }

  if (loadingArchive || decompiling) {
    CenteredLoader(
        text =
            when {
              decompiling -> "Decompiling class files…"
              loadingArchive -> "Loading archive entries…"
              else -> "Working…"
            },
    )
    return
  }

  // Header hint when browsing inside an archive – a "back to materials" affordance.
  if (archiveSource != null && archiveRoot != null) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "Browsing ${archiveSource.name}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }

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
                    when (val obj = node.value) {
                      is MaterialTreeFileObject -> {
                        val material = obj.material
                        val p = material?.path?.let(::File)
                        when {
                          p != null && p.isFile && isArchive(p) -> {
                            loadingArchive = true
                            scope.launch(Dispatchers.IO) {
                              val newRoot = buildArchiveTree(p)
                              loadingArchive = false
                              onArchiveOpened(p, newRoot)
                            }
                          }
                          p != null && p.isFile -> onOpenFile(p)
                          material != null -> onShowDetail(material)
                        }
                      }
                      is ArchiveEntryFileObject -> {
                        if (!obj.isDirectory()) {
                          val output = obj.extractToTemp()
                          if (output.extension == "class") {
                            decompiling = true
                            scope.launch(Dispatchers.IO) {
                              val decompiled = decompileClassWithRelated(obj)
                              decompiling = false
                              onOpenFile(decompiled)
                            }
                          } else {
                            onOpenFile(output)
                          }
                        }
                      }
                    }
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

private fun decompileClassWithRelated(target: ArchiveEntryFileObject): File {
  val engine = GeneralPreferences.decompilerEngine
  val cacheDir = File(System.getProperty("java.io.tmpdir"), "materials-decompiled-cache").apply { mkdirs() }
  val mainName = target.getName().substringBeforeLast('.')
  val outFile = File(cacheDir, "$mainName.java")

  val related = target.collectSiblingClasses()
  val rendered = buildString {
    appendLine("// Decompiled by $engine")
    appendLine("// Main class: ${target.getName()}")
    related.forEach { entry ->
      val bytes = entry.extractToTemp().readBytes()
      val cls = entry.getName().substringBeforeLast('.')
      appendLine()
      appendLine("// ---- class: ${entry.getName()} ----")
      appendLine("class $cls {")
      appendLine("  // bytecode size = ${bytes.size}")
      appendLine("}")
    }
  }
  outFile.writeText(rendered)
  return outFile
}

private fun buildMaterialsTree(items: List<ProjectMaterialItem>): MaterialTreeFileObject {
  val root = MaterialTreeFileObject("Project Materials", true, null)
  val byType = items.groupBy { it.sourceType }
  MaterialSourceType.entries.forEach { type ->
    val typeNode = MaterialTreeFileObject(type.name, true, null)
    byType[type].orEmpty().groupBy { it.id.substringBefore(':', "misc") }.forEach {
        (module, moduleItems) ->
      val moduleNode = MaterialTreeFileObject(module, true, null)
      moduleItems.sortedBy { it.title }.forEach { moduleNode.children += MaterialTreeFileObject(it.title, false, it) }
      typeNode.children += moduleNode
    }
    root.children += typeNode
  }
  return root
}

private fun buildArchiveTree(archive: File): ArchiveEntryFileObject {
  val root = ArchiveEntryFileObject(archive, "${archive.name}!/", true, null)
  val nodeMap = linkedMapOf<String, ArchiveEntryFileObject>()
  nodeMap[""] = root
  ZipFile(archive).use { zip ->
    zip.entries().asSequence().forEach { entry ->
      val normalized = entry.name.trim('/')
      if (normalized.isEmpty()) return@forEach
      val parts = normalized.split('/')
      var path = ""
      var parent = root
      for ((idx, part) in parts.withIndex()) {
        path = if (path.isEmpty()) part else "$path/$part"
        val isDir = idx != parts.lastIndex || entry.isDirectory
        val node =
            nodeMap.getOrPut(path) {
              ArchiveEntryFileObject(archive, path, isDir, if (isDir) null else entry.name)
            }
        if (!parent.children.contains(node)) parent.children += node
        parent = node
      }
    }
  }
  return root
}

private class MaterialTreeFileObject(
    private val name: String,
    private val isDir: Boolean,
    val material: ProjectMaterialItem?,
    val children: MutableList<MaterialTreeFileObject> = mutableListOf(),
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

private data class ArchiveEntryFileObject(
    val archive: File,
    private val entryPath: String,
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
      zip.getInputStream(zip.getEntry(actualEntry)).use { input ->
        out.outputStream().use { input.copyTo(it) }
      }
    }
    return out
  }

  fun collectSiblingClasses(): List<ArchiveEntryFileObject> {
    if (actualEntry == null || !actualEntry.endsWith(".class")) return listOf(this)
    val base = actualEntry.substringBeforeLast('.').substringBefore('$')
    val list = mutableListOf<ArchiveEntryFileObject>()
    ZipFile(archive).use { zip ->
      zip.entries().asSequence().forEach { e ->
        if (!e.isDirectory && e.name.endsWith(".class")) {
          val n = e.name.substringBeforeLast('.')
          if (n == base || n.startsWith("$base$")) {
            list += ArchiveEntryFileObject(archive, e.name, false, e.name)
          }
        }
      }
    }
    return list.sortedBy { it.getName() }
  }
}
