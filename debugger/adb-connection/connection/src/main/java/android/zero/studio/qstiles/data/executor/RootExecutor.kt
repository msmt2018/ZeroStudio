package android.zero.studio.qstiles.data.executor

import com.topjohnwu.superuser.Shell
import android.zero.studio.qstiles.domain.executor.CommandExecutor
import android.zero.studio.qstiles.domain.executor.CommandResult
import android.zero.studio.qstiles.domain.model.TileErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RootExecutor @Inject constructor() : CommandExecutor {

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        val filteredCommand = filterCommand(command)
        val start = System.currentTimeMillis()

        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                return@withContext CommandResult(
                    output = "Root access is not available or was denied.",
                    isSuccess = false,
                    errorType = TileErrorType.PERMISSION_DENIED,
                    durationMs = elapsed(start)
                )
            }

            val result = shell.newJob().add(filteredCommand).exec()

            val output = buildString {
                if (result.out.isNotEmpty()) append(result.out.joinToString("\n"))
                if (result.err.isNotEmpty()) {
                    if (isNotEmpty()) appendLine()
                    append(result.err.joinToString("\n"))
                }
            }

            CommandResult(
                output = output.ifBlank { "Command executed (no output)." },
                isSuccess = result.isSuccess,
                errorType = if (result.isSuccess) TileErrorType.NONE else TileErrorType.EXECUTION_FAILED,
                durationMs = elapsed(start)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandResult(
                output = "Root execution error: ${e.message}",
                isSuccess = false,
                errorType = TileErrorType.EXECUTION_FAILED,
                durationMs = elapsed(start)
            )
        }
    }

    private fun elapsed(start: Long) = System.currentTimeMillis() - start

    private fun filterCommand(command: String): String {
        return command.trim().removePrefix("adb ").removePrefix("shell ")
    }
}


