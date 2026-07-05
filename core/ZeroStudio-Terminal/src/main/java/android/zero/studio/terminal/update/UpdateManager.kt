package android.zero.studio.termux.update

import android.zero.studio.termux.libcommons.application
import android.zero.studio.termux.libcommons.child
import android.zero.studio.termux.libcommons.createFileIfNot
import android.zero.studio.termux.libcommons.localBinDir
import java.io.File

class UpdateManager {
    fun onUpdate(){
        val initFile: File = localBinDir().child("init-host")
        if(initFile.exists()){
            initFile.delete()
        }

        if (initFile.exists().not()){
            initFile.createFileIfNot()
            initFile.writeText(application!!.assets.open("init-host.sh").bufferedReader().use { it.readText() })
        }

        val initFilex: File = localBinDir().child("init")
        if(initFilex.exists()){
            initFilex.delete()
        }

        if (initFilex.exists().not()){
            initFilex.createFileIfNot()
            initFilex.writeText(application!!.assets.open("init.sh").bufferedReader().use { it.readText() })
        }
    }
}