/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  DebugTarget: 当前要调试的宿主应用标识。
 *  由 DebugConnectionFactory.create(type, target, settings) 显式传入,
 *  不再从 host:port 反推。
 */

package com.itsaky.androidide.debugger.connection

data class DebugTarget(
    val packageName: String,
    val mainActivity: String,
    val debuggable: Boolean = true,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(mainActivity.isNotBlank()) { "mainActivity must not be blank" }
    }
}
