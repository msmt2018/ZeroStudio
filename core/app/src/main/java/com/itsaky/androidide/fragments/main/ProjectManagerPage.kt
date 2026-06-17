package com.itsaky.androidide.fragments.main

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Xml
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.GradleFileParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.w3c.dom.Element

private data class ProjectTab(val title: String, val filePath: String? = null, val treeUri: Uri? = null) {
  fun stableKey(): String = filePath ?: treeUri.toString()
  fun rootPathOrNull(): String? = filePath ?: treeUri?.let { uriToPath(it) }
}
private data class ClipboardProject(val sourcePath: String)
private data class ProjectDisplayInfo(val label: String, val iconFile: File?, val subtitle: String)
private data class RestoredProjectManagerState(val tabs: List<ProjectTab>, val selectedIndex: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectManagerPage(onOpenProject: (String) -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val restoredState = remember(context) { restoreProjectManagerState(context) }
  val tabState = remember(context) { mutableStateListOf<ProjectTab>().apply { addAll(restoredState.tabs) } }
  val tabProjectsState = remember { mutableStateMapOf<String, List<String>>() }
  val loadingState = remember { mutableStateMapOf<String, Boolean>() }
  var clipboardState by remember { mutableStateOf<ClipboardProject?>(null) }
  var selectedTabIndexState by rememberSaveable(context) { mutableIntStateOf(restoredState.selectedIndex) }
  var menuProjectPath by remember { mutableStateOf<String?>(null) }

  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    val flags = IntentFlags
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    val tabName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
    val filePath = uriToPath(uri)
    val tab = ProjectTab(tabName, filePath, uri)
    if (tabState.none { it.stableKey() == tab.stableKey() }) {
      tabState.add(tab)
      selectedTabIndexState = tabState.lastIndex
      persistTabs(context, tabState, selectedTabIndexState)
    }
  }

  // 把 safeSelected 改成 derivedStateOf, 让它能正确跟踪 tabState.size 和
  // selectedTabIndexState 的变化, 避免 ScrollableTabRow 内部用旧 size 算
  // tabPositions 时拿到越界的 selectedTabIndex 触发 IndexOutOfBoundsException。
  val safeSelected by remember(tabState.size, selectedTabIndexState) {
    derivedStateOf {
      if (tabState.isEmpty()) 0
      else selectedTabIndexState.coerceIn(0, tabState.lastIndex)
    }
  }
  if (safeSelected != selectedTabIndexState) selectedTabIndexState = safeSelected
  val selectedTab = tabState.getOrNull(safeSelected)

  LaunchedEffect(selectedTab?.stableKey()) {
    val tab = selectedTab ?: return@LaunchedEffect
    val key = tab.stableKey()
    loadingState[key] = true
    tabProjectsState[key] = withContext(Dispatchers.IO) { scanTopLevelProjects(tab).distinct().sortedBy { File(it).name.lowercase() } }
    loadingState[key] = false
  }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(modifier = Modifier.fillMaxWidth()) {
      if (tabState.isNotEmpty() && safeSelected in tabState.indices) {
        // key(tabState.size) 强制 ScrollableTabRow 在 tab 数量变化时整体重组,
        // 避免 Material3 内部缓存的 tabIndicators 跟外部传入的 selectedTabIndex 不一致。
        key(tabState.size) {
        ScrollableTabRow(selectedTabIndex = safeSelected, modifier = Modifier.fillMaxWidth().padding(end = 30.dp)) {
          tabState.forEachIndexed { index, tab ->
            Tab(
              selected = safeSelected == index,
              onClick = {
                selectedTabIndexState = index
                persistTabs(context, tabState, selectedTabIndexState)
              },
              text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
          }
        }
        }
      }
      FloatingActionButton(
        onClick = { folderPicker.launch(null) },
        modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
        shape = RoundedCornerShape(6.dp),
        elevation = FloatingActionButtonDefaults.elevation(6.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = "add")
      }
    }

    val tab = selectedTab
    if (tab == null) {
      Text("Path not exists")
      return@Column
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      if (clipboardState != null) {
        TextButton(onClick = {
          val src = File(clipboardState!!.sourcePath)
          val dst = File(tab.rootPathOrNull(), src.name)
          if (src.exists() && !dst.exists()) src.renameTo(dst)
          clipboardState = null
        }) {
          Icon(Icons.Default.ContentPaste, null)
          Text("Paste", modifier = Modifier.padding(start = 6.dp))
        }
      }
    }

    val key = tab.stableKey()
    val projects = tabProjectsState[key].orEmpty()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(projects, key = { it }) { projectPath ->
        val info = remember(projectPath) { parseProjectDisplayInfo(File(projectPath)) }
        Box {
          Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenProject(projectPath) }, onLongClick = { menuProjectPath = projectPath }),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              ProjectIconPreview(info.iconFile)
              Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(info.label, style = MaterialTheme.typography.titleMedium)
                Text(info.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          DropdownMenu(expanded = menuProjectPath == projectPath, onDismissRequest = { menuProjectPath = null }) {
            DropdownMenuItem(text = { Text("Cut") }, onClick = { clipboardState = ClipboardProject(projectPath); menuProjectPath = null })
          }
        }
      }
    }
  }
}

