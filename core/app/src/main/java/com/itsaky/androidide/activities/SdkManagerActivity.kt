package com.itsaky.androidide.activities

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import dagger.hilt.android.AndroidEntryPoint
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.repository.sdkmanager.SdkHostFragment

/** @author android_zero */
@AndroidEntryPoint
class SdkManagerActivity : IDEActivity() {

  override fun bindLayout(): View {
    return FrameLayout(this).apply { id = android.R.id.content }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      supportFragmentManager
          .beginTransaction()
          .replace(android.R.id.content, SdkHostFragment())
          .commit()
    }
  }
}
