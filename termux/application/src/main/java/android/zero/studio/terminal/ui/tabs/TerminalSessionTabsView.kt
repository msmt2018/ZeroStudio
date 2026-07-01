package android.zero.studio.terminal.ui.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import android.zero.studio.terminal.TermuxFragment
import android.zero.studio.terminal.ui.tabs.callback.TabItemTouchHelperCallback
import android.zero.studio.terminal.ui.tabs.utils.GradientBlurHelper
import android.zero.studio.terminal.ui.theme.TerminalThemeManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.termux.R
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession
import java.util.Collections

/**
 * View for displaying terminal tabs with drag-and-drop support.
 *
 * @author android_zero
 */
class TerminalSessionTabsView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), SessionTabManager.SessionChangeListener {

  private val recyclerView: RecyclerView
  private val scrollView: HorizontalScrollView
  private val actionMenuButton: ImageView
  private val actionMenuContainer: FrameLayout
  private val adapter: SessionTabAdapter
  private var sessionManager: SessionTabManager? = null
  private var fragment: TermuxFragment? = null

  private val themeManager = TerminalThemeManager(context)

  init {
    LayoutInflater.from(context).inflate(R.layout.view_terminal_session_tabs, this, true)

    recyclerView = findViewById(R.id.tabs_recycler_view)
    scrollView = findViewById(R.id.tabs_scroll_view)
    actionMenuButton = findViewById(R.id.action_menu_button)
    actionMenuContainer = findViewById(R.id.action_menu_container)

    adapter = SessionTabAdapter()
    recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    recyclerView.adapter = adapter

    setupDragToReorder()
    setupActionMenu()
  }

  fun attachToFragment(fragment: TermuxFragment, manager: SessionTabManager) {
    this.fragment = fragment
    this.sessionManager = manager
    this.sessionManager?.addListener(this)
    adapter.sessionManager = manager
    refreshSessions()
  }

  fun refreshSessions() {
    val sessions = sessionManager?.getAllSessions() ?: emptyList()
    val currentSession = sessionManager?.getCurrentSession()
    adapter.updateSessions(sessions, currentSession)
    scrollToActiveTab()
  }

