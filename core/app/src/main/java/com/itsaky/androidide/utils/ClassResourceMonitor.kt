package com.itsaky.androidide.utils

import android.os.Debug
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import org.slf4j.Logger

/**
 * Runtime in-process usage monitor grouped by class tag (or logger name).
 *
 * 说明：Android/JVM 在未使用 JVMTI/字节码插桩时，无法直接精确拿到“某个 .kt/.class 当前占用内存”。
 * 这个监控器采用“打点区间统计”方案：在 begin/end（或 [trace]）区间内累计 CPU 时间和内存变化，
 * 以类名/Logger 名作为标签，方便精细定位热点。
 */
object ClassResourceMonitor {

  data class Sample(
      val tag: String,
      val hits: Long,
      val totalCpuNanos: Long,
      val avgCpuNanos: Long,
      val totalMemoryDeltaBytes: Long,
      val avgMemoryDeltaBytes: Long,
      val peakMemoryDeltaBytes: Long,
      val lastUpdatedUptimeMillis: Long,
  )

  data class Token internal constructor(
      val id: Long,
      val tag: String,
      val startedAtCpuNanos: Long,
      val startedAtUsedBytes: Long,
  )

  private data class MutableSample(
      val tag: String,
      val hits: AtomicLong = AtomicLong(0L),
      val totalCpuNanos: AtomicLong = AtomicLong(0L),
      val totalMemoryDeltaBytes: AtomicLong = AtomicLong(0L),
      val peakMemoryDeltaBytes: AtomicLong = AtomicLong(0L),
      val lastUpdatedUptimeMillis: AtomicLong = AtomicLong(0L),
  )

  private val nextTokenId = AtomicLong(1L)
  private val activeTokens = ConcurrentHashMap<Long, Token>()
  private val samples = ConcurrentHashMap<String, MutableSample>()

  @JvmStatic
  fun begin(tag: String): Token {
    val token =
        Token(
            id = nextTokenId.getAndIncrement(),
            tag = normalizeTag(tag),
            startedAtCpuNanos = Debug.threadCpuTimeNanos(),
            startedAtUsedBytes = usedHeapBytes(),
        )
    activeTokens[token.id] = token
    return token
  }

  @JvmStatic
  fun begin(logger: Logger): Token = begin(logger.name)

  @JvmStatic
  fun begin(clazz: Class<*>): Token = begin(clazz.name)

  @JvmStatic
  fun end(token: Token) {
    activeTokens.remove(token.id) ?: return

    val cpuDelta = (Debug.threadCpuTimeNanos() - token.startedAtCpuNanos).coerceAtLeast(0L)
    val memDelta = usedHeapBytes() - token.startedAtUsedBytes

    val sample = samples.getOrPut(token.tag) { MutableSample(tag = token.tag) }
    sample.hits.incrementAndGet()
    sample.totalCpuNanos.addAndGet(cpuDelta)
    sample.totalMemoryDeltaBytes.addAndGet(memDelta)
    sample.lastUpdatedUptimeMillis.set(SystemClock.uptimeMillis())

    val memAbs = kotlin.math.abs(memDelta)
    while (true) {
      val current = sample.peakMemoryDeltaBytes.get()
      if (memAbs <= current || sample.peakMemoryDeltaBytes.compareAndSet(current, max(current, memAbs))) {
        break
      }
    }
  }

  @JvmStatic
  inline fun <T> trace(tag: String, block: () -> T): T {
    val token = begin(tag)
    return try {
      block()
    } finally {
      end(token)
    }
  }

  @JvmStatic
  inline fun <T> trace(logger: Logger, block: () -> T): T = trace(logger.name, block)

  @JvmStatic
  inline fun <T> trace(clazz: Class<*>, block: () -> T): T = trace(clazz.name, block)

  @JvmStatic
  fun snapshot(): List<Sample> {
    return samples.values
        .map {
          val hits = it.hits.get().coerceAtLeast(1L)
          val cpu = it.totalCpuNanos.get()
          val mem = it.totalMemoryDeltaBytes.get()
          Sample(
              tag = it.tag,
              hits = it.hits.get(),
              totalCpuNanos = cpu,
              avgCpuNanos = cpu / hits,
              totalMemoryDeltaBytes = mem,
              avgMemoryDeltaBytes = mem / hits,
              peakMemoryDeltaBytes = it.peakMemoryDeltaBytes.get(),
              lastUpdatedUptimeMillis = it.lastUpdatedUptimeMillis.get(),
          )
        }
        .sortedByDescending { it.totalCpuNanos }
  }

  @JvmStatic
  fun reset() {
    activeTokens.clear()
    samples.clear()
  }

  private fun usedHeapBytes(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
  }

  private fun normalizeTag(tag: String): String {
    val clean = tag.trim()
    return if (clean.isEmpty()) "unknown" else clean
  }
}
