/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.activities

import android.content.Intent
import androidx.compose.runtime.Composable
import com.itsaky.androidide.activities.about.AboutScreen
import com.itsaky.androidide.app.IDEActivity

/**
 * 纯 Compose 版 AboutActivity。
 *
 * @author android_zero
 */
class AboutActivity : IDEActivity() {

  @Composable
  override fun ComposeContent() {
    AboutScreen(
        onBack = { finish() },
        onOpenContributors = {
          startActivity(Intent(this, ContributorsActivity::class.java))
        },
    )
  }
}