package com.itsaky.androidide.debugger.root

/**
 * Root 授权状态。
 *
 * 由 [RootManager] 探测后产出，供状态通道系统与 Root 卡片消费。
 */
sealed class RootState {
    /** 尚未探测。 */
    data object Idle : RootState()

    /** 探测中。 */
    data object Probing : RootState()

    /** 已授权，[manager] 为当前生效的 Root 管理器。 */
    data class Granted(val manager: RootManagerType) : RootState()

    /** 探测完成但无 root。 */
    data object Denied : RootState()

    /** 探测过程出错。 */
    data class Error(val message: String) : RootState()
}

/**
 * 已支持的 Root 管理器类型。
 */
enum class RootManagerType(val displayName: String) {
    STANDARD_SU("标准 su"),
    KERNEL_SU("KernelSU"),
    MAGISK("Magisk"),
    APATCH("APatch"),
}