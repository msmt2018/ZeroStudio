/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.editor.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.SizeUtils
import com.itsaky.androidide.editor.R.string
import com.itsaky.androidide.editor.adapters.CompletionListAdapter
import com.itsaky.androidide.editor.api.IEditor
import com.itsaky.androidide.editor.api.ILspEditor
import com.itsaky.androidide.editor.language.IDELanguage
import com.itsaky.androidide.editor.language.cpp.CppLanguage
import com.itsaky.androidide.editor.language.groovy.GroovyLanguage
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguageProvider
import com.itsaky.androidide.editor.schemes.IDEColorScheme
import com.itsaky.androidide.editor.schemes.IDEColorSchemeProvider
import com.itsaky.androidide.editor.snippets.AbstractSnippetVariableResolver
import com.itsaky.androidide.editor.snippets.FileVariableResolver
import com.itsaky.androidide.editor.snippets.WorkspaceVariableResolver
import com.itsaky.androidide.eventbus.events.editor.ChangeType
import com.itsaky.androidide.eventbus.events.editor.ColorSchemeInvalidatedEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentCloseEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentOpenEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSelectedEvent
import com.itsaky.androidide.flashbar.Flashbar
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.java.utils.CancelChecker
import com.itsaky.androidide.lsp.models.Command
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.ShowDocumentParams
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.progress.ICancelChecker
import com.itsaky.androidide.syntax.colorschemes.DynamicColorScheme
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.tasks.JobCancelChecker
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.tasks.launchAsyncWithProgress
import com.itsaky.androidide.utils.DocumentUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.UndoManager
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.IDEEditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorBuiltinComponent
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.CancellationException

/**
 * [CodeEditor] implementation for the IDE.
 *
 * @author Akash Yadav
 * @author android_zero
 */
open class IDEEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    private val editorFeatures: EditorFeatures = EditorFeatures()
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes), IEditor by editorFeatures, ILspEditor {

    /**
     * A callback interface to notify the host (usually an Activity) about file modification status changes.
     */
    interface ModificationCallback {
        fun onFileModified(file: File?)
        fun onFileSaved(file: File?)
    }

    @Suppress("PropertyName")
    internal var _file: File? = null

    private var _actionsMenu: EditorActionsMenu? = null
    private var _signatureHelpWindow: SignatureHelpWindow? = null
    private var _diagnosticWindow: DiagnosticWindow? = null
    private var fileVersion = 0

    private var mLastSaveHistoryIndex: Int = 0
    private var mUndoManagerField: Field? = null

    private val selectionChangeHandler = Handler(Looper.getMainLooper())
    private var selectionChangeRunner: Runnable? = Runnable {
        // Safe check for language client before accessing
        val client = languageClient
        if (client == null || isReleased) return@Runnable

        val cursor = this.cursor ?: return@Runnable

        if (cursor.isSelected || _signatureHelpWindow?.isShowing == true) {
            return@Runnable
        }

        // Get diagnostics using the safe reference
        val diagnostic = client.getDiagnosticAt(file, cursor.leftLine, cursor.leftColumn)
        diagnosticWindow.showDiagnostic(diagnostic)
    }

    /**
     * The [CoroutineScope] for the editor.
     * All the jobs in this scope are cancelled when the editor is released.
     */
    val editorScope = CoroutineScope(Dispatchers.Default + CoroutineName("IDEEditor"))

    protected val eventDispatcher = EditorEventDispatcher()

    private var setupTsLanguageJob: Job? = null
    private var sigHelpCancelChecker: ICancelChecker? = null

    var languageServer: ILanguageServer? = null
        private set

    var languageClient: ILanguageClient? = null
        private set

    /**
     * Whether the cursor position change animation is enabled for the editor.
     */
    var isEnsurePosAnimEnabled = true

    /**
     * The text searcher for the editor.
     */
    lateinit var searcher: IDEEditorSearcher

    /**
     * The signature help window for the editor.
     */
    val signatureHelpWindow: SignatureHelpWindow
        get() {
            return _signatureHelpWindow ?: SignatureHelpWindow(this).also { _signatureHelpWindow = it }
        }

    /**
     * The diagnostic window for the editor.
     */
    val diagnosticWindow: DiagnosticWindow
        get() {
            return _diagnosticWindow ?: DiagnosticWindow(this).also { _diagnosticWindow = it }
        }

    companion object {

        private const val SELECTION_CHANGE_DELAY = 500L

        internal val log = LoggerFactory.getLogger(IDEEditor::class.java)

        /**
         * Create input type flags for the editor.
         */
        fun createInputTypeFlags(): Int {
            var flags = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if (EditorPreferences.visiblePasswordFlag) {
                flags = flags or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            return flags
        }
    }

    init {
        run {
            editorFeatures.editor = this
            eventDispatcher.editor = this
            eventDispatcher.init(editorScope)
            initEditor()
        }
    }

    /**
     * Set the file for this editor.
     */
    fun setFile(file: File?) {
        if (isReleased) {
            return
        }

        this._file = file
        file?.also {
            dispatchDocumentOpenEvent()
        }
    }

    override fun setLanguageServer(server: ILanguageServer?) {
        if (isReleased) {
            return
        }
        this.languageServer = server
        server?.also {
            this.languageClient = it.client
            snippetController.apply {
                fileVariableResolver = FileVariableResolver(this@IDEEditor)
                workspaceVariableResolver = WorkspaceVariableResolver()
            }
        }
    }


    override fun setLanguageClient(client: ILanguageClient?) {
        if (isReleased) {
            return
        }
        this.languageClient = client
    }

    override fun executeCommand(command: Command?) {
        if (isReleased) {
            return
        }
        if (command == null) {
            log.warn("Cannot execute command in editor. Command is null.")
            return
        }

        log.info(String.format("Executing command '%s' for completion item.", command.title))
        when (command.command) {
            Command.TRIGGER_COMPLETION -> {
                val completion = getComponent(EditorAutoCompletion::class.java)
                completion.requireCompletion()
            }

            Command.TRIGGER_PARAMETER_HINTS -> signatureHelp()
            Command.FORMAT_CODE -> formatCodeAsync()
        }
    }

    override fun signatureHelp() {
        if (isReleased) {
            return
        }
        val languageServer = this.languageServer ?: return
        val file = this.file ?: return

        // Check if client is available
        this.languageClient ?: return

        sigHelpCancelChecker?.also { it.cancel() }

        val cancelChecker = JobCancelChecker().also {
            this.sigHelpCancelChecker = it
        }

        editorScope.launch(Dispatchers.Default) {
            cancelChecker.job = coroutineContext[Job]

            // [FIXED] Removed the `content = text` parameter which caused the compilation error.
            // SignatureHelpParams usually expects (path, position, cancelChecker).
            val help = safeGet("signature help request") {
                val params = SignatureHelpParams(
                    file = file.toPath(),
                    position = cursorLSPPosition,
                    cancelChecker = cancelChecker,
                )
                languageServer.signatureHelp(params)
            }

            withContext(Dispatchers.Main) { showSignatureHelp(help) }
        }.logError("signature help request")
    }

    override fun showSignatureHelp(help: SignatureHelp?) {
        if (isReleased) {
            return
        }
        signatureHelpWindow.setupAndDisplay(help)
    }

    override fun findDefinition() {
        if (isReleased) {
            return
        }
        val languageServer = this.languageServer ?: return
        val file = file ?: return

        launchCancellableAsyncWithProgress(string.msg_finding_definition) { _, cancelChecker ->
            val result = safeGet("definition request") {
                val params = DefinitionParams(file.toPath(), cursorLSPPosition, cancelChecker)
                languageServer.findDefinition(params)
            }

            onFindDefinitionResult(result)
        }?.logError("definition request")
    }

    override fun findReferences() {
        if (isReleased) {
            return
        }
        val languageServer = this.languageServer ?: return
        val file = file ?: return

        launchCancellableAsyncWithProgress(string.msg_finding_references) { _, cancelChecker ->
            val result = safeGet("references request") {
                val params = ReferenceParams(file.toPath(), cursorLSPPosition, true, cancelChecker)
                languageServer.findReferences(params)
            }

            onFindReferencesResult(result)
        }?.logError("references request")
    }

    override fun expandSelection() {
        if (isReleased) {
            return
        }
        val languageServer = this.languageServer ?: return
        val file = file ?: return

        launchCancellableAsyncWithProgress(string.please_wait) { _, _ ->
            val initialRange = cursorLSPRange
            val result = safeGet("expand selection request") {
                val params = ExpandSelectionParams(file.toPath(), initialRange)
                languageServer.expandSelection(params)
            } ?: initialRange

            withContext(Dispatchers.Main) {
                setSelection(result)
            }
        }?.logError("expand selection request")
    }

    override fun ensureWindowsDismissed() {
        if (_diagnosticWindow?.isShowing == true) {
            _diagnosticWindow?.dismiss()
        }

        if (_signatureHelpWindow?.isShowing == true) {
            _signatureHelpWindow?.dismiss()
        }

        if (_actionsMenu?.isShowing == true) {
            _actionsMenu?.dismiss()
        }
    }

    // not overridable!
    final override fun <T : EditorBuiltinComponent?> replaceComponent(clazz: Class<T>, replacement: T & Any) {
        super.replaceComponent(clazz, replacement)
    }

    // not overridable
    final override fun <T : EditorBuiltinComponent?> getComponent(clazz: Class<T>): T & Any {
        return super.getComponent(clazz)
    }

    override fun release() {
        ensureWindowsDismissed()

        if (isReleased) {
            return
        }

        super.release()

        snippetController.apply {
            (fileVariableResolver as? AbstractSnippetVariableResolver?)?.close()
            (workspaceVariableResolver as? AbstractSnippetVariableResolver?)?.close()

            fileVariableResolver = null
            workspaceVariableResolver = null
        }

        _actionsMenu?.destroy()

        _actionsMenu = null
        _signatureHelpWindow = null
        _diagnosticWindow = null

        languageServer = null
        languageClient = null

        _file = null
        fileVersion = 0
        markUnmodified()

        editorFeatures.editor = null
        eventDispatcher.editor = null

        eventDispatcher.destroy()

        selectionChangeRunner?.also { selectionChangeHandler.removeCallbacks(it) }
        selectionChangeRunner = null

        setupTsLanguageJob?.cancel(CancellationException("Editor is releasing resources."))

        if (editorScope.isActive) {
            editorScope.cancelIfActive("Editor is releasing resources.")
        }

        try {
            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this)
            }
        } catch (e: Exception) {
            log.debug("EventBus unregister failed", e)
        }
    }

    override fun getSearcher(): EditorSearcher {
        return this.searcher
    }

    override fun getExtraArguments(): Bundle {
        return super.getExtraArguments().apply {
            putString(IEditor.KEY_FILE, file?.absolutePath)
        }
    }

    override fun ensurePositionVisible(line: Int, column: Int, noAnimation: Boolean) {
        super.ensurePositionVisible(line, column, !isEnsurePosAnimEnabled || noAnimation)
    }

    override fun getTabWidth(): Int {
        return EditorPreferences.tabSize
    }

    override fun beginSearchMode() {
        throw UnsupportedOperationException("Search ActionMode is not supported. Use CodeEditorView.beginSearch() instead.")
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            ensureWindowsDismissed()
        }
    }

    /**
     * Analyze the opened file and publish the diagnostics result.
     */
    open fun analyze() {
        if (isReleased) {
            return
        }
        if (editorLanguage !is IDELanguage) {
            return
        }

        val languageServer = languageServer ?: return
        val file = file ?: return

        editorScope.launch {
            val result = safeGet("LSP file analysis") { languageServer.analyze(file.toPath()) }
            languageClient?.publishDiagnostics(result)
        }.logError("LSP file analysis")
    }

    fun markUnmodified() {
        if (isReleased || text == null) {
            return
        }
        mLastSaveHistoryIndex = getHistoryIndex(text.undoManager)
        (context as? ModificationCallback)?.onFileSaved(this.file)
    }

    fun markModified() {
        (context as? ModificationCallback)?.onFileModified(this.file)
    }

    override fun isModified(): Boolean {
        return if (isReleased || text == null) {
            false
        } else {
            getHistoryIndex(text.undoManager) != mLastSaveHistoryIndex
        }
    }

    /**
     * Notify the language server that the file in this editor is about to be closed.
     */
    open fun notifyClose() {
        if (isReleased) {
            return
        }
        file ?: run {
            log.info("Cannot notify language server. File is null.")
            return
        }

        dispatchDocumentCloseEvent()

        _actionsMenu?.unsubscribeEvents()
        selectionChangeRunner?.also {
            selectionChangeHandler.removeCallbacks(it)
        }

        selectionChangeRunner = null

        ensureWindowsDismissed()
    }

    /**
     * Called when this editor is selected and visible to the user.
     */
    open fun onEditorSelected() {
        if (isReleased) {
            return
        }

        file ?: return
        dispatchDocumentSelectedEvent()
    }

    /**
     * Dispatches the [DocumentSaveEvent] for this editor.
     */
    open fun dispatchDocumentSaveEvent() {
        markUnmodified()
        if (isReleased) {
            return
        }
        if (file == null) {
            return
        }
        eventDispatcher.dispatch(DocumentSaveEvent(file!!.toPath()))
    }

    /**
     * Called when the color scheme has been invalidated. This usually happens when the user reloads
     * the color schemes.
     */
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    open fun onColorSchemeInvalidated(event: ColorSchemeInvalidatedEvent?) {
        val file = file ?: return
        setupLanguage(file)
    }

    /**
     * Setup the editor language for the given [file].
     * This applies a proper [Language] and the color scheme to the editor.
     */
    open fun setupLanguage(file: File?) {
        if (isReleased || file == null) {
            return
        }

        createLanguage(file) { language ->
            val extension = file.extension
            if (language is TreeSitterLanguage) {
                IDEColorSchemeProvider.readSchemeAsync(context = context, coroutineScope = editorScope, type = extension) { scheme ->
                    applyTreeSitterLang(language, extension, scheme)
                }
            } else {
                setEditorLanguage(language)
            }
        }
    }

    /**
     * Applies the given [TreeSitterLanguage] and the [color scheme][scheme] for the given [file type][type].
     */
    open fun applyTreeSitterLang(language: TreeSitterLanguage, type: String, scheme: SchemeAndroidIDE?) {
        applyTreeSitterLangInternal(language, type, scheme)
    }

    private fun applyTreeSitterLangInternal(language: TreeSitterLanguage, type: String, scheme: SchemeAndroidIDE?) {
        if (isReleased) {
            return
        }
        var finalScheme = scheme
        if (finalScheme == null) {
            log.error("Failed to read current color scheme, falling back to default.")
            finalScheme = SchemeAndroidIDE.newInstance(context)
        }

        if (finalScheme is IDEColorScheme) {
            language.setupWith(finalScheme)

            if (finalScheme.getLanguageScheme(type) == null) {
                log.warn("Color scheme does not support file type '{}'", type)
                // If specific language scheme not found, use default but keep the language instance
                // We might want to fallback to a basic syntax highlighter if needed
            }
        }

        if (finalScheme is DynamicColorScheme) {
            finalScheme.apply(context)
        }

        colorScheme = finalScheme!!
        setEditorLanguage(language)
    }

    private inline fun createLanguage(file: File, crossinline callback: (Language?) -> Unit) {
        // 1. Basic check
        if (!file.isFile) {
            return callback(EmptyLanguage())
        }

        // 2. TreeSitter Check
        if (TreeSitterLanguageProvider.hasTsLanguage(file)) {
            setupTsLanguageJob = editorScope.launch {
                val lang = TreeSitterLanguageProvider.forFile(file, context)
                withContext(Dispatchers.Main) {
                    callback(lang)
                }
            }.also { job ->
                job.invokeOnCompletion { err ->
                    if (err != null && err !is java.util.concurrent.CancellationException) {
                        log.error("Failed to setup tree sitter language for file: {}", file, err)
                    }
                    setupTsLanguageJob = null
                }
            }
            return
        }

        // 3. Fallback / Legacy Language Support
        val lang = when (FileUtils.getFileExtension(file)) {
            "gradle" -> GroovyLanguage()
            "c", "h", "cc", "cpp", "cxx", "hpp" -> CppLanguage()
            else -> EmptyLanguage()
        }

        callback(lang)
    }

    /**
     * Initialize the editor.
     */
    protected open fun initEditor() {

        lineNumberMarginLeft = SizeUtils.dp2px(2f).toFloat()

        _actionsMenu = EditorActionsMenu(this).also {
            it.init()
        }

        markUnmodified()

        searcher = IDEEditorSearcher(this)
        colorScheme = SchemeAndroidIDE.newInstance(context)
        inputType = createInputTypeFlags()
        val backgroundColor = getSystemBackgroundColor()
        setBackgroundColor(backgroundColor)

        val window = EditorCompletionWindow(this)
        window.setAdapter(CompletionListAdapter())
        replaceComponent(EditorAutoCompletion::class.java, window)

        getComponent(EditorTextActionWindow::class.java).isEnabled = false
        
        // [Performance] Cache render nodes for long lines to avoid lag on huge files
        props.cacheRenderNodeForLongLines = true
        // [Feature] Enable fast deletion of empty lines
        props.deleteEmptyLineFast = true

        subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (isReleased) {
                return@subscribeEvent
            }

            // Notify UI about modification
            (context as? ModificationCallback)?.onFileModified(this.file)

            file ?: return@subscribeEvent

            editorScope.launch {
                dispatchDocumentChangeEvent(event)
                checkForSignatureHelp(event)
            }
        }

        subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            if (isReleased) {
                return@subscribeEvent
            }

            if (_diagnosticWindow?.isShowing == true) {
                _diagnosticWindow?.dismiss()
            }

            selectionChangeRunner?.also {
                selectionChangeHandler.removeCallbacks(it)
                selectionChangeHandler.postDelayed(it, SELECTION_CHANGE_DELAY)
            }
        }

        EventBus.getDefault().register(this)
    }

    private fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun getSystemBackgroundColor(): Int {
        val typedValue = android.util.TypedValue()
        val theme = context.theme

        return when {
            // Material 3 background
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true) -> {
                typedValue.data
            }
            // Fallback to Material 2 background
            theme.resolveAttribute(com.google.android.material.R.attr.backgroundColor, typedValue, true) -> {
                typedValue.data
            }
            // Android system background
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true) -> {
                typedValue.data
            }
            // Ultimate fallback
            else -> {
                if (isSystemInDarkMode()) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
            }
        }
    }

    private fun getHistoryIndex(undoManager: UndoManager): Int {
        if (mUndoManagerField == null) {
            try {
                mUndoManagerField = undoManager.javaClass.getDeclaredField("stackPointer").apply {
                    isAccessible = true
                }
            } catch (e: NoSuchFieldException) {
                log.error("Failed to get 'stackPointer' field from UndoManager", e)
                return -1
            }
        }
        return try {
            mUndoManagerField!!.getInt(undoManager)
        } catch (e: Exception) {
            log.error("Failed to get value of 'stackPointer' from UndoManager", e)
            -1
        }
    }

    private inline fun launchCancellableAsyncWithProgress(
        @StringRes message: Int,
        crossinline action:
        suspend CoroutineScope.(flashbar: Flashbar, cancelChecker: ICancelChecker) -> Unit,
    ): Job? {
        if (isReleased) {
            return null
        }

        return editorScope.launchAsyncWithProgress(
            configureFlashbar = { builder, cancelChecker ->
                configureFlashbar(builder, message, cancelChecker)
            },
            action = action,
        )
    }

    protected open suspend fun onFindDefinitionResult(
        result: DefinitionResult?,
    ) = withContext(Dispatchers.Main) {

        if (isReleased) {
            return@withContext
        }

        val client = languageClient
        if (client == null) {
            log.error("No language client found to handle the definitions result")
            return@withContext
        }

        if (result == null) {
            // Log warning but maybe not flash error to user to avoid spam if it just didn't find anything
            log.warn("Definitions result from language server was null")
            flashError(string.msg_no_definition)
            return@withContext
        }

        val locations = result.locations
        if (locations.isEmpty()) {
            log.info("No definitions found")
            flashError(string.msg_no_definition)
            return@withContext
        }

        if (locations.size != 1) {
            client.showLocations(locations)
            return@withContext
        }

        val (file1, range) = locations[0]
        val currentFile = file
        if (currentFile != null && DocumentUtils.isSameFile(file1, currentFile.toPath())) {
            setSelection(range)
            return@withContext
        }

        client.showDocument(ShowDocumentParams(file1, range))
    }

    protected open suspend fun onFindReferencesResult(result: ReferenceResult?) =
        withContext(Dispatchers.Main) {
            if (isReleased) {
                return@withContext
            }

            val client = languageClient
            if (client == null) {
                log.error("No language client found to handle the references result")
                return@withContext
            }

            if (result == null) {
                log.warn("References result from language server was null")
                flashError(string.msg_no_references)
                return@withContext
            }

            val locations = result.locations
            if (locations.isEmpty()) {
                log.info("No references found")
                flashError(string.msg_no_references)
                return@withContext
            }

            if (locations.size == 1) {
                val (file1, range) = locations[0]
                val currentFile = file
                if (currentFile != null && DocumentUtils.isSameFile(file1, currentFile.toPath())) {
                    setSelection(range)
                    return@withContext
                }
            }

            client.showLocations(locations)
        }

    protected open fun dispatchDocumentOpenEvent() {
        if (isReleased) {
            return
        }

        val file = this.file ?: return

        this.fileVersion = 0

        val openEvent = DocumentOpenEvent(file.toPath(), text.toString(), fileVersion)

        eventDispatcher.dispatch(openEvent)
    }

    protected open fun dispatchDocumentChangeEvent(event: ContentChangeEvent) {
        if (isReleased) {
            return
        }

        val file = file?.toPath() ?: return
        var type = ChangeType.INSERT
        if (event.action == ContentChangeEvent.ACTION_DELETE) {
            type = ChangeType.DELETE
        } else if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
            type = ChangeType.NEW_TEXT
        }
        var changeDelta = if (type == ChangeType.NEW_TEXT) 0 else event.changedText.length
        if (type == ChangeType.DELETE) {
            changeDelta = -changeDelta
        }
        val start = event.changeStart
        val end = event.changeEnd
        val changeRange =
            Range(
                Position(start.line, start.column, start.index),
                Position(end.line, end.column, end.index),
            )
        val changedText = event.changedText.toString()
        val changeEvent =
            DocumentChangeEvent(
                file,
                changedText,
                text.toString(),
                ++fileVersion,
                type,
                changeDelta,
                changeRange,
            )

        eventDispatcher.dispatch(changeEvent)
    }

    protected open fun dispatchDocumentSelectedEvent() {
        if (isReleased) {
            return
        }
        val file = file ?: return
        eventDispatcher.dispatch(DocumentSelectedEvent(file.toPath()))
    }

    protected open fun dispatchDocumentCloseEvent() {
        if (isReleased) {
            return
        }
        val file = file ?: return

        eventDispatcher.dispatch(DocumentCloseEvent(file.toPath(), cursorLSPRange))
    }

    /**
     * Checks if the content change event should trigger signature help. Signature help trigger
     * characters are :
     * * `'('` (parentheses)
     * * `','` (comma)
     *
     * @param event The content change event.
     */
    private fun checkForSignatureHelp(event: ContentChangeEvent) {
        if (isReleased) {
            return
        }
        if (languageServer == null) {
            return
        }
        val changeLength = event.changedText.length
        if (event.action != ContentChangeEvent.ACTION_INSERT || changeLength < 1 || changeLength > 2) {
            // change length will be 1 if ',' is inserted
            // changeLength will be 2 as '(' and ')' are inserted at the same time
            return
        }

        val ch = event.changedText[0]
        if (ch == '(' || ch == ',') {
            signatureHelp()
        }
    }

    private fun configureFlashbar(
        builder: Flashbar.Builder,
        @StringRes message: Int,
        cancelChecker: ICancelChecker,
    ) {
        builder.message(message).primaryActionText(android.R.string.cancel).primaryActionTapListener {
                bar: Flashbar ->
            cancelChecker.cancel()
            bar.dismiss()
        }
    }

    private inline fun <T> safeGet(name: String, action: () -> T): T? {
        return try {
            action()
        } catch (err: Throwable) {
            logError(err, name)
            null
        }
    }

    private fun Job.logError(action: String): Job = apply {
        invokeOnCompletion { err -> logError(err, action) }
    }

    private fun logError(err: Throwable?, action: String) {
        err ?: return
        if (CancelChecker.isCancelled(err)) {
            log.warn("{} has been cancelled", action)
        } else {
            log.error("{} failed", action)
        }
    }

    override fun setSelectionAround(line: Int, column: Int) {
        editorFeatures.setSelectionAround(line, column)
    }
    
    /**
     * Helper to jump to a specific line and column, ensuring it's visible and selected.
     */
    // Check if opened file is layout XML
fun isLayoutFile(): Boolean {
    val f = this.file ?: return false
    return f.extension.equals("xml", true) && f.absolutePath.contains("layout")
}

// Open layout preview
fun openLayoutPreview() {

    if (!isLayoutFile()) {
        Toast.makeText(context, "Not a layout XML file", Toast.LENGTH_SHORT).show()
        return
    }

    val xmlContent = text?.toString() ?: ""

    try {
        val intent = android.content.Intent()
        intent.setClassName(
            context,
            "com.itsaky.androidide.preview.LayoutPreviewActivity"
        )
        intent.putExtra("layout_xml", xmlContent)

        context.startActivity(intent)

    } catch (e: Exception) {
        Toast.makeText(context, "Preview failed", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
