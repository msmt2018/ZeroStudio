package com.itsaky.androidide.templates.impl.nativeTemplate.imguiActivityProject

internal fun imguiManifestXml(packageName: String, activityClass: String) = """
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
               android:name="${data.packageName}.MainActivity"
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