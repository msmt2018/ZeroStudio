@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package android.zero.studio.shell.wifi_adb_shell.presentation.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.zero.studio.R
import android.zero.studio.core.presentation.components.haptic.withHaptic
import android.zero.studio.core.utils.isConnectedToWifi
import android.zero.studio.core.utils.showToast
import android.zero.studio.navigation.LocalNavController
import android.zero.studio.navigation.NavRoutes
import android.zero.studio.shell.common.presentation.components.dialog.ConnectedDeviceDialog
import android.zero.studio.shell.common.presentation.screens.BaseShellScreen
import android.zero.studio.shell.common.presentation.viewmodel.ShellViewModel
import android.zero.studio.shell.file_browser.domain.model.ConnectionMode
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.presentation.component.dialog.DeviceDisconnectedDialog
import android.zero.studio.shell.wifi_adb_shell.presentation.viewmodel.WifiAdbViewModel

@Composable
fun WifiAdbScreen(
    shellViewModel: ShellViewModel = hiltViewModel(),
    wifiAdbViewModel: WifiAdbViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val res = LocalResources.current
    var showConnectedDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var showDeviceDisconnectedDialog by rememberSaveable { mutableStateOf(false) }

    val wifiAdbState by wifiAdbViewModel.state.collectAsState()
    val currentDevice by wifiAdbViewModel.currentDevice.collectAsState()

    val isConnected = wifiAdbState is WifiAdbState.Connected
    val connectedDeviceName = if (isConnected) {
        currentDevice?.deviceName ?: res.getString(R.string.none)
    } else {
        res.getString(R.string.none)
    }
    val lastConnectedDevice by wifiAdbViewModel.lastConnectedDevice.collectAsState()

    LaunchedEffect(wifiAdbState) {
        when (wifiAdbState) {
            is WifiAdbState.Disconnected -> {
                showDeviceDisconnectedDialog = true
            }

            is WifiAdbState.Connected -> {}

            else -> {}
        }
    }

    val modeButtonText = stringResource(R.string.wifi_adb)
    val modeButtonOnClick: () -> Unit = {
        showConnectedDeviceDialog = true
    }

    val runCommandIfPermissionGranted: () -> Unit = {
        shellViewModel.runWifiAdbCommand()
    }

    var isReconnecting by rememberSaveable { mutableStateOf(false) }
    val navController = LocalNavController.current

    BaseShellScreen(
        modeButtonText = modeButtonText,
        modeButtonOnClick = modeButtonOnClick,
        runCommandIfPermissionGranted = runCommandIfPermissionGranted,
        extraButtonContent = {
            if (isConnected) {
                IconButton(
                    onClick = withHaptic(HapticFeedbackType.VirtualKey) {
                        val deviceAddr = currentDevice?.let { "${it.ip}:${it.port}" } ?: ""
                        val isOwn = currentDevice?.isOwnDevice ?: false
                        navController.navigate(
                            NavRoutes.FileBrowserScreen(
                                deviceAddress = deviceAddr,
                                connectionMode = ConnectionMode.WIFI_ADB,
                                isOwnDevice = isOwn
                            )
                        )
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_directory),
                        contentDescription = stringResource(R.string.file_browser),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = withHaptic(HapticFeedbackType.VirtualKey) {
                        if (!context.isConnectedToWifi()) {
                            showToast(
                                context,
                                res.getString(R.string.no_wifi_connection)
                            )
                            return@withHaptic
                        }

                        if (lastConnectedDevice == null) {
                            showToast(
                                context,
                                res.getString(R.string.error)
                            )
                            return@withHaptic
                        }

                        lastConnectedDevice?.let { device ->
                            isReconnecting = true
                            wifiAdbViewModel.reconnectToDeviceWithCallback(
                                device = device,
                                onSuccess = {
                                    isReconnecting = false
                                    showToast(
                                        context,
                                        res.getString(R.string.reconnect_success),
                                    )
                                },
                                onFailure = { requiresPairing ->
                                    isReconnecting = false
                                    val message = if (requiresPairing) {
                                        res.getString(R.string.reconnect_failed_requires_pairing)
                                    } else {
                                        res.getString(R.string.reconnect_failed)
                                    }
                                    showToast(context, message)
                                }
                            )
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    enabled = !isReconnecting
                ) {
                    if (isReconnecting) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.reconnect),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    )

    if (showConnectedDeviceDialog) {
        ConnectedDeviceDialog(
            connectedDevice = connectedDeviceName,
            onDismiss = { showConnectedDeviceDialog = false },
            showModeSwitchButton = false
        )
    }

    if (showDeviceDisconnectedDialog) {
        DeviceDisconnectedDialog(
            onDismiss = {
                showDeviceDisconnectedDialog = false
                WifiAdbConnection.updateState(WifiAdbState.Idle)
            }
        )
    }
}
