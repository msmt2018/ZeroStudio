/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.cli.jvm.compiler.jarfs

import org.jetbrains.kotlin.com.intellij.openapi.util.Couple
import org.jetbrains.kotlin.com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.kotlin.com.intellij.util.io.FileAccessorCache
import org.jetbrains.kotlin.reflection.android.AndroidSupport.isDalvik
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private typealias RandomAccessFileAndBuffer = Pair<RandomAccessFile, MappedByteBuffer>

class FastJarFileSystem private constructor(internal val unmapBuffer: MappedByteBuffer.() -> Unit) : DeprecatedVirtualFileSystem() {
    private val myHandlers: MutableMap<String, FastJarHandler> =
        ConcurrentFactoryMap.createMap { key: String -> FastJarHandler(this@FastJarFileSystem, key) }

    internal val cachedOpenFileHandles: FileAccessorCache<File, RandomAccessFileAndBuffer> =
        object : FileAccessorCache<File, RandomAccessFileAndBuffer>(20, 10) {
            @Throws(IOException::class)
            override fun createAccessor(file: File): RandomAccessFileAndBuffer {
                val randomAccessFile = RandomAccessFile(file, "r")
                return Pair(
                    randomAccessFile,
                    randomAccessFile.channel.map(FileChannel.MapMode.READ_ONLY, 0, randomAccessFile.length()),
                )
            }

            @Throws(IOException::class)
            override fun disposeAccessor(fileAccessor: RandomAccessFileAndBuffer) {
                fileAccessor.first.close()
                fileAccessor.second.unmapBuffer()
            }

            override fun isEqual(val1: File, val2: File): Boolean {
                return val1 == val2
            }
        }

    override fun getProtocol(): String = StandardFileSystems.JAR_PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? {
        val pair = splitPath(path)
        return myHandlers[pair.first]!!.findFileByPath(pair.second)
    }

    override fun refresh(asynchronous: Boolean) {}

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    fun clearHandlersCache() {
        myHandlers.clear()
        cleanOpenFilesCache()
    }

    fun cleanOpenFilesCache() {
        cachedOpenFileHandles.clear()
    }

    companion object {
        fun splitPath(path: String): Couple<String> {
            val separator = path.indexOf("!/")
            require(separator >= 0) { "Path in JarFileSystem must contain a separator: $path" }
            val localPath = path.substring(0, separator)
            val pathInJar = path.substring(separator + 2)
            return Couple.of(localPath, pathInJar)
        }

        fun createIfUnmappingPossible(): FastJarFileSystem? {
            val cleanerCallBack = prepareCleanerCallback() ?: return null
            return FastJarFileSystem(cleanerCallBack)
        }
    }
}

private val IS_PRIOR_9_JRE = System.getProperty("java.specification.version", "").startsWith("1.")

private fun prepareCleanerCallback(): ((ByteBuffer) -> Unit)? {
    return try {
        if (isDalvik()) {
            val directByteBuffer = Class.forName("java.nio.DirectByteBuffer")
            if (directByteBuffer.declaredMethods.none { it.name == "cleaner" }) {
                return null
            }
            val cleaner = directByteBuffer.getMethod("cleaner")
            cleaner.isAccessible = true

            val clean = Class.forName("sun.misc.Cleaner").getMethod("clean")
            clean.isAccessible = true

            val callback: (ByteBuffer) -> Unit = { buffer: ByteBuffer ->
                clean.invoke(cleaner.invoke(buffer))
                Unit
            }
            callback
        } else if (IS_PRIOR_9_JRE) {
            val cleaner = Class.forName("java.nio.DirectByteBuffer").getMethod("cleaner")
            cleaner.isAccessible = true

            val clean = Class.forName("sun.misc.Cleaner").getMethod("clean")
            clean.isAccessible = true

            val callback: (ByteBuffer) -> Unit = { buffer: ByteBuffer ->
                cleaner.invoke(buffer)?.let { clean.invoke(it) }
                Unit
            }
            callback
        } else {
            val unsafeClass = try {
                Class.forName("sun.misc.Unsafe")
            } catch (ex: Exception) {
                Class.forName("jdk.internal.misc.Unsafe")
            }

            val clean = unsafeClass.getMethod("invokeCleaner", ByteBuffer::class.java)
            clean.isAccessible = true

            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true

            val theUnsafe = theUnsafeField.get(null)
            val callback: (ByteBuffer) -> Unit = { buffer: ByteBuffer ->
                clean.invoke(theUnsafe, buffer)
                Unit
            }
            callback
        }
    } catch (ex: Exception) {
        null
    }
}
