/*
 *  ZeroStudio IDE - ide-log-plugin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.zerostudio.logplugin.plugin.util

import java.io.File
import java.util.jar.JarFile

/**
 * Small helper for working with the AAR archive.
 */
object PackageUtils {

    /**
     * Extract the classes.jar from an AAR and return its path on disk.
     * The caller is responsible for deleting the extracted file.
     */
    @JvmStatic
    fun extractClassesJar(aar: File, destDir: File): File {
        require(aar.exists()) { "AAR does not exist: $aar" }
        require(aar.name.endsWith(".aar")) { "Not an AAR: $aar" }
        destDir.mkdirs()
        val out = File(destDir, "ide-log-plugin-classes.jar")
        JarFile(aar).use { jar ->
            val entry = jar.getJarEntry("classes.jar") ?: error("classes.jar missing in AAR")
            jar.getInputStream(entry).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }
}
