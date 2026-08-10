package android.zero.studio.termux.ui.screens.terminal

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.widget.doOnTextChanged
import androidx.navigation.NavController
import com.google.android.material.R
import androidx.compose.ui.res.stringResource
import android.zero.studio.termux.components.compose.preferences.base.PreferenceGroup
import android.zero.studio.termux.components.compose.preferences.base.PreferenceTemplate
import android.zero.studio.termux.components.compose.preferences.switch.PreferenceSwitch
import android.zero.studio.termux.libcommons.application
import android.zero.studio.termux.resources.strings
import android.zero.studio.termux.libcommons.child
import android.zero.studio.termux.libcommons.dpToPx
import android.zero.studio.termux.libcommons.localDir
import android.zero.studio.termux.libcommons.pendingCommand
import android.zero.studio.termux.settings.Settings
import android.content.Context
import android.zero.studio.termux.ui.fragments.TerminalSessionHolder
import android.zero.studio.termux.ui.components.InputDialog
import android.zero.studio.termux.ui.components.SessionTabBar
import android.zero.studio.termux.ui.components.TerminalEnvironmentOption
import android.zero.studio.termux.ui.components.TerminalEnvironmentSegmentedSelector
import android.zero.studio.termux.ui.components.terminalEnvironmentDescriptionRes
import android.zero.studio.termux.ui.components.terminalEnvironmentFromWorkingMode
import android.zero.studio.termux.ui.components.terminalEnvironmentToWorkingMode
import android.zero.studio.termux.ui.components.workingModeIsRoot
import android.zero.studio.termux.ui.routes.MainActivityRoutes
import android.zero.studio.termux.model.WorkingMode
import android.zero.studio.termux.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import android.zero.studio.termux.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import android.zero.studio.termux.ui.screens.terminal.virtualkeys.VirtualKeysListener
import android.zero.studio.termux.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalView
import android.zero.studio.termux.ui.theme.colorscheme.ColorSchemeManager
import android.zero.studio.termux.ui.theme.colorscheme.TerminalColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.Properties

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)


var darkText = mutableStateOf(Settings.blackTextColor)
var bitmap = mutableStateOf<ImageBitmap?>(null)

private val file = application!!.filesDir.child("font.ttf")
private var font = (if (file.exists() && file.canRead()){
    Typeface.createFromFile(file)
}else{
    Typeface.MONOSPACE
})

suspend fun setFont(typeface: Typeface) = withContext(Dispatchers.Main){
    font = typeface
    terminalView.get()?.apply {
        setTypeface(typeface)
        onScreenUpdated()
    }
}

inline fun getViewColor(): Int{
    return if (darkText.value){
        Color.BLACK
    }else{
        Color.WHITE
    }
}

inline fun getComposeColor():androidx.compose.ui.graphics.Color{
    return if (darkText.value){
        androidx.compose.ui.graphics.Color.Black
    }else{
        androidx.compose.ui.graphics.Color.White
    }
}

private fun resolveDarkTextForTerminalSurface(scheme: TerminalColorScheme): Boolean {
    return if (bitmap.value != null) {
        Settings.blackTextColor
    } else {
        ColorSchemeManager.shouldUseDarkUiText(scheme)
    }
}

private fun applyLegacyColorOverrides(terminalView: TerminalView, baseScheme: TerminalColorScheme) {
    val colorsFile = localDir().child("colors.properties")
    if (!colorsFile.exists() || !colorsFile.isFile) {
        return
    }

    val props = runCatching {
        Properties().also { loadedProps ->
            FileInputStream(colorsFile).use { input ->
                loadedProps.load(input)
            }
        }
    }.getOrNull() ?: return

    TerminalColors.COLOR_SCHEME.updateWith(props)
    terminalView.mEmulator?.mColors?.reset()

    val overriddenBackground = props.getProperty("background")
        ?.let { colorHex -> runCatching { TerminalColorScheme.parseHexColor(colorHex) }.getOrNull() }

    if (bitmap.value == null) {
        val effectiveBackground = overriddenBackground ?: baseScheme.background
        terminalView.setBackgroundColor(effectiveBackground)
        darkText.value = ColorSchemeManager.shouldUseDarkUiText(
            baseScheme.copy(background = effectiveBackground)
        )
    }
}