private val IntentFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

private fun scanTopLevelProjects(tab: ProjectTab): List<String> =
  tab.filePath?.let { root -> File(root).listFiles { f -> f.isDirectory }?.map { it.absolutePath }.orEmpty() }
    ?: tab.treeUri?.let { uri -> DocumentFile.fromTreeUri(com.itsaky.androidide.app.BaseApplication.getBaseInstance(), uri)?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.uri?.let(::uriToPath) } }
    ?: emptyList()

private fun persistTabs(context: Context, tabs: List<ProjectTab>, selected: Int) {
  val arr = JSONArray()
  tabs.forEach {
    val item = JSONArray(); item.put(it.title); item.put(it.filePath); item.put(it.treeUri?.toString()); arr.put(item)
  }
  context.getSharedPreferences("project_manager_tabs", Context.MODE_PRIVATE).edit().putString("tabs", arr.toString()).putInt("selected", selected).apply()
}

private fun restoreProjectManagerState(context: Context): RestoredProjectManagerState {
  val prefs = context.getSharedPreferences("project_manager_tabs", Context.MODE_PRIVATE)
  val restoredTabs = mutableListOf<ProjectTab>()
  val raw = prefs.getString("tabs", null)
  if (!raw.isNullOrBlank()) {
    runCatching {
      val arr = JSONArray(raw)
      for (i in 0 until arr.length()) {
        val item = arr.getJSONArray(i)
        val title = item.optString(0)
        val filePath = item.optString(1).ifBlank { null }?.takeUnless { it == "null" }
        val treeUri = item.optString(2).ifBlank { null }?.takeUnless { it == "null" }?.let(Uri::parse)
        if (title.isNotBlank() && (filePath != null || treeUri != null)) {
          restoredTabs.add(ProjectTab(title, filePath, treeUri))
        }
      }
    }
  }

  val tabs = restoredTabs.distinctBy { it.stableKey() }.ifEmpty { listOf(defaultProjectTab()) }
  return RestoredProjectManagerState(
    tabs = tabs,
    selectedIndex = prefs.getInt("selected", 0).coerceIn(0, tabs.lastIndex),
  )
}

private fun defaultProjectTab(): ProjectTab =
  ProjectTab(Environment.PROJECTS_FOLDER, Environment.getProjectsDir().absolutePath)

private fun parseProjectDisplayInfo(projectDir: File): ProjectDisplayInfo {
  val appModule = findApplicationModule(projectDir)
  if (appModule == null) return ProjectDisplayInfo(projectDir.name, null, "")
  val manifest = File(appModule, "src/main/AndroidManifest.xml")
  val mxml = if (manifest.exists()) manifest.readText() else ""
  val iconRef = Regex("android:icon=\"([^\"]+)\"").find(mxml)?.groupValues?.getOrNull(1)
  val labelRef = Regex("android:label=\"([^\"]+)\"").find(mxml)?.groupValues?.getOrNull(1)
  val resDir = File(appModule, "src/main/res")
  val iconFile = iconRef?.let { resolveDrawableResourceFile(resDir, it) }
  val label = resolveLabel(projectDir.name, resDir, labelRef)
  val gradleInfo = GradleFileParser.parseModuleBuildGradle(appModule)
  val gradleText = listOf(File(appModule, "build.gradle.kts"), File(appModule, "build.gradle")).firstOrNull { it.exists() }?.readText().orEmpty()
  val versionName = gradleInfo?.versionName ?: "?"
  val versionCode = gradleInfo?.versionCode?.toString() ?: "?"
  val targetSdk = gradleInfo?.targetSdk?.toString() ?: "?"
  val minSdk = gradleInfo?.minSdk?.toString() ?: "?"
  val namespace = Regex("namespace\\s*[= ]\\s*\"([^\"]+)\"").find(gradleText)?.groupValues?.getOrNull(1)
  val appId = Regex("applicationId\\s*[= ]\\s*\"([^\"]+)\"").find(gradleText)?.groupValues?.getOrNull(1)
  val pkg = namespace ?: appId ?: "?"
  val subtitle = "versionName:${versionName},versionCode:${versionCode},targetSdk:${targetSdk},minSdk:${minSdk},package:${pkg}"
  return ProjectDisplayInfo("${projectDir.name} : $label", iconFile, subtitle)
}

