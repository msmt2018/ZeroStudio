package com.termix.libcommons

import android.content.Context
import java.io.File
import com.termix.core.BuildConfig

private fun getFilesDir(): File{
    return if (application == null){
        if (BuildConfig.DEBUG){
            File("/data/data/com.termix.app.debug/files")
        }else{
            File("/data/data/com.termix.app/files")
        }
    }else{
        application!!.filesDir
    }
}

fun localDir(): File {
    return File(getFilesDir().parentFile, "local").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineDir(): File{
    return localDir().child("alpine").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun alpineHomeDir(): File{
    return alpineDir().child("root").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun archDir(): File{
    return localDir().child("arch").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun archHomeDir(): File{
    return archDir().child("root").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun localBinDir(): File {
    return localDir().child("bin").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun localLibDir(): File {
    return localDir().child("lib").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
}

fun File.child(fileName:String):File {
    return File(this,fileName)
}

fun File.createFileIfNot():File{
    if (exists().not()){
        createNewFile()
    }
    return this
}
