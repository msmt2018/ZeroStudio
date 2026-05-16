package com.itsaky.androidide.tooling.impl

import java.io.OutputStream

/** No-op output stream after legacy tooling RPC removal. */
class LoggingOutputStream : OutputStream() {
  override fun write(b: Int) = Unit
}
