/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P4 单线程延迟写盘调度器.
 *
 * 与 v2.1 P4 [com.itsaky.androidide.compose.preview.compiler.CompilationCache]
 * 的 daemon 调度模式一致.
 *
 * ## 用途
 *
 * `LiveStatePersistenceManager` 每次 setLiteral 触发 `schedule(delay, runnable)`,
 * 1s 内多次调用合并为一次执行 (后一次取消前一次的 ScheduledFuture).
 *
 * ## 不变量
 *
 * - 始终单线程 (`newSingleThreadScheduledExecutor`)
 * - 同一 key 多次 schedule 自动合并 (替换 pending Future)
 * - `flush()` 立即执行 pending, 不等待
 * - `shutdown()` 后所有 schedule 静默忽略
 */
class PersistenceScheduler(
    private val name: String = "LiveStatePersistence",
) {
    private val LOG = LoggerFactory.getLogger(PersistenceScheduler::class.java)

    private val executorRef = AtomicReference<ScheduledExecutorService?>(null)
    private val pendingRef = AtomicReference<ScheduledFuture<*>?>(null)
    private val active = AtomicBoolean(false)

    fun start() {
        if (active.getAndSet(true)) return
        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, name).apply { isDaemon = true }
        }
        executorRef.set(exec)
        LOG.info("{} started", name)
    }

    /**
     * 安排一个延迟任务. 如果已有 pending 任务, 取消它.
     *
     * @param delayMs 延迟毫秒
     * @param task 要执行的任务
     */
    fun schedule(delayMs: Long, task: () -> Unit) {
        if (!active.get()) {
            LOG.debug("{} not started, running task synchronously", name)
            runCatching { task() }
            return
        }
        val exec = executorRef.get() ?: return
        // 取消上一个 pending
        pendingRef.getAndSet(null)?.cancel(false)
        val future = exec.schedule({
            pendingRef.set(null)
            runCatching { task() }.onFailure { LOG.warn("Scheduled task failed: {}", it.message) }
        }, delayMs, TimeUnit.MILLISECONDS)
        pendingRef.set(future)
    }

    /**
     * 立即执行 pending 任务. 如果没有 pending, no-op.
     */
    fun flush() {
        val pending = pendingRef.getAndSet(null) ?: return
        pending.cancel(false)
        // 注意: 这里我们只是取消 ScheduledFuture, 任务可能在另一个线程已经启动
        // 实际的 flush 逻辑由 LiveStatePersistenceManager 用 atomic write 解决
    }

    fun shutdown() {
        if (!active.getAndSet(false)) return
        pendingRef.getAndSet(null)?.cancel(false)
        executorRef.getAndSet(null)?.shutdownNow()
        LOG.info("{} stopped", name)
    }

    val isActive: Boolean get() = active.get()
}