  private fun setupDragToReorder() {
    val callback =
        TabItemTouchHelperCallback(
            object : TabItemTouchHelperCallback.ItemMoveCallback {
              override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
                // Notify manager to update backend list
                sessionManager?.reorderSessions(fromPosition, toPosition)
                // Notify adapter to update UI list locally to avoid flicker
                adapter.moveItem(fromPosition, toPosition)
                return true
              }

              override fun onItemDragStarted(viewHolder: RecyclerView.ViewHolder) {
                // Optional: visual feedback
              }

              override fun onItemDragEnded(viewHolder: RecyclerView.ViewHolder) {
                // Optional: visual feedback
              }
            }
        )
    val itemTouchHelper = ItemTouchHelper(callback)
    itemTouchHelper.attachToRecyclerView(recyclerView)
  }

  private fun setupActionMenu() {
    // Using container for larger click area if needed
    actionMenuContainer.setOnClickListener { view -> showActionMenu(view) }
  }

  /** Shows the right-side action menu as a PopupMenu (Dropdown) */
  private fun showActionMenu(anchorView: View) {
    val popup = PopupMenu(context, anchorView, Gravity.END)

    // Standard menu items
    popup.menu.add(Menu.NONE, 1, 1, context.getString(R.string.action_new_session))
    popup.menu.add(Menu.NONE, 2, 2, context.getString(R.string.action_change_theme))
    popup.menu.add(Menu.NONE, 3, 3, "Set Working Directory")

    popup.setOnMenuItemClickListener { item ->
      when (item.itemId) {
        1 -> {
          // Create New Session
          sessionManager?.createNewSession(isFailsafe = false)
          true
        }
        2 -> {
          // Change Theme
          showThemeSelectionDialog()
          true
        }
        3 -> {
          showWorkingDirectoryDialog()
          true
        }
        else -> {
          // Fragment.onOptionsItemSelected is deprecated in AndroidX, but
          // no stable MenuHost replacement exists in this code path yet.
          @Suppress("DEPRECATION")
          val handled = fragment?.onOptionsItemSelected(item) ?: false
          handled
        }
      }
    }
    popup.show()
  }

  private fun showThemeSelectionDialog() {
    val themes = themeManager.getAvailableThemes()
    val themeNames = themes.map { it.name }.toTypedArray()

    MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.title_select_theme))
        .setItems(themeNames) { dialog, which ->
          val selectedTheme = themes[which]
          fragment?.let { themeManager.applyTheme(it, selectedTheme) }
        }
        .show()
  }

  private fun showWorkingDirectoryDialog() {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_session, null)
    val inputEdit = dialogView.findViewById<TextInputEditText>(R.id.input_edit)
    val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.input_layout)

    // Pre-fill with current default or session cwd
    inputLayout.hint = "Working Directory"
    inputEdit.setText(sessionManager?.defaultWorkingDirectory ?: "")

    MaterialAlertDialogBuilder(context)
        .setTitle("Set Working Directory")
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
          val newPath = inputEdit.text.toString()
          if (newPath.isNotEmpty()) {
            sessionManager?.updateWorkingDirectory(newPath)
          }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }

  private fun scrollToActiveTab() {
    val activePosition = adapter.getActivePosition()
    if (activePosition != -1) {
      recyclerView.post { recyclerView.smoothScrollToPosition(activePosition) }
    }
  }

  // SessionChangeListener implementation
  override fun onSessionAdded(session: TermuxSession) {
    refreshSessions()
  }

  override fun onSessionRemoved(session: TermuxSession) {
    refreshSessions()
  }

  override fun onSessionChanged(session: TermuxSession) {
    refreshSessions()
  }

  override fun onActiveSessionChanged(session: TerminalSession?) {
    refreshSessions()
  }

  override fun onSessionsReordered() {
    refreshSessions()
  }

  inner class SessionTabAdapter : RecyclerView.Adapter<SessionTabAdapter.TabViewHolder>() {
    private var sessions = mutableListOf<TermuxSession>()
    private var currentSession: TerminalSession? = null
    var sessionManager: SessionTabManager? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateSessions(newSessions: List<TermuxSession>, current: TerminalSession?) {
      sessions.clear()
      sessions.addAll(newSessions)
      currentSession = current
      notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
      if (from < sessions.size && to < sessions.size) {
        Collections.swap(sessions, from, to)
        notifyItemMoved(from, to)
      }
    }

    fun getActivePosition(): Int {
      return sessions.indexOfFirst { it.terminalSession == currentSession }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
      val view =
          LayoutInflater.from(parent.context).inflate(R.layout.item_terminal_tab, parent, false)
      return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
      holder.bind(sessions[position], position)
    }

    override fun getItemCount() = sessions.size

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      private val cardView: MaterialCardView = itemView.findViewById(R.id.tab_card)
      private val labelText: TextView = itemView.findViewById(R.id.tab_label)
      private val closeButton: ImageView = itemView.findViewById(R.id.tab_close_button)
      private val gradientView: View = itemView.findViewById(R.id.tab_gradient_background)

      @SuppressLint("StringFormatMatches")
      fun bind(termuxSession: TermuxSession, position: Int) {
        val session = termuxSession.terminalSession
        val isActive = session == currentSession

        // Calculate display index based on current list order, not session ID
        val displayIndex = position + 1

        val sessionName =
            if (!session.mSessionName.isNullOrEmpty()) {
              session.mSessionName
            } else {
              if (isActive) context.getString(R.string.tab_label_active, displayIndex)
              else context.getString(R.string.tab_label_terminal, displayIndex)
            }

        labelText.text = sessionName

        // Apply Gradient
        GradientBlurHelper.applyGradientToView(gradientView, position)

        // Apply Blur (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          GradientBlurHelper.applyBlurEffect(gradientView, 4f)
        }

        // Visual state
        cardView.strokeColor =
            if (isActive) ContextCompat.getColor(context, R.color.active_tab_stroke) else 0
        cardView.cardElevation = if (isActive) 8f else 2f

        // Click to switch
        cardView.setOnClickListener { sessionManager?.switchToSession(session) }

        // Long click for menu
        cardView.setOnLongClickListener {
          showTabContextMenu(it, termuxSession)
          true
        }

        // Close button click
        closeButton.setOnClickListener { sessionManager?.closeSession(session) }
      }

      /** Context Menu for Tab (Long Press) */
      private fun showTabContextMenu(anchor: View, session: TermuxSession) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
          add(Menu.NONE, 1, 1, R.string.action_close_tab) // Close
          add(Menu.NONE, 2, 2, R.string.action_close_other_sessions) // Close Others
          add(Menu.NONE, 3, 3, R.string.action_close_all_sessions) // Close All
          add(Menu.NONE, 4, 4, R.string.title_rename_session) // Rename

          val infoStr =
              context.getString(
                  R.string.session_info_format,
                  session.executionCommand.mPid,
                  session.terminalSession.pid,
              )
          val info = add(Menu.NONE, 5, 5, infoStr)
          info.isEnabled = false
        }

        popup.setOnMenuItemClickListener { menuItem ->
          when (menuItem.itemId) {
            1 -> sessionManager?.closeSession(session.terminalSession)
            2 -> sessionManager?.closeOtherSessions(session.terminalSession)
            3 -> sessionManager?.closeAllSessions()
            4 -> showRenameDialog(session.terminalSession)
          }
          true
        }
        popup.show()
      }

      private fun showRenameDialog(session: TerminalSession) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_rename_session, null)
        val input = view.findViewById<TextInputEditText>(R.id.input_edit)
        input.setText(session.mSessionName ?: "")

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_rename_session)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
              val name = input.text.toString()
              if (name.isNotEmpty()) sessionManager?.renameSession(session, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
      }
    }
  }
}
