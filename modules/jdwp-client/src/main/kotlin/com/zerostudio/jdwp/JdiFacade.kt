package com.zerostudio.jdwp

class JdwpSymbolRepository(private val client: JdwpClient) {
  private val classCache = mutableMapOf<String, JdwpClassRef>()
  private val methodCache = mutableMapOf<Triple<Long, String, String?>, JdwpMethodRef>()

  fun clearCaches() { classCache.clear(); methodCache.clear() }

  fun resolveClass(signature: String): JdwpClassRef = classCache.getOrPut(signature) {
    val info = client.classesBySignature(signature).firstOrNull() ?: error("Class not found: $signature")
    JdwpClassRef(info.typeId, signature)
  }

  fun resolveMethod(classRef: JdwpClassRef, methodName: String, signature: String? = null): JdwpMethodRef {
    return methodCache.getOrPut(Triple(classRef.id.raw, methodName, signature)) {
      val method = client.methodsByName(classRef.id, methodName, signature).firstOrNull()
        ?: error("Method not found: ${classRef.signature}#$methodName")
      JdwpMethodRef(method.id, method.name, method.signature)
    }
  }
}
