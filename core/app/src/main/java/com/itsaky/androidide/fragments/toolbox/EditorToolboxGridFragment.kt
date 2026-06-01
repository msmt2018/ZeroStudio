package com.itsaky.androidide.fragments.toolbox

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R

class EditorToolboxGridFragment : Fragment() {

  fun interface ToolSelectionListener {
    fun onToolSelected(entry: EditorToolboxEntry)
  }

  private var toolSelectionListener: ToolSelectionListener? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    toolSelectionListener = parentFragment as? ToolSelectionListener
  }

  override fun onDetach() {
    toolSelectionListener = null
    super.onDetach()
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        MaterialTheme {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var entries by remember { mutableStateOf(loadSortedEntries()) }
            ToolboxGrid(
                entries = entries,
                onEntryClick = { entry ->
                  recordUsage(entry)
                  entries = loadSortedEntries()
                  toolSelectionListener?.onToolSelected(entry)
                },
            )
          }
        }
      }
    }
  }

  private fun loadSortedEntries(): List<EditorToolboxEntry> {
    val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return EditorToolboxRegistry.getEntries()
        .sortedWith(
            compareByDescending<EditorToolboxEntry> { prefs.getInt(usageKey(it.id), 0) }
                .thenBy { it.id }
        )
  }

  private fun recordUsage(entry: EditorToolboxEntry) {
    val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val nextCount = prefs.getInt(usageKey(entry.id), 0) + 1
    prefs.edit().putInt(usageKey(entry.id), nextCount).apply()
  }

  @Composable
  private fun ToolboxGrid(entries: List<EditorToolboxEntry>, onEntryClick: (EditorToolboxEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
      Text(
          text = stringResource(R.string.title_editor_toolbox),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          text = stringResource(R.string.desc_editor_toolbox),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
      )
      Spacer(modifier = Modifier.height(12.dp))
      LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(items = entries, key = { it.id }) { entry ->
          ToolboxTile(entry = entry, onClick = { onEntryClick(entry) })
        }
      }
    }
  }

  @Composable
  private fun ToolboxTile(entry: EditorToolboxEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(118.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 8.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
              painter = painterResource(entry.iconRes),
              contentDescription = stringResource(entry.titleRes),
              modifier = Modifier.size(34.dp),
              tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
              text = stringResource(entry.titleRes),
              style = MaterialTheme.typography.labelLarge,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  companion object {
    private const val PREFS_NAME = "editor_toolbox_usage"
    private fun usageKey(id: String) = "usage_$id"
  }
}
