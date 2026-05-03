package com.zerostudio.jdwp

class JdwpSymbolRepository(private val client: JdwpClient) {
  private val classCache = mutableMapOf<String, JdwpClassRef>()
  private val methodCache = mutableMapOf<Pair<Long, String>, JdwpMethodRef>()

  fun resolveClass(signature: String): JdwpClassRef {
    return classCache.getOrPut(signature) {
      val info = client.classesBySignature(signature).firstOrNull() ?: error("Class not found: $signature")
      JdwpClassRef(info.typeId, signature)
    }
  }

  fun resolveMethod(classRef: JdwpClassRef, methodName: String): JdwpMethodRef {
    return methodCache.getOrPut(classRef.id.raw to methodName) {
      val method = client.methods(classRef.id).firstOrNull { it.name == methodName }
        ?: error("Method not found: ${classRef.signature}#$methodName")
      JdwpMethodRef(method.id, method.name, method.signature)
    }
  }
}
