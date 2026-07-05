package com.termix.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.termix.resources.strings
import com.termix.libcommons.child
import com.termix.libcommons.createFileIfNot
import com.termix.libcommons.dpToPx
import com.termix.settings.Settings
import com.termix.model.WorkingMode
import com.termix.ui.activities.terminal.MainActivity
import com.termix.ui.components.SettingsToggle
import com.termix.ui.components.TerminalEnvironmentOption
import com.termix.ui.components.TerminalEnvironmentSegmentedSelector
import com.termix.ui.components.terminalEnvironmentDescriptionRes
import com.termix.ui.components.terminalEnvironmentFromWorkingMode
import com.termix.ui.components.terminalEnvironmentToWorkingMode
import com.termix.ui.components.workingModeIsRoot
import com.termix.ui.navHosts.horizontal_statusBar
import com.termix.ui.navHosts.showStatusBar
import com.termix.ui.screens.customization.ColorSchemeSelector
import com.termix.ui.screens.terminal.bitmap
import com.termix.ui.screens.terminal.darkText
import com.termix.ui.screens.terminal.setFont
import com.termix.ui.screens.terminal.showHorizontalToolbar
import com.termix.ui.screens.terminal.showToolbar
import com.termix.ui.screens.terminal.showVirtualKeys
import com.termix.ui.screens.terminal.terminalView
import com.termix.ui.screens.terminal.wallAlpha
import com.termix.ui.screens.terminal.ShortcutAction
import com.termix.ui.screens.terminal.ShortcutCaptureDialog
import com.termix.ui.theme.colorscheme.ColorSchemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.io.File
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = modifier
            .combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = title,
        description = description,
        startWidget = startWidget,
        endWidget = endWidget,
        applyPaddings = false
    )

}

object InputMode {
    const val DEFAULT = 0
    const val TYPE_NULL = 1
    const val VISIBLE_PASSWORD = 2
}
object LayoutMode {
    const val CLASSIC = 0   // Original Material drawer + TopAppBar
    const val TAB_BAR = 1   // Horizontal tab bar mode
}

object CloseLastSessionBehavior {
    const val EXIT_APP = 0      // Exit the app when last session is closed
    const val NEW_SESSION = 1   // Create a new session instead of exiting
}
object ShellType {
    const val BASH = 0
    const val ASH = 1
    const val ZSH = 2
}

private const val min_text_size = 10f
private const val max_text_size = 20f

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                return cursor.getString(nameIndex)
            }
        }
    } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return File(uri.path!!).name
    }
    return null
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(modifier: Modifier = Modifier,navController: NavController,mainActivity: MainActivity) {
    val context = LocalContext.current
    val initialTerminalEnvironment = remember {
        terminalEnvironmentFromWorkingMode(Settings.working_Mode)
    }
    var selectedTerminalEnvironment by remember { mutableStateOf(initialTerminalEnvironment) }
    var startWithRoot by remember {
        mutableStateOf(workingModeIsRoot(Settings.working_Mode) && initialTerminalEnvironment.supportsRoot)
    }
    var selectedInputMode by remember { mutableIntStateOf(Settings.input_mode) }
    var selectedLayoutMode by remember { mutableIntStateOf(Settings.layout_mode) }
    var selectedCloseLastSessionBehavior by remember { mutableIntStateOf(Settings.close_last_session_behavior) }
    var selectedShellType by remember { mutableIntStateOf(Settings.default_shell) }

    val applyTerminalEnvironmentSelection: (TerminalEnvironmentOption, Boolean) -> Unit = { environment, rootEnabled ->
        val normalizedRoot = rootEnabled && environment.supportsRoot
        selectedTerminalEnvironment = environment
        startWithRoot = normalizedRoot
        Settings.working_Mode = terminalEnvironmentToWorkingMode(environment, normalizedRoot)
    }

    PreferenceLayout(label = stringResource(strings.settings)) {

        // ======================================================
        // 1. Appearance & Interface
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.appearance_and_interface)) {
            // -- Text Size --
            var sliderPosition by remember { mutableFloatStateOf(Settings.terminal_font_size.toFloat()) }
            PreferenceTemplate(title = { Text(stringResource(strings.text_size)) }) {
                Text(sliderPosition.toInt().toString())
            }
            PreferenceTemplate(title = {}) {
                Slider(
                    modifier = modifier,
                    value = sliderPosition,
                    onValueChange = {
                        sliderPosition = it
                        Settings.terminal_font_size = it.toInt()
                        terminalView.get()?.setTextSize(dpToPx(it.toFloat(), context))
                    },
                    steps = (max_text_size - min_text_size).toInt() - 1,
                    valueRange = min_text_size..max_text_size,
                )
            }
        }

        // -- Scrollback Lines --
        PreferenceGroup {
            val scrollbackSteps = remember {
                listOf(500, 1000, 2000, 3000, 5000, 8000, 10000, 20000, 50000)
            }
            val savedValue = Settings.scrollback_lines
            val initialIndex = remember {
                scrollbackSteps.indexOfFirst { it >= savedValue }
                    .let { if (it == -1) scrollbackSteps.lastIndex else it }
            }
            var sliderIndex by remember { mutableFloatStateOf(initialIndex.toFloat()) }
            val currentValue = scrollbackSteps[sliderIndex.roundToInt().coerceIn(0, scrollbackSteps.lastIndex)]
            PreferenceTemplate(title = { Text(stringResource(strings.scrollback_lines)) }) {
                Text(
                    if (currentValue >= 1000) "${currentValue / 1000}K" else currentValue.toString(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            PreferenceTemplate(title = {}) {
                Slider(
                    modifier = modifier,
                    value = sliderIndex,
                    onValueChange = {
                        sliderIndex = it
                        Settings.scrollback_lines = scrollbackSteps[it.roundToInt().coerceIn(0, scrollbackSteps.lastIndex)]
                    },
                    steps = scrollbackSteps.size - 2,
                    valueRange = 0f..(scrollbackSteps.size - 1).toFloat(),
                )
            }
            // Min / Max labels
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("500", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("50K", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // -- Color Scheme --
        PreferenceGroup {
            PreferenceTemplate(
                title = { Text(stringResource(strings.color_scheme)) },
                description = { Text(stringResource(strings.color_scheme_desc)) }
            ) {}
            ColorSchemeSelector(
                onSchemeSelected = { scheme ->
                    terminalView.get()?.let { tv ->
                        ColorSchemeManager.setTerminalView(tv)
                        ColorSchemeManager.applyCurrentSchemeToTerminal()
                        tv.invalidate()
                    }
                    darkText.value = if (bitmap.value != null) {
                        Settings.blackTextColor
                    } else {
                        ColorSchemeManager.shouldUseDarkUiText(ColorSchemeManager.getCurrentScheme())
                    }
                }
            )
        }

        // -- Custom Font --
        PreferenceGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                }
                Text(
                    text = stringResource(strings.font_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            val scope = rememberCoroutineScope()
            val font by remember { mutableStateOf<File>(context.filesDir.child("font.ttf")) }
            var fontExists by remember { mutableStateOf(font.exists()) }

            val noFontSelected = stringResource(strings.no_font_selected)
            var fontName by remember { mutableStateOf(if (!fontExists || !font.canRead()){
                noFontSelected
            }else{
                Settings.custom_font_name
            }) }

            val fontLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    scope.launch(Dispatchers.IO){
                        font.createFileIfNot()
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            font.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        val name = getFileNameFromUri(context,uri).toString()
                        Settings.custom_font_name = name
                        fontName = name
                        fontExists = font.exists()
                        setFont(Typeface.createFromFile(font))
                    }
                }

            }

            PreferenceTemplate(
                modifier = Modifier.clickable(onClick = {
                    scope.launch{
                        fontLauncher.launch("font/ttf")
                    }
                }),
                title = {
                    Text(stringResource(strings.custom_font))
                },
                description = {
                    Text(fontName)
                },
                endWidget = {
                    if (fontExists){
                        IconButton(onClick = {
                            scope.launch{
                                font.delete()
                                fontName = noFontSelected
                                Settings.custom_font_name = noFontSelected
                                setFont(Typeface.MONOSPACE)
                                fontExists = font.exists()
                            }
                        }) {
                            Icon(imageVector = Icons.Outlined.Delete,contentDescription = "delete")
                        }
                    }
                }
            )
        }

        // -- Custom Background --
        PreferenceGroup {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val image by remember { mutableStateOf<File>(context.filesDir.child("background")) }

            var imageExists by remember { mutableStateOf(image.exists()) }

            val noImageSelected = stringResource(strings.no_image_selected)
            var backgroundName by remember { mutableStateOf(if (!imageExists || !image.canRead()){
                noImageSelected
            }else{
                Settings.custom_background_name
            }) }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    scope.launch(Dispatchers.IO){
                        image.createFileIfNot()
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            image.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        val name = getFileNameFromUri(context,uri).toString()
                        Settings.custom_background_name = name
                        backgroundName = name

                        withContext(Dispatchers.IO) {
                            val file = context.filesDir.child("background")
                            if (!file.exists()) return@withContext
                            bitmap.value = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            bitmap.value?.apply {
                                val androidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val buffer = IntArray(width * height)
                                readPixels(buffer, 0, 0, width, height)
                                androidBitmap.setPixels(buffer, 0, width, 0, 0, width, height)
                                Palette.from(androidBitmap).generate { palette ->
                                    val dominantColor = palette?.getDominantColor(android.graphics.Color.WHITE)
                                    val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(dominantColor ?: android.graphics.Color.WHITE)
                                    val blackText = luminance > 0.5f
                                    Settings.blackTextColor = blackText
                                    darkText.value = blackText
                                }
                            }

                        }
                        imageExists = image.exists()
                    }

                }

            }

            PreferenceTemplate(
                modifier = Modifier.clickable(onClick = {
                    scope.launch{
                        launcher.launch("image/*")
                    }
                }),
                title = {
                    Text(stringResource(strings.custom_background))
                },
                description = {
                    Text(backgroundName)
                },
                endWidget = {
                    if (imageExists){
                        IconButton(onClick = {
                            scope.launch{
                                image.delete()
                                Settings.custom_background_name = noImageSelected
                                backgroundName = noImageSelected
                                darkText.value = ColorSchemeManager.shouldUseDarkUiText(ColorSchemeManager.getCurrentScheme())
                                imageExists = image.exists()
                                bitmap.value = null
                            }
                        }) {
                            Icon(imageVector = Icons.Outlined.Delete,contentDescription = "delete")
                        }
                    }

                }
            )

        }

        // -- Wallpaper Alpha --
        PreferenceGroup {
            PreferenceTemplate(title = {
                Text(stringResource(strings.wallpaper_alpha))
            }) { Text(
                DecimalFormat("0.##")
                .apply { roundingMode = RoundingMode.HALF_UP }
                .format(wallAlpha)) }
            PreferenceTemplate(title = {}){
                Slider(
                    value = wallAlpha,
                    onValueChange = {
                        wallAlpha = it
                    },
                    onValueChangeFinished = {
                        Settings.wallTransparency = wallAlpha
                    }
                )
            }
        }

        // -- UI Elements (StatusBar, TitleBar, Virtual Keys, Keyboard) --
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.statusbar),
                description = stringResource(strings.statusbar_desc),
                showSwitch = true,
                default = Settings.statusBar, sideEffect = {
                    Settings.statusBar = it
                    showStatusBar.value = it
                })

            SettingsToggle(
                label = stringResource(strings.horizontal_statusbar),
                description = stringResource(strings.horizontal_statusbar_desc),
                showSwitch = true,
                default = Settings.horizontal_statusBar, sideEffect = {
                    Settings.horizontal_statusBar = it
                    horizontal_statusBar.value = it
                })


            val attentionTitle = stringResource(strings.attention)
            val toolbarWarning = stringResource(strings.toolbar_warning)
            val cancelStr = stringResource(strings.cancel)
            val sideEffect:(Boolean)-> Unit = {
                if (!it && showToolbar.value){
                    MaterialAlertDialogBuilder(context).apply {
                        setTitle(attentionTitle)
                        setMessage(toolbarWarning)
                        setPositiveButton("OK"){_,_ ->
                            Settings.toolbar = it
                            showToolbar.value = it
                        }
                        setNegativeButton(cancelStr,null)
                        show()
                    }
                }else{
                    Settings.toolbar = it
                    showToolbar.value = it
                }

            }


            PreferenceSwitch(checked = showToolbar.value,
                onCheckedChange = {
                    sideEffect.invoke(it)
                },
                label = stringResource(strings.titlebar),
                modifier = modifier,
                description = stringResource(strings.titlebar_desc),
                onClick = {
                    sideEffect.invoke(!showToolbar.value)
                })

            SettingsToggle(
                isEnabled = showToolbar.value,
                label = stringResource(strings.horizontal_titlebar),
                description = stringResource(strings.horizontal_titlebar_desc),
                showSwitch = true,
                default = Settings.toolbar_in_horizontal, sideEffect = {
                    Settings.toolbar_in_horizontal = it
                    showHorizontalToolbar.value = it
                })
            SettingsToggle(
                label = stringResource(strings.virtual_keys),
                description = stringResource(strings.virtual_keys_desc),
                showSwitch = true,
                default = Settings.virtualKeys, sideEffect = {
                    Settings.virtualKeys = it
                    showVirtualKeys.value = it
                })

            SettingsToggle(
                label = stringResource(strings.hide_soft_keyboard),
                description = stringResource(strings.hide_soft_keyboard_desc),
                showSwitch = true,
                default = Settings.hide_soft_keyboard_if_hwd, sideEffect = {
                    Settings.hide_soft_keyboard_if_hwd = it
                })

        }

        // ======================================================
        // 2. Input & Feedback
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.input_and_feedback)) {

            SettingsCard(
                title = { Text(stringResource(strings.input_mode_default)) },
                description = { Text(stringResource(strings.input_mode_default_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedInputMode == InputMode.DEFAULT,
                        onClick = {
                            selectedInputMode = InputMode.DEFAULT
                            Settings.input_mode = selectedInputMode
                        })
                },
                onClick = {
                    selectedInputMode = InputMode.DEFAULT
                    Settings.input_mode = selectedInputMode
                })

            SettingsCard(
                title = { Text(stringResource(strings.input_mode_type_null)) },
                description = { Text(stringResource(strings.input_mode_type_null_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedInputMode == InputMode.TYPE_NULL,
                        onClick = {
                            selectedInputMode = InputMode.TYPE_NULL
                            Settings.input_mode = selectedInputMode
                        })
                },
                onClick = {
                    selectedInputMode = InputMode.TYPE_NULL
                    Settings.input_mode = selectedInputMode
                })

            SettingsCard(
                title = { Text(stringResource(strings.input_mode_visible_password)) },
                description = { Text(stringResource(strings.input_mode_visible_password_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedInputMode == InputMode.VISIBLE_PASSWORD,
                        onClick = {
                            selectedInputMode = InputMode.VISIBLE_PASSWORD
                            Settings.input_mode = selectedInputMode
                        })
                },
                onClick = {
                    selectedInputMode = InputMode.VISIBLE_PASSWORD
                    Settings.input_mode = selectedInputMode
                })
        }

        PreferenceGroup {
            SettingsToggle(label = stringResource(strings.bell), description = stringResource(strings.bell_desc), showSwitch = true, default = Settings.bell, sideEffect = {
                Settings.bell = it
            })

            SettingsToggle(label = stringResource(strings.vibrate), description = stringResource(strings.vibrate_desc), showSwitch = true, default = Settings.vibrate, sideEffect = {
                Settings.vibrate = it
            })
        }

        // ======================================================
        // 3. Terminal Environment
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.terminal_environment)) {

            PreferenceTemplate(
                title = { Text(stringResource(strings.default_working_mode)) },
                description = {
                    Text(
                        stringResource(
                            terminalEnvironmentDescriptionRes(
                                selectedTerminalEnvironment,
                                startWithRoot,
                            )
                        )
                    )
                },
            ) {}

            TerminalEnvironmentSegmentedSelector(
                selectedEnvironment = selectedTerminalEnvironment,
                onSelected = { environment ->
                    applyTerminalEnvironmentSelection(environment, startWithRoot)
                },
            )

            if (selectedTerminalEnvironment.supportsRoot) {
                PreferenceSwitch(
                    checked = startWithRoot,
                    onCheckedChange = {
                        applyTerminalEnvironmentSelection(selectedTerminalEnvironment, it)
                    },
                    label = stringResource(strings.terminal_env_root_toggle),
                    modifier = modifier,
                    description = stringResource(
                        strings.terminal_env_root_toggle_desc,
                        stringResource(selectedTerminalEnvironment.labelRes),
                    ),
                    onClick = {
                        applyTerminalEnvironmentSelection(selectedTerminalEnvironment, !startWithRoot)
                    },
                )
            } else {
                PreferenceTemplate(
                    title = { Text(stringResource(strings.terminal_env_android_root_unavailable_title)) },
                    description = { Text(stringResource(strings.terminal_env_android_root_unavailable_desc)) },
                ) {}
            }
        }

        PreferenceGroup(heading = stringResource(strings.default_shell)) {

            SettingsCard(
                title = { Text("Bash") },
                description = { Text(stringResource(strings.shell_bash_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedShellType == ShellType.BASH,
                        onClick = {
                            selectedShellType = ShellType.BASH
                            Settings.default_shell = selectedShellType
                        })
                },
                onClick = {
                    selectedShellType = ShellType.BASH
                    Settings.default_shell = selectedShellType
                })

            SettingsCard(
                title = { Text("Ash") },
                description = { Text(stringResource(strings.shell_ash_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedShellType == ShellType.ASH,
                        onClick = {
                            selectedShellType = ShellType.ASH
                            Settings.default_shell = selectedShellType
                        })
                },
                onClick = {
                    selectedShellType = ShellType.ASH
                    Settings.default_shell = selectedShellType
                })

            SettingsCard(
                title = { Text("Zsh") },
                description = { Text(stringResource(strings.shell_zsh_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedShellType == ShellType.ZSH,
                        onClick = {
                            selectedShellType = ShellType.ZSH
                            Settings.default_shell = selectedShellType
                        })
                },
                onClick = {
                    selectedShellType = ShellType.ZSH
                    Settings.default_shell = selectedShellType
                })
        }

        // ======================================================
        // 4. Session Behavior
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.session_behavior)) {
            SettingsCard(
                title = { Text(stringResource(strings.layout_mode_classic)) },
                description = { Text(stringResource(strings.layout_mode_classic_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedLayoutMode == LayoutMode.CLASSIC,
                        onClick = {
                            selectedLayoutMode = LayoutMode.CLASSIC
                            Settings.layout_mode = selectedLayoutMode
                        })
                },
                onClick = {
                    selectedLayoutMode = LayoutMode.CLASSIC
                    Settings.layout_mode = selectedLayoutMode
                })

            SettingsCard(
                title = { Text(stringResource(strings.layout_mode_tab_bar)) },
                description = { Text(stringResource(strings.layout_mode_tab_bar_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedLayoutMode == LayoutMode.TAB_BAR,
                        onClick = {
                            selectedLayoutMode = LayoutMode.TAB_BAR
                            Settings.layout_mode = selectedLayoutMode
                        })
                },
                onClick = {
                    selectedLayoutMode = LayoutMode.TAB_BAR
                    Settings.layout_mode = selectedLayoutMode
                })
        }

        PreferenceGroup(heading = stringResource(strings.close_last_session)) {
            SettingsCard(
                title = { Text(stringResource(strings.close_last_session_exit)) },
                description = { Text(stringResource(strings.close_last_session_exit_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedCloseLastSessionBehavior == CloseLastSessionBehavior.EXIT_APP,
                        onClick = {
                            selectedCloseLastSessionBehavior = CloseLastSessionBehavior.EXIT_APP
                            Settings.close_last_session_behavior = selectedCloseLastSessionBehavior
                        })
                },
                onClick = {
                    selectedCloseLastSessionBehavior = CloseLastSessionBehavior.EXIT_APP
                    Settings.close_last_session_behavior = selectedCloseLastSessionBehavior
                })

            SettingsCard(
                title = { Text(stringResource(strings.close_last_session_new)) },
                description = { Text(stringResource(strings.close_last_session_new_desc)) },
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedCloseLastSessionBehavior == CloseLastSessionBehavior.NEW_SESSION,
                        onClick = {
                            selectedCloseLastSessionBehavior = CloseLastSessionBehavior.NEW_SESSION
                            Settings.close_last_session_behavior = selectedCloseLastSessionBehavior
                        })
                },
                onClick = {
                    selectedCloseLastSessionBehavior = CloseLastSessionBehavior.NEW_SESSION
                    Settings.close_last_session_behavior = selectedCloseLastSessionBehavior
                })
        }

        // ======================================================
        // 5. Keyboard Shortcuts
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.keyboard_shortcuts)) {
            var shortcutsEnabled by remember { mutableStateOf(Settings.shortcuts_enabled) }
            var showCaptureFor by remember { mutableStateOf<ShortcutAction?>(null) }

            SettingsToggle(
                label = stringResource(strings.keyboard_shortcuts),
                description = stringResource(strings.keyboard_shortcuts_desc),
                showSwitch = true,
                default = Settings.shortcuts_enabled,
                sideEffect = {
                    Settings.shortcuts_enabled = it
                    shortcutsEnabled = it
                })


            for (action in ShortcutAction.entries) {
                val binding = Settings.getShortcutBinding(action)
                val labelRes = when (action) {
                    ShortcutAction.PASTE -> strings.shortcut_paste
                    ShortcutAction.NEW_SESSION -> strings.shortcut_new_session
                    ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session
                    ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev
                    ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next
                }
                val descRes = when (action) {
                    ShortcutAction.PASTE -> strings.shortcut_paste_desc
                    ShortcutAction.NEW_SESSION -> strings.shortcut_new_session_desc
                    ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session_desc
                    ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev_desc
                    ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next_desc
                }
                SettingsToggle(
                    isEnabled = shortcutsEnabled,
                    label = stringResource(labelRes),
                    description = "${stringResource(descRes)} (${binding.toDisplayString()})",
                    showSwitch = false,
                    default = false,
                    sideEffect = { showCaptureFor = action },
                )
            }

            if (showCaptureFor != null) {
                ShortcutCaptureDialog(
                    action = showCaptureFor!!,
                    onDismiss = { showCaptureFor = null },
                    onConfirm = { binding ->
                        Settings.setShortcutBinding(showCaptureFor!!, binding)
                        showCaptureFor = null
                    },
                )
            }

            // Number shortcut modifier — user records a key combo, only modifier flags are used
            var numberBinding by remember { mutableStateOf(Settings.getNumberShortcutBinding()) }
            var showNumberCapture by remember { mutableStateOf(false) }

            SettingsToggle(
                isEnabled = shortcutsEnabled,
                label = stringResource(strings.shortcut_switch_by_number),
                description = "${stringResource(strings.shortcut_switch_by_number_desc)} (${numberBinding.toModifierDisplayString()})",
                showSwitch = false,
                default = false,
                sideEffect = { showNumberCapture = true },
            )

            if (showNumberCapture) {
                ShortcutCaptureDialog(
                    action = ShortcutAction.PASTE, // dummy — dialog only captures keys
                    onDismiss = { showNumberCapture = false },
                    onConfirm = { binding ->
                        // Only store modifier flags; require at least one modifier
                        if (binding.ctrl || binding.shift || binding.alt) {
                            Settings.setNumberShortcutBinding(binding)
                            numberBinding = binding
                        }
                        showNumberCapture = false
                    },
                )
            }
        }

        // ======================================================
        // 6. Permissions
        // ======================================================
        PreferenceGroup(heading = stringResource(strings.permissions)) {

            SettingsToggle(
                label = stringResource(strings.all_file_access),
                description = stringResource(strings.all_file_access_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }.onFailure {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }else{
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }

                })

        }
    }
}