private fun findApplicationModule(projectDir: File): File? =
  projectDir.listFiles { f -> f.isDirectory }?.firstOrNull { module ->
    val text = listOf(File(module, "build.gradle.kts"), File(module, "build.gradle")).firstOrNull { it.exists() }?.readText().orEmpty()
    text.contains("com.android.application")
  }

private fun resolveDrawableResourceFile(resDir: File, value: String): File? {
  if (!value.startsWith("@")) return null
  val parts = value.removePrefix("@").split("/")
  if (parts.size != 2) return null
  val type = parts[0]
  val name = parts[1]
  val dirs = resDir.listFiles { f -> f.isDirectory && (f.name == type || f.name.startsWith("$type-")) }?.toList().orEmpty()
  val exact = dirs.flatMap { it.listFiles()?.toList().orEmpty() }.firstOrNull { f -> f.nameWithoutExtension == name }
  if (exact == null || exact.extension.lowercase() != "xml") return exact
  return resolveAdaptiveOrLayeredIcon(resDir, exact) ?: exact
}

private fun resolveAdaptiveOrLayeredIcon(resDir: File, xmlIconFile: File): File? {
  val doc = runCatching { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlIconFile) }.getOrNull() ?: return null
  val root = doc.documentElement ?: return null
  if (root.tagName != "adaptive-icon" && root.tagName != "layer-list") return null
  val refs = mutableListOf<String>()
  val nodeList = root.childNodes
  for (i in 0 until nodeList.length) {
    val node = nodeList.item(i)
    if (node is Element) {
      listOf(node.getAttribute("android:drawable"), node.getAttribute("android:foreground"), node.getAttribute("android:background")).filter { it.startsWith("@") }.forEach(refs::add)
    }
  }
  return refs.asSequence().mapNotNull { resolveDrawableResourceFile(resDir, it) }.firstOrNull()
}

private fun resolveLabel(defaultLabel: String, resDir: File, labelRef: String?): String {
  if (labelRef.isNullOrBlank()) return defaultLabel
  if (!labelRef.startsWith("@string/")) return labelRef
  val key = labelRef.removePrefix("@string/")
  val valuesXml = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }?.map { File(it, "strings.xml") }?.firstOrNull { it.exists() } ?: return defaultLabel
  val doc = runCatching { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(valuesXml) }.getOrNull() ?: return defaultLabel
  val nodes = doc.getElementsByTagName("string")
  for (i in 0 until nodes.length) {
    val node = nodes.item(i)
    if (node.attributes?.getNamedItem("name")?.nodeValue == key) return node.textContent ?: defaultLabel
  }
  return defaultLabel
}

@Composable
private fun ProjectIconPreview(iconFile: File?) {
  if (iconFile == null || !iconFile.exists()) {
    Icon(Icons.Default.Folder, null)
  } else if (iconFile.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")) {
    val bmp = android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
    if (bmp != null) androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
    else Icon(Icons.Default.Folder, null)
  } else {
    AndroidView(factory = { context -> AppCompatImageView(context) }, update = { imageView ->
      val drawable = runCatching {
        val parser = Xml.newPullParser().apply { setInput(iconFile.inputStream(), "utf-8") }
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG && parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) parser.next()
        android.graphics.drawable.Drawable.createFromXml(imageView.resources, parser)
      }.getOrNull()
      imageView.setImageDrawable(drawable ?: AppCompatResources.getDrawable(imageView.context, R.drawable.ic_android))
    }, modifier = Modifier.size(40.dp))
  }
}

private fun uriToPath(uri: Uri): String? {
  val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
  val parts = docId.split(':', limit = 2)
  if (parts.size < 2) return null
  val volume = parts[0]
  val relPath = parts[1]
  return if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0/$relPath" else "/storage/$volume/$relPath"
}
