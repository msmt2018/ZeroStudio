package android.zero.studio.shell.common.domain.usecase

import android.zero.studio.core.utils.DateTimeUtils

class GetSaveOutputFileNameUseCase {
    operator fun invoke(saveWholeOutput: Boolean, lastCommand: String?): String {
        val currentDateTime = DateTimeUtils.getCurrentDateTime()

        val wholeOutputFileName = "aShellYou_$currentDateTime.txt"
        val lastCommandOutputFileName = lastCommand + "_" + currentDateTime + ".txt"

        return if (saveWholeOutput) wholeOutputFileName else lastCommandOutputFileName
    }
}