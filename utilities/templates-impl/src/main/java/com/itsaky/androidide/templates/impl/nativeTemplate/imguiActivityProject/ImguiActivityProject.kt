/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.templates.impl.nativeTemplate.imguiActivityProject

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.NdkVersion
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.defaultAppModuleWithNdk
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.projectCmakeVersionParameter
import com.itsaky.androidide.templates.projectNdkVersionParameter
import com.itsaky.androidide.templates.useCmakeParameter
import com.itsaky.androidide.templates.useNdkParameter
import java.io.File

/**
 * ImGui Native Build Project Template.
 * @author android_zero
 */
fun imguiActivityProject(): ProjectTemplate = baseProjectImpl(
    useNdk = useNdkParameter { default = true },
    ndkVersion = projectNdkVersionParameter { default = NdkVersion.R27A },
    useCmake = useCmakeParameter { default = false }
) {
    templateName = R.string.title_template_native_build_imgui
    thumb = R.drawable.template_ndk_native_imgui

    defaultAppModuleWithNdk(addAndroidX = false) {
        
        postRecipe = commonPostRecipe {
            // 覆盖生成 ImGui 专用的 AndroidManifest.xml
            save(imguiManifestXml(data.packageName, "MainActivity"), manifestFile())
        }

        recipe = createRecipe {
            val mainDir = File(data.projectDir, "src/main")
            mainDir.mkdirs()

           // 解压jni源码压缩包
            extractAssetWithDialog(
                path = "template/imgui/jni/imgui-NdkSource-Jni.tar.xz",
                destDir = mainDir,
                stripPaths = 1,
                dialogTitle = "Extracting NDK Sources",
                dialogMessage = "Please wait while the ImGui NDK sources are being extracted..."
            )

            val activityClass = "MainActivity"
            sources {
                if (data.language == Language.Kotlin) {
                    writeKtSrc(data.packageName, activityClass, source = imguiActivitySrcKt(data.packageName, activityClass))
                } else {
                    writeJavaSrc(data.packageName, activityClass, source = imguiActivitySrcJava(data.packageName, activityClass))
                }
            }

            res {
                putStringRes("app_name", data.appName ?: "ImGui App")
            }
        }
    }
}

internal fun imguiActivitySrcKt(packageName: String, activityClass: String): String = """
package ${packageName}

import android.app.NativeActivity
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.LinkedBlockingQueue

class ${activityClass} : NativeActivity() {
    private val unicodeCharacterQueue = LinkedBlockingQueue<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun showSoftInput() {
        val systemService = getSystemService(INPUT_METHOD_SERVICE)
        if (systemService != null) {
            val inputMethodManager = systemService as InputMethodManager
            inputMethodManager.showSoftInput(window.decorView, 0)
            return
        }
        throw NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager")
    }

    fun hideSoftInput() {
        val systemService = getSystemService(INPUT_METHOD_SERVICE)
        if (systemService != null) {
            val inputMethodManager = systemService as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            return
        }
        throw NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            unicodeCharacterQueue.offer(event.getUnicodeChar(event.metaState))
        }
        return super.dispatchKeyEvent(event)
    }

    fun pollUnicodeChar(): Int {
        return unicodeCharacterQueue.poll() ?: 0
    }
}
""".trimIndent()

internal fun imguiActivitySrcJava(packageName: String, activityClass: String): String = """
package ${packageName};

import android.app.NativeActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import java.util.concurrent.LinkedBlockingQueue;

public final class ${activityClass} extends NativeActivity {
    private LinkedBlockingQueue<Integer> unicodeCharacterQueue = new LinkedBlockingQueue<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public final void showSoftInput() {
        Object systemService = getSystemService("input_method");
        if (systemService != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) systemService;
            inputMethodManager.showSoftInput(getWindow().getDecorView(), 0);
            return;
        }
        throw new NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager");
    }

    public final void hideSoftInput() {
        Object systemService = getSystemService("input_method");
        if (systemService != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) systemService;
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            return;
        }
        throw new NullPointerException("null cannot be cast to non-null type android.view.inputmethod.InputMethodManager");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == 0) {
            this.unicodeCharacterQueue.offer(Integer.valueOf(event.getUnicodeChar(event.getMetaState())));
        }
        return super.dispatchKeyEvent(event);
    }

    public final int pollUnicodeChar() {
        Integer poll = this.unicodeCharacterQueue.poll();
        if (poll == null) {
            return 0;
        }
        return poll.intValue();
    }
}
""".trimIndent()

internal fun AndroidModuleTemplateBuilder.imguiManifestXml(packageName: String, activityClass: String) = """
<?xml version='1.0' encoding='utf-8'?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

     <application
          android:allowBackup="true"
          android:icon="@mipmap/ic_launcher"
          android:label="@string/app_name"
          android:largeHeap="true"
          android:hasCode="true"
          android:resizeableActivity="true">

          <activity
               android:name="${packageName}.${activityClass}"
               android:exported="true"
               android:theme="@android:style/Theme.NoTitleBar.Fullscreen">
               <meta-data
                    android:name="android.app.lib_name"
                    android:value="ImGuiExample" />
               <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
               </intent-filter>
          </activity>
          <meta-data
               android:name="android.max_aspect"
               android:value="4.0" />
     </application>
</manifest>
""".trimIndent()