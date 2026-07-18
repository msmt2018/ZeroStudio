package android.zero.studio.shell.otg_adb_shell.domain.repository

import android.zero.studio.adblib.AdbConnection
import android.zero.studio.shell.common.domain.model.OutputLine
import kotlinx.coroutines.flow.Flow

interface OtgRepository {
    fun searchDevices()
    fun disconnect()
    fun unRegister()
    fun runOtgCommand(command: String): Flow<OutputLine>
    fun stopCommand()
    
    // Added for file browser support
    fun isConnected(): Boolean
    fun getAdbConnection(): AdbConnection?
}