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

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * v2.2 P3 源文件变更监听.
 *
 * 提供两种触发源:
 *
 * 1. **File WatchService** — 监听 [watchedFile] 所在目录, 任何 `ENTRY_MODIFY` / `ENTRY_CREATE`
 *    都产生 [SourceChangeEvent]. 仅在 [startWatch] 后生效; 适用于"preview 源文件在磁盘上"的场景.
 * 2. **手动 API** — 调用 [notifySourceChanged] 推送事件, 不依赖文件系统. 适用于"编辑器 buffer
 *    内存修改"场景 (AndroidIDE 编辑器主要走这条).
 *
 * 输出统一为 `SharedFlow<SourceChangeEvent>`, 由 [LiveEditCoordinator] 内做 300ms debounce.
 *
 * ## 线程安全
 *
 * - [events] 是 `MutableSharedFlow` (extraBufferCapacity=16, DROP_OLDEST), 多线程 emit 安全.
 * - WatchService 跑在 daemon 线程, 关闭时通过 [stopWatch] 中断.
 * - 重复 [startWatch] 会自动关闭上一个 watch.
 *
 * ## 已知限制
 *
 * - WatchService 在某些 Android 设备 (尤其低 RAM) 上可能被系统限制; 此时 `startWatch` 返回 false,
 *   上层应退化到只用 [notifySourceChanged].
 * - 监听粒度: `ENTRY_MODIFY` 可能因编辑器"保存-清空-重写"产生 2-3 次事件; debounce 在 Coordinator 处理.
 */
class SourceChangeWatcher {

    private val LOG = LoggerFactory.getLogger(SourceChangeWatcher::class.java)

    private val _events = MutableSharedFlow<SourceChangeEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SourceChangeEvent> = _events.asSharedFlow()

    private val _resourceEvents = MutableSharedFlow<ResourceChangeEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val resourceEvents: SharedFlow<ResourceChangeEvent> = _resourceEvents.asSharedFlow()

    private val watchingRef = AtomicReference<WatchSession?>(null)
    private val resourceWatchRef = AtomicReference<ResourceWatchSession?>(null)
    private val running = AtomicBoolean(false)

    /**
     * 启动对 [file] 的 WatchService 监听.
     *
     * @return true 成功, false 失败 (例如 WatchService 不可用).
     * 重复调用会自动停止上一个 watch session.
     */
    fun startWatch(file: File): Boolean {
        stopWatch()

        if (!file.exists()) {
            LOG.warn("startWatch: file does not exist: {}", file.absolutePath)
            return false
        }

        val watchService: WatchService = try {
            FileSystems.getDefault().newWatchService()
        } catch (e: Throwable) {
            LOG.warn("WatchService unavailable: {}", e.message)
            return false
        }

        val parentPath: Path = file.absoluteFile.parentFile.toPath()
        val key: WatchKey = try {
            parentPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
            )
        } catch (e: Throwable) {
            LOG.warn("Failed to register watch on {}: {}", parentPath, e.message)
            runCatching { watchService.close() }
            return false
        }

        val session = WatchSession(watchService, key, file.absolutePath)
        watchingRef.set(session)
        running.set(true)

