package android.zero.studio.qstiles.domain.model

data class RunningTileState(
    val tileId: Int,
    val startTime: Long = System.currentTimeMillis()
)
