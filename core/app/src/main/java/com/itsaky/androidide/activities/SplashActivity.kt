package com.itsaky.androidide.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Startup launcher activity.
 *
 * Uses AndroidX SplashScreen API to avoid cold-start white flash and immediately hands off to the
 * real entry activity.
 */
class SplashActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    // Keep launcher activity ultra-lightweight: no Compose / no heavy rendering work.
    startActivity(Intent(this, OnboardingActivity::class.java))
    finish()
    overridePendingTransition(0, 0)
  }
}