        val thread = Thread({
            try {
                while (running.get()) {
                    val polled = watchService.take() // blocks
                    if (!running.get()) break
                    for (event in polled.pollEvents()) {
                        if (!running.get()) break
                        handleEvent(event, file.absolutePath)
                    }
                    if (!polled.reset()) {
                        LOG.warn("WatchKey no longer valid, stopping watch")
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Throwable) {
                LOG.warn("WatchService loop failed: {}", e.message)
            } finally {
                runCatching { watchService.close() }
            }
        }, "SourceChangeWatcher").apply {
            isDaemon = true
            start()
        }
        session.threadRef.set(thread)

        LOG.info("Watching: {}", file.absolutePath)
        return true
    }

    /**
     * 停止 WatchService. 已经 stop 后再次调用 no-op.
     */
    fun stopWatch() {
        val session = watchingRef.getAndSet(null) ?: return
        running.set(false)
        runCatching { session.watchService.close() }
        session.threadRef.get()?.interrupt()
        LOG.info("Stopped watching: {}", session.targetPath)
    }

    /**
     * 手动推送一个 source change 事件. 不依赖 WatchService, 可由编辑器 buffer 变更调用.
     */
    fun notifySourceChanged(sourceText: String, path: String? = null) {
        val hash = fnv1aHash(sourceText)
        _events.tryEmit(SourceChangeEvent(sourceText = sourceText, sourcePath = path, sourceHash = hash, manual = true))
    }

    // ===================================================================
    // v2.2 P8 资源监听
    // ===================================================================

    /**
     * v2.2 P8: 启动对资源目录的 WatchService 监听.
     *
     * 递归注册 [resourcesDir] 下所有子目录, 只 emit 4 个标准资源子目录的事件:
     * - `drawable/` (xml 矢量/selector)
     * - `values/` (xml string/color/style/dimen/...)
     * - `color/` (xml color selector)
     * - `mipmap/` (png launcher icon)
     *
     * 文件过滤: `*.xml` 和 `*.png`. 其他文件被忽略.
     *
     * 多次调用会停止上一个 session, 只保留最新. 与 [startWatch] 互不影响.
     *
     * @return true 成功, false 失败 (WatchService 不可用 / 目录不存在).
     */
    fun startWatchResources(resourcesDir: File): Boolean {
        stopWatchResources()

        if (!resourcesDir.exists() || !resourcesDir.isDirectory) {
            LOG.warn("startWatchResources: dir does not exist: {}", resourcesDir.absolutePath)
            return false
        }

        val watchService: WatchService = try {
            FileSystems.getDefault().newWatchService()
        } catch (e: Throwable) {
            LOG.warn("WatchService unavailable for resources: {}", e.message)
            return false
        }

        val registered = mutableListOf<WatchKey>()
        val ok = walkAndRegister(resourcesDir.toPath(), watchService, registered)
        if (registered.isEmpty()) {
            LOG.warn("No standard resource subdirs found under {}", resourcesDir.absolutePath)
            runCatching { watchService.close() }
            return false
        }
        if (!ok) {
            registered.forEach { runCatching { it.cancel() } }
            runCatching { watchService.close() }
            return false
        }

        val session = ResourceWatchSession(watchService, registered.toList(), resourcesDir.absolutePath)
        resourceWatchRef.set(session)
        val localRunning = AtomicBoolean(true)
        session.runningRef.set(localRunning)

        val thread = Thread({
            try {
                while (localRunning.get()) {
                    val polled = watchService.take() // blocks
                    if (!localRunning.get()) break
                    for (event in polled.pollEvents()) {
                        if (!localRunning.get()) break
                        handleResourceEvent(event, resourcesDir)
                    }
                    if (!polled.reset()) {
                        LOG.warn("Resource WatchKey no longer valid, stopping watch")
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Throwable) {
                LOG.warn("Resource WatchService loop failed: {}", e.message)
            } finally {
                runCatching { watchService.close() }
            }
        }, "SourceChangeWatcher-Resources").apply {
            isDaemon = true
            start()
        }
        session.threadRef.set(thread)

        LOG.info("Watching resources: {} ({} subdirs)", resourcesDir.absolutePath, registered.size)
        return true
    }

    /**
     * 停止资源 WatchService. 已经 stop 后再次调用 no-op.
     */
    fun stopWatchResources() {
        val session = resourceWatchRef.getAndSet(null) ?: return
        session.runningRef.set(false)
        runCatching { session.watchService.close() }
        session.keys.forEach { runCatching { it.cancel() } }
        session.threadRef.get()?.interrupt()
        LOG.info("Stopped watching resources: {}", session.resourcesDir)
    }

    /**
     * v2.2 P8: 手动推送资源变更事件. 不依赖 WatchService.
     *
     * 用于 IDE 编辑器直接修改 res/ 下的资源 (无 fs watch).
     */
    fun notifyResourceChanged(file: File) {
        val pathHash = fnv1aHash(file.absolutePath)
        _resourceEvents.tryEmit(
            ResourceChangeEvent(
                filePath = file.absolutePath,
                pathHash = pathHash,
                manual = true,
            )
        )
    }

    private fun walkAndRegister(
        dir: Path,
        watchService: WatchService,
        registered: MutableList<WatchKey>,
    ): Boolean {
        // 先注册当前 dir (如果不是根)
        val isStandardSubdir = isStandardResourceSubdir(dir)
        if (isStandardSubdir) {
            try {
                val key = dir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                )
                registered.add(key)
            } catch (e: Throwable) {
                LOG.warn("Failed to register {}: {}", dir, e.message)
                return false
            }
        }
        // 递归子目录
        return try {
            val stream = java.nio.file.Files.list(dir)
            stream.use { paths ->
                paths.forEach { child ->
                    if (java.nio.file.Files.isDirectory(child)) {
                        if (!walkAndRegister(child, watchService, registered)) {
                            return false
                        }
                    }
                }
            }
            true
        } catch (e: Throwable) {
            LOG.warn("walkAndRegister failed for {}: {}", dir, e.message)
            false
        }
    }

    private fun isStandardResourceSubdir(path: Path): Boolean {
        val name = path.fileName?.toString()?.lowercase() ?: return false
        return name in STANDARD_RESOURCE_SUBDIRS
    }

    private fun isResourceFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".xml") || lower.endsWith(".png")
    }

