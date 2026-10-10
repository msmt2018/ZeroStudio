package com.itsaky.androidide.logging

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.Context
import com.itsaky.androidide.logging.encoder.IDELogFormatLayout

/**
 * 支持从源头直接暂停的 Logback Appender。
 */
class LifecycleAwareAppender
@JvmOverloads
constructor(
    private val requireLifecycleState: Lifecycle.State = Lifecycle.State.CREATED,
    var consumer: ((String) -> Unit)? = null,
) : AppenderBase<ILoggingEvent>(), LifecycleEventObserver {

  private var currentState: Lifecycle.State? = null
  private val logLayout = IDELogFormatLayout()

  /** 关键：源头直接静默开关，暂停时在最开始即返回，避免一切内存分配与格式化计算 */
  @Volatile var isSourcePaused: Boolean = false

  init {
    name = "LifecycleAwareAppender"
    logLayout.isOmitMessage = true
  }

  fun attachTo(lifecycleOwner: LifecycleOwner) = attachTo(lifecycleOwner.lifecycle)
  fun attachTo(lifecycle: Lifecycle) = lifecycle.addObserver(this)
  fun detachFrom(lifecycleOwner: LifecycleOwner) = detachFrom(lifecycleOwner.lifecycle)
  fun detachFrom(lifecycle: Lifecycle) = lifecycle.removeObserver(this)

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    this.currentState = if (event == Lifecycle.Event.ON_ANY) null else event.targetState
  }

  override fun isStarted(): Boolean {
    return (super.isStarted() &&
        currentState?.isAtLeast(this.requireLifecycleState) == true &&
        consumer != null)
  }

  override fun start() {
    this.logLayout.start()
    super.start()
  }

  override fun stop() {
    super.stop()
    this.logLayout.stop()
    this.consumer = null
  }

  override fun setContext(context: Context?) {
    super.setContext(context)
    this.logLayout.context = context
  }

  override fun append(eventObject: ILoggingEvent?) {
    if (eventObject == null || !isStarted || isSourcePaused) {
      return // 源头暂停，零对象分配
    }

    val prefix = logLayout.doLayout(eventObject)
    eventObject.formattedMessage.split('\n').forEach { consumer?.invoke("$prefix $it") }
  }
}