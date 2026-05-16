package com.itsaky.androidide.tooling.api.bsp.ipc

/**
 * Binary-first IPC frame contract carried by AIDL/Binder.
 *
 * JSON strings are used as compatibility payloads while large objects should be passed by
 * shared-file/token references (`content://`, file ids) to avoid Binder transaction limits.
 */
object BspWireProtocol {
  const val TOPIC_BUILD_EVENT = "bsp.build.event"
  const val TOPIC_PROGRESS = "bsp.progress"
  const val TOPIC_LOG = "bsp.log"

  const val STATUS_OK = "ok"
  const val STATUS_ERROR = "error"
}
