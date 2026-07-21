package android.zero.studio.qstiles.domain.executor

interface CommandExecutor {
    suspend fun execute(command: String): CommandResult
}
