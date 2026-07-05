package android.zero.studio.termux.ui.tabs

import android.content.Context
import android.zero.studio.termux.TermuxFragment
import android.zero.studio.termux.TermuxService
import com.termux.shared.logger.Logger
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession
import java.io.File
import java.util.Collections

/**
 * Manager class for handling terminal session tabs Provides centralized logic for session
 * management
 *
 * @author android_zero
 */
class SessionTabManager(private val context: Context) {

  private var fragment: TermuxFragment? = null
  private var service: TermuxService? = null
  private val listeners = mutableListOf<SessionChangeListener>()

  /**
   * The default working directory for new sessions. This should be set by the Fragment based on the
   * IDE's project path.
   */
  var defaultWorkingDirectory: String? = null

  companion object {
    private const val LOG_TAG = "SessionTabManager"
    private const val MAX_SESSIONS = 8
  }

  interface SessionChangeListener {
    fun onSessionAdded(session: TermuxSession)

    fun onSessionRemoved(session: TermuxSession)

    fun onSessionChanged(session: TermuxSession)

    fun onActiveSessionChanged(session: TerminalSession?)

    fun onSessionsReordered()
  }

  fun attachToFragment(fragment: TermuxFragment) {
    this.fragment = fragment
    this.service = fragment.getTermuxService()
  }

  fun detach() {
    this.fragment = null
    this.service = null
    listeners.clear()
  }

  fun addListener(listener: SessionChangeListener) {
    if (!listeners.contains(listener)) {
      listeners.add(listener)
    }
  }

  fun removeListener(listener: SessionChangeListener) {
    listeners.remove(listener)
  }

  /** Updates the working directory for future sessions. */
  fun updateWorkingDirectory(path: String) {
    val file = File(path)
    if (file.exists() && file.isDirectory) {
      this.defaultWorkingDirectory = path
    }
  }

  fun getAllSessions(): List<TermuxSession> {
    return service?.termuxSessions ?: emptyList()
  }

  /** Gets the currently active session */
  fun getCurrentSession(): TerminalSession? {
    return fragment?.currentSession
  }

  /** Creates a new session Uses [defaultWorkingDirectory] if specific path is not provided. */
  fun createNewSession(
      isFailsafe: Boolean = false,
      sessionName: String? = null,
      workingDirectory: String? = null,
  ): TermuxSession? {
    val svc = service ?: return null // If service is null, we can't create session

    val currentSessionCount = svc.termuxSessionsSize

    if (currentSessionCount >= MAX_SESSIONS) {
      Logger.logWarn(LOG_TAG, "Maximum session limit reached: $MAX_SESSIONS")
      return null
    }

    // Priority: Argument -> Manager Property -> Default Home
    val targetDir = workingDirectory ?: defaultWorkingDirectory

    val newSession =
        svc.createTermuxSession(null, null, null, workingDirectory, isFailsafe, sessionName)

    newSession?.let { session ->
      notifySessionAdded(session)
      switchToSession(session.terminalSession)
    }

    return newSession
  }

  /** Switches to a specific session */
  fun switchToSession(session: TerminalSession?) {
    session?.let {
      fragment?.termuxTerminalSessionClient?.setCurrentSession(it)
      notifyActiveSessionChanged(it)
    }
  }

  /** Closes a session */
  fun closeSession(session: TerminalSession): Boolean {
    val target = service?.getTermuxSessionForTerminalSession(session) ?: return false
    fragment?.termuxTerminalSessionClient?.removeFinishedSession(session) ?: return false
    notifySessionRemoved(target)
    notifyActiveSessionChanged(getCurrentSession())
    return true
  }

  /** Closes all sessions except the specified one */
  fun closeOtherSessions(exceptSession: TerminalSession) {
    val sessionsToClose = getAllSessions().filter { it.terminalSession != exceptSession }

    sessionsToClose.forEach { session ->
      fragment?.termuxTerminalSessionClient?.removeFinishedSession(session.terminalSession)
      notifySessionRemoved(session)
    }

    switchToSession(exceptSession)
  }

  /** Closes all sessions */
  fun closeAllSessions() {
    val allSessions = getAllSessions().toList()

    allSessions.forEach { session ->
      fragment?.termuxTerminalSessionClient?.removeFinishedSession(session.terminalSession)
      notifySessionRemoved(session)
    }

    notifyActiveSessionChanged(null)
  }

  /** Renames a session */
  fun renameSession(session: TerminalSession, newName: String) {
    session.mSessionName = newName

    service?.getTermuxSessionForTerminalSession(session)?.let { termuxSession ->
      termuxSession.executionCommand.shellName = newName
      notifySessionChanged(termuxSession)
    }
  }

  /** Reorders sessions */
  fun reorderSessions(fromPosition: Int, toPosition: Int) {
    val svc = service ?: return
    val sessions = svc.termuxSessions

    if (
        fromPosition < 0 ||
            fromPosition >= sessions.size ||
            toPosition < 0 ||
            toPosition >= sessions.size
    ) {
      return
    }

    // Swap in the backend list
    Collections.swap(sessions, fromPosition, toPosition)
    notifySessionsReordered()
  }

  /** Gets session info for display */
  fun getSessionDisplayInfo(session: TermuxSession, isActive: Boolean): SessionDisplayInfo {
    val terminalSession = session.terminalSession

    return SessionDisplayInfo(
        sessionName = terminalSession.mSessionName ?: "",
        title = terminalSession.title ?: "",
        isRunning = terminalSession.isRunning,
        isActive = isActive,
        pid = terminalSession.pid,
        uid = session.executionCommand.mPid,
    )
  }

  // Notification methods
  private fun notifySessionAdded(session: TermuxSession) {
    listeners.forEach { it.onSessionAdded(session) }
  }

  private fun notifySessionRemoved(session: TermuxSession) {
    listeners.forEach { it.onSessionRemoved(session) }
  }

  private fun notifySessionChanged(session: TermuxSession) {
    listeners.forEach { it.onSessionChanged(session) }
  }

  private fun notifyActiveSessionChanged(session: TerminalSession?) {
    listeners.forEach { it.onActiveSessionChanged(session) }
  }

  private fun notifySessionsReordered() {
    listeners.forEach { it.onSessionsReordered() }
  }

  /** Data class for session display information */
  data class SessionDisplayInfo(
      val sessionName: String,
      val title: String,
      val isRunning: Boolean,
      val isActive: Boolean,
      val pid: Int,
      val uid: Int,
  )
}
