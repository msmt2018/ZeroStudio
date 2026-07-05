/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ConnectionRetryPolicy: 单方案内重试,指数退避。
 *  5 种实现各自在 resolve/connect/attach 内部包 retry {} 调用;
 *  不跨方案降级,失败上报 ConnectionError 由 UI 决定让用户切方案。
 */

package com.itsaky.androidide.debugger.connection

import kotlinx.coroutines.delay
import kotlin.math.min

class ConnectionRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500L,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 4_000L,
) {

    /**
     * @param block 一次尝试,返回 Result.success 视为成功,Result.failure 视为失败
     * @return 第一次成功的 Result,或最后一次失败的 Result
     */
    suspend fun <T> retry(
        block: suspend (attempt: Int) -> Result<T>,
    ): Result<T> {
        var lastFailure: Result.Failure<T>? = null
        for (attempt in 1..maxAttempts) {
            val r = block(attempt)
            if (r.isSuccess) return r
            lastFailure = r as Result.Failure<T>
            // 最后一次失败不再 delay
            if (attempt < maxAttempts) {
                val backoff = computeBackoff(attempt)
                delay(backoff)
            }
        }
        return lastFailure ?: Result.failure(IllegalStateException("retry: no attempts"))
    }

    private fun computeBackoff(attempt: Int): Long {
        // attempt 1 -> initial, 2 -> initial*mult, 3 -> initial*mult^2, ...
        val raw = initialDelayMs * Math.pow(multiplier, (attempt - 1).toDouble())
        return min(raw.toLong(), maxDelayMs)
    }
}