    private fun handleResourceEvent(event: WatchEvent<*>, resourcesRoot: File) {
        val changed = event.context() as? Path ?: return
        val changedName = changed.toString()

        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            LOG.debug("Resource WatchService overflow, skipping")
            return
        }

        if (!isResourceFile(changedName)) {
            return
        }

        val file = File(resourcesRoot, changed.toString())
        if (!file.exists()) return

        val pathHash = fnv1aHash(file.absolutePath)
        _resourceEvents.tryEmit(
            ResourceChangeEvent(
                filePath = file.absolutePath,
                pathHash = pathHash,
                manual = false,
            )
        )
    }

    private data class ResourceWatchSession(
        val watchService: WatchService,
        val keys: List<WatchKey>,
        val resourcesDir: String,
    ) {
        val threadRef = AtomicReference<Thread?>(null)
        val runningRef = AtomicReference<AtomicBoolean>(null)
    }

    val isWatching: Boolean get() = watchingRef.get() != null
    val isWatchingResources: Boolean get() = resourceWatchRef.get() != null

    private fun handleEvent(event: WatchEvent<*>, targetPath: String) {
        val changed = event.context() as? Path ?: return
        val changedName = changed.toString()
        // WatchService 在父目录注册, 事件是相对于 parentPath; 比较 basename
        val targetFile = File(targetPath)
        if (changedName != targetFile.name) return

        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            LOG.debug("WatchService overflow, skipping")
            return
        }

        runCatching { targetFile.readText() }.getOrNull()?.let { text ->
            val hash = fnv1aHash(text)
            _events.tryEmit(SourceChangeEvent(sourceText = text, sourcePath = targetPath, sourceHash = hash, manual = false))
        }
    }

    private data class WatchSession(
        val watchService: WatchService,
        val key: WatchKey,
        val targetPath: String,
    ) {
        val threadRef = AtomicReference<Thread?>(null)
    }

    companion object {
        /**
         * v2.2 P8 监听的标准资源子目录. 大小写不敏感.
         */
        val STANDARD_RESOURCE_SUBDIRS = setOf("drawable", "values", "color", "mipmap")

        /**
         * 32-bit FNV-1a hash. 用于把 source text 映射到 32-bit int (与 stats 一致).
         */
        fun fnv1aHash(text: String): Int {
            var hash = -2128831035 // 0x811c9dc5
            val bytes = text.toByteArray(Charsets.UTF_8)
            for (b in bytes) {
                hash = hash xor (b.toInt() and 0xff)
                hash *= 16777619 // 0x01000193
            }
            return hash
        }
    }
}

/**
 * 单个 source change 事件.
 *
 * @param sourceText 完整源文本 (Editor buffer 或文件内容)
 * @param sourcePath 可选, 仅 [SourceChangeWatcher] WatchService 触发时存在
 * @param sourceHash FNV-1a hash of sourceText
 * @param manual true 表示来自 [SourceChangeWatcher.notifySourceChanged] 手动 API
 */
data class SourceChangeEvent(
    val sourceText: String,
    val sourcePath: String?,
    val sourceHash: Int,
    val manual: Boolean,
)

/**
 * v2.2 P8 资源变更事件.
 *
 * 与 [SourceChangeEvent] 不同: 资源没有"源文本",只有文件路径.
 * 资源变化后 [LiveEditCoordinator] 调 [LiveEditCoordinator.forceReload] 沿用最近一次 sourceText 重编.
 *
 * @param filePath 资源文件的绝对路径
 * @param pathHash FNV-1a hash of filePath, 用于 300ms debounce 期间去重
 * @param manual true 表示来自 [SourceChangeWatcher.notifyResourceChanged] 手动 API
 */
data class ResourceChangeEvent(
    val filePath: String,
    val pathHash: Int,
    val manual: Boolean,
)
