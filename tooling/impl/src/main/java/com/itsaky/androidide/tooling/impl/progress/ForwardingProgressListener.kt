package com.itsaky.androidide.tooling.impl.progress

import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener

/** No-op progress listener after legacy tooling RPC removal. */
class ForwardingProgressListener : ProgressListener {
  override fun statusChanged(event: ProgressEvent?) = Unit
}