var showToolbar = mutableStateOf(Settings.toolbar)
var showVirtualKeys = mutableStateOf(Settings.virtualKeys)
var showHorizontalToolbar = mutableStateOf(Settings.toolbar)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    LaunchedEffect(Unit){
        withContext(Dispatchers.IO){
            if (context.filesDir.child("background").exists().not()){
                darkText.value = resolveDarkTextForTerminalSurface(ColorSchemeManager.getCurrentScheme())
            }else if (bitmap.value == null){
                val fullBitmap = BitmapFactory.decodeFile(context.filesDir.child("background").absolutePath)?.asImageBitmap()
                if (fullBitmap != null) bitmap.value = fullBitmap
            }
        }


        scope.launch(Dispatchers.Main){
            virtualKeysView.get()?.apply {
                virtualKeysViewClient =
                    terminalView.get()?.mTermSession?.let {
                        VirtualKeysListener(
                            it
                        )
                    }

                buttonTextColor = getViewColor()

                reload(
                    VirtualKeysInfo(
                        VIRTUAL_KEYS,
                        "",
                        VirtualKeysConstants.CONTROL_CHARS_ALIASES
                    )
                )
            }

            terminalView.get()?.apply {
                onScreenUpdated()
                // Colors are managed by ColorSchemeManager
            }
        }


    }

    Box {
        var showAddDialog by remember { mutableStateOf(false) }
        var selectedNewSessionEnvironment by remember {
            mutableStateOf(terminalEnvironmentFromWorkingMode(Settings.working_Mode))
        }
        var startNewSessionWithRoot by remember {
            mutableStateOf(workingModeIsRoot(Settings.working_Mode))
        }
        var selectedNewSessionVersion by remember {
            mutableStateOf(Settings.linux_distribution_version)
        }
        var showRenameDialogFor by remember { mutableStateOf<String?>(null) }
        var pendingEmptySessionCreate by remember { mutableStateOf(false) }

        // Helper function to generate unique session ID
        fun generateUniqueSessionId(): String {
            val existingStrings = TerminalSessionHolder.sessionBinder?.getService()?.sessionOrder?.toList() ?: emptyList()
            var index = 1
            var newString: String
            do {
                newString = "main$index"
                index++
            } while (newString in existingStrings)
            return newString
        }

        // Helper function to create a new session
        fun createNewSession(workingMode: Int) {
            if (!Rootfs.isFilesDownloaded(workingMode)) {
                Settings.working_Mode = workingMode
                navController.navigate(MainActivityRoutes.MainScreen.route) {
                    popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                    launchSingleTop = true
                }
                return
            }

            val sessionId = generateUniqueSessionId()
            val terminalViewInstance = terminalView.get()
            if (terminalViewInstance == null) {
                TerminalSessionHolder.sessionBinder?.getService()?.currentSession?.value = Pair(sessionId, workingMode)
                pendingEmptySessionCreate = true
            } else {
                val client = TerminalBackEnd(terminalViewInstance, context)
                TerminalSessionHolder.sessionBinder!!.createSession(
                    sessionId,
                    client,
                    context,
                    workingMode = workingMode
                )
                changeSession(context, session_id = sessionId)
                pendingEmptySessionCreate = false
            }
        }

        fun openAddSessionDialog() {
            val initialEnvironment = terminalEnvironmentFromWorkingMode(Settings.working_Mode)
            selectedNewSessionEnvironment = initialEnvironment
            startNewSessionWithRoot = workingModeIsRoot(Settings.working_Mode) && initialEnvironment.supportsRoot
            selectedNewSessionVersion = Settings.linux_distribution_version
            showAddDialog = true
        }

        // Helper functions to close tab-backed sessions. The terminal pane is hidden
        // when no sessions remain, so closing all sessions never recreates an
        // implicit fallback session.
        fun handleCloseSession(sessionId: String, currentSessionId: String) {
            val service = TerminalSessionHolder.sessionBinder?.getService() ?: return
            val keys = service.sessionOrder.toList()

            if (keys.size > 1 && sessionId == currentSessionId) {
                val currentIndex = keys.indexOf(sessionId)
                val nextId = if (currentIndex < keys.lastIndex) keys[currentIndex + 1] else keys[currentIndex - 1]
                changeSession(context, nextId)
            }

            TerminalSessionHolder.sessionBinder?.terminateSession(
                sessionId,
                stopServiceWhenEmpty = false,
            )
        }

        fun handleCloseOtherSessions(sessionId: String) {
            val service = TerminalSessionHolder.sessionBinder?.getService() ?: return
            if (service.currentSession.value.first != sessionId) {
                changeSession(context, sessionId)
            }
            service.sessionOrder.toList()
                .filter { it != sessionId }
                .forEach { TerminalSessionHolder.sessionBinder?.terminateSession(it, stopServiceWhenEmpty = false) }
        }

        fun handleCloseAllSessions() {
            val service = TerminalSessionHolder.sessionBinder?.getService() ?: return
            pendingEmptySessionCreate = false
            TerminalSessionHolder.sessionBinder?.terminateAllSessions()
        }
        // Add session dialog (shared between wide and narrow layouts)
        if (showAddDialog) {
            BasicAlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                }
            ) {
                PreferenceGroup {
                    PreferenceTemplate(
                        title = { Text(stringResource(strings.shortcut_new_session)) },
                        description = {
                            Text(
                                stringResource(
                                    terminalEnvironmentDescriptionRes(
                                        selectedNewSessionEnvironment,
                                        startNewSessionWithRoot,
                                    )
                                )
                            )
                        },
                    ) {}

                    TerminalEnvironmentSegmentedSelector(
                        selectedEnvironment = selectedNewSessionEnvironment,
                        onSelected = { environment ->
                            selectedNewSessionEnvironment = environment
                            if (!environment.supportsRoot) {
                                startNewSessionWithRoot = false
                            }
                        },
                    )

                    if (selectedNewSessionEnvironment.versions.isNotEmpty()) {
                        var versionExpanded by remember { mutableStateOf(false) }
                        val versionOptions = selectedNewSessionEnvironment.versions
                        if (selectedNewSessionVersion !in versionOptions) {
                            selectedNewSessionVersion = versionOptions.first()
                        }
                        ExposedDropdownMenuBox(
                            expanded = versionExpanded,
                            onExpandedChange = { versionExpanded = !versionExpanded },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            OutlinedTextField(
                                value = selectedNewSessionVersion,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text("系统版本") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = versionExpanded,
                                onDismissRequest = { versionExpanded = false },
                            ) {
                                versionOptions.forEach { version ->
                                    DropdownMenuItem(
                                        text = { Text(version) },
                                        onClick = {
                                            selectedNewSessionVersion = version
                                            Settings.linux_distribution_version = version
                                            versionExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (selectedNewSessionEnvironment.supportsRoot) {
                        PreferenceSwitch(
                            checked = startNewSessionWithRoot,
                            onCheckedChange = {
                                startNewSessionWithRoot = it
                            },
                            label = stringResource(strings.terminal_env_root_toggle),
                            description = stringResource(
                                strings.terminal_env_root_toggle_desc,
                                stringResource(selectedNewSessionEnvironment.labelRes),
                            ),
                            onClick = {
                                startNewSessionWithRoot = !startNewSessionWithRoot
                            },
                        )
                    } else {
                        PreferenceTemplate(
                            title = { Text(stringResource(strings.terminal_env_android_root_unavailable_title)) },
                            description = { Text(stringResource(strings.terminal_env_android_root_unavailable_desc)) },
                        ) {}
                    }

                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(min = 48.dp),
                        onClick = {
                            if (selectedNewSessionEnvironment.versions.isNotEmpty()) {
                                Settings.linux_distribution_version = selectedNewSessionVersion
                            }
                            createNewSession(
                                workingMode = terminalEnvironmentToWorkingMode(
                                    selectedNewSessionEnvironment,
                                    startNewSessionWithRoot,
                                )
                            )
                            showAddDialog = false
                        },
                    ) {
                        Text(stringResource(strings.shortcut_new_session))
                    }
                }
            }
        }

        // Rename session dialog
        showRenameDialogFor?.let { sessionId ->
            val service = TerminalSessionHolder.sessionBinder?.getService()
            val currentDisplayTitle = service?.getDisplayTitle(sessionId) ?: sessionId
            var renameValue by remember(sessionId) { mutableStateOf(currentDisplayTitle) }
            InputDialog(
                title = stringResource(strings.session) + " — " + sessionId,
                inputLabel = stringResource(strings.session),
                inputValue = renameValue,
                onInputValueChange = { renameValue = it },
                onConfirm = {
                    service?.setCustomName(sessionId, renameValue)
                },
                onDismiss = { showRenameDialogFor = null }
            )
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            val service = TerminalSessionHolder.sessionBinder?.getService()
            val sessionKeys = service?.sessionOrder?.toList() ?: emptyList()
            val currentSessionId = service?.currentSession?.value?.first ?: ""

            LaunchedEffect(sessionKeys.size) {
                if (sessionKeys.isNotEmpty()) pendingEmptySessionCreate = false
            }

            if (sessionKeys.isNotEmpty()) {
                SessionTabBar(
                    sessions = sessionKeys,
                    currentSessionId = currentSessionId,
                    getDisplayTitle = { id -> service?.getDisplayTitle(id) ?: id },
                    getWorkingMode = { id -> service?.getWorkingMode(id) },
                    onSelectSession = { id -> changeSession(context, id) },
                    onCloseSession = { id -> handleCloseSession(id, currentSessionId) },
                    onCloseOtherSessions = { id -> handleCloseOtherSessions(id) },
                    onCloseAllSessions = { handleCloseAllSessions() },
                    onAddSession = { openAddSessionDialog() },
                    onRenameSession = { id -> showRenameDialogFor = id },
                    onOpenSettings = { navController.navigate(MainActivityRoutes.Settings.route) },
                    modifier = Modifier.fillMaxWidth()
                )

                TabBarTerminalContent(
                    modifier = Modifier.weight(1f)
                )
            } else if (pendingEmptySessionCreate) {
                TabBarTerminalContent(
                    modifier = Modifier.weight(1f)
                )
            } else {
                EmptyTerminalSessions(
                    onAddSession = { openAddSessionDialog() },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                )
            }
        }
    }
}


/**
 * Returns a color for session text based on working mode privilege level.
 * - Alpine Root: red (danger)
 * - Android: amber (warning)
 * - Alpine/default: theme default
 */
@Composable
fun getSessionTextColor(workingMode: Int?): androidx.compose.ui.graphics.Color {
    return when (workingMode) {
        WorkingMode.ALPINE_ROOT -> androidx.compose.ui.graphics.Color(0xFFEF5350)
        WorkingMode.ARCH_ROOT -> androidx.compose.ui.graphics.Color(0xFFEF5350)
        WorkingMode.UBUNTU -> androidx.compose.ui.graphics.Color(0xFFFFB300)
        WorkingMode.UBUNTU_ROOT -> androidx.compose.ui.graphics.Color(0xFFEF5350)
        WorkingMode.ANDROID -> androidx.compose.ui.graphics.Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun EmptyTerminalSessions(
    onAddSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        BackgroundImage()
        FilledTonalButton(onClick = onAddSession) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(strings.shortcut_new_session))
        }
    }
}

@Composable
fun TabBarTerminalContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        BackgroundImage()
        TerminalPaneContent(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalPaneContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Observe color scheme state to trigger recomposition when it changes
    val currentScheme = ColorSchemeManager.currentScheme.value
    val terminalBackgroundColor = currentScheme.background

    // Request focus on the terminal view after Compose layout completes.
    // This handles: initial launch, returning from Settings, and session switches.
    // A short delay ensures Compose's own focus system has finished its pass.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        terminalView.get()?.post { terminalView.get()?.requestFocus() }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { context ->
                TerminalView(context, null).apply {
                    terminalView = WeakReference(this)
                    setTextSize(
                        dpToPx(
                            Settings.terminal_font_size.toFloat(),
                            context
                        )
                    )
                    val client = TerminalBackEnd(this, context)

                    val session = if (pendingCommand != null) {
                        TerminalSessionHolder.sessionBinder!!.getService().currentSession.value = Pair(
                            pendingCommand!!.id, pendingCommand!!.workingMode
                        )
                        TerminalSessionHolder.sessionBinder!!.getSession(
                            pendingCommand!!.id
                        )
                            ?: TerminalSessionHolder.sessionBinder!!.createSession(
                                pendingCommand!!.id,
                                client,
                                context, workingMode = Settings.working_Mode
                            )
                    } else {
                        TerminalSessionHolder.sessionBinder!!.getSession(
                            TerminalSessionHolder.sessionBinder!!.getService().currentSession.value.first
                        )
                            ?: TerminalSessionHolder.sessionBinder!!.createSession(
                                TerminalSessionHolder.sessionBinder!!.getService().currentSession.value.first,
                                client,
                                context, workingMode = Settings.working_Mode
                            )
                    }

                    session.updateTerminalSessionClient(client)
                    attachSession(session)
                    setTerminalViewClient(client)
                    setTypeface(font)

                    isFocusable = true
                    isFocusableInTouchMode = true

                    post {
                        // Apply the saved color scheme
                        ColorSchemeManager.setTerminalView(this)
                        ColorSchemeManager.applyCurrentSchemeToTerminal()
                        
                        // Get the current scheme and apply background color directly
                        val scheme = ColorSchemeManager.getCurrentScheme()
                        // If a background image is set, make terminal view transparent
                        // so the image shows through; otherwise use scheme background
                        if (bitmap.value != null) {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        } else {
                            setBackgroundColor(scheme.background)
                        }
                        
                        darkText.value = resolveDarkTextForTerminalSurface(scheme)
                        
                        keepScreenOn = true
                        requestFocus()
                        
                        // Legacy colors.properties support (can override scheme)
                        applyLegacyColorOverrides(this, scheme)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { terminalView ->
                // Apply color scheme background - this runs when currentScheme changes
                // If a background image is set, make terminal view transparent
                // so the image shows through; otherwise use scheme background
                if (bitmap.value != null) {
                    terminalView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                } else {
                    terminalView.setBackgroundColor(terminalBackgroundColor)
                }
                terminalView.mEmulator?.mColors?.reset()
                terminalView.onScreenUpdated()
                
                darkText.value = resolveDarkTextForTerminalSurface(currentScheme)

                applyLegacyColorOverrides(terminalView, currentScheme)
                
                // Handle custom background image text color adjustment
                if (bitmap.value != null) {
                    val color = getViewColor()
                    terminalView.mEmulator?.mColors?.mCurrentColors?.apply {
                        set(256, color)
                        set(258, color)
                    }
                }
            },
        )

        if (showVirtualKeys.value) {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp)
            ) { page ->
                when (page) {
                    0 -> {
                        terminalView.get()?.requestFocus()
                        AndroidView(
                            factory = { context ->
                                VirtualKeysView(context, null).apply {
                                    virtualKeysView = WeakReference(this)
                                    virtualKeysViewClient =
                                        terminalView.get()?.mTermSession?.let {
                                            VirtualKeysListener(it)
                                        }

                                    buttonTextColor = getViewColor()

                                    reload(
                                        VirtualKeysInfo(
                                            VIRTUAL_KEYS,
                                            "",
                                            VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp),
                            update = { keysView ->
                                keysView.buttonTextColor = getViewColor()
                            }
                        )
                    }

                    1 -> {
                        var text by rememberSaveable { mutableStateOf("") }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp),
                            factory = { ctx ->
                                EditText(ctx).apply {
                                    maxLines = 1
                                    isSingleLine = true
                                    imeOptions = EditorInfo.IME_ACTION_DONE

                                    doOnTextChanged { textInput, _, _, _ ->
                                        text = textInput.toString()
                                    }

                                    setOnEditorActionListener { _, actionId, _ ->
                                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                                            if (text.isEmpty()) {
                                                val eventDown = KeyEvent(
                                                    KeyEvent.ACTION_DOWN,
                                                    KeyEvent.KEYCODE_ENTER
                                                )
                                                val eventUp = KeyEvent(
                                                    KeyEvent.ACTION_UP,
                                                    KeyEvent.KEYCODE_ENTER
                                                )
                                                terminalView.get()?.dispatchKeyEvent(eventDown)
                                                terminalView.get()?.dispatchKeyEvent(eventUp)
                                            } else {
                                                terminalView.get()?.currentSession?.write(text)
                                                setText("")
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                }
                            },
                            update = { editText ->
                                if (editText.text.toString() != text) {
                                    editText.setText(text)
                                    editText.setSelection(text.length)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            virtualKeysView = WeakReference(null)
        }
    }
}

var wallAlpha by mutableFloatStateOf(Settings.wallTransparency)

@Composable
fun BackgroundImage() {
    bitmap.value?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(wallAlpha)
                .zIndex(-1f)
        )
    }
}




fun changeSession(context: Context, session_id: String) {
    terminalView.get()?.apply {
        val client = TerminalBackEnd(this, context)
        val session =
            TerminalSessionHolder.sessionBinder!!.getSession(session_id)
                ?: TerminalSessionHolder.sessionBinder!!.createSession(
                    session_id,
                    client,
                    context,workingMode = Settings.working_Mode
                )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            // Apply color scheme to this session
            val scheme = ColorSchemeManager.getCurrentScheme()
            // If a background image is set, make terminal view transparent
            if (bitmap.value != null) {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            } else {
                setBackgroundColor(scheme.background)
            }
            
            // Update terminal colors
            mEmulator?.mColors?.reset()
            
            darkText.value = resolveDarkTextForTerminalSurface(scheme)

            applyLegacyColorOverrides(this, scheme)
            
            keepScreenOn = true
            requestFocus()
        }
        virtualKeysView.get()?.apply {
            virtualKeysViewClient =
                terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }

    }
    TerminalSessionHolder.sessionBinder!!.getService().currentSession.value = Pair(session_id,TerminalSessionHolder.sessionBinder!!.getService().sessionList[session_id]!!)

}


const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
