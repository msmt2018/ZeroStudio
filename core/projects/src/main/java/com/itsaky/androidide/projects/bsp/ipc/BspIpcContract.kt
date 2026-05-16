package com.itsaky.androidide.projects.bsp.ipc

/** Shared constants for project-layer consumption of Binder+BSP transport. */
object BspIpcContract {
  const val SERVICE_ACTION = "com.itsaky.androidide.bsp.BIND_SESSION_SERVICE"
  const val EXTRA_ROOT_URI = "extra_root_uri"
  const val EXTRA_OPTIONS_JSON = "extra_options_json"
}
