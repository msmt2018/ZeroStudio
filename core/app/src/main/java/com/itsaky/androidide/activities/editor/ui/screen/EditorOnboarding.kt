package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.itsaky.androidide.R
import com.itsaky.androidide.onboarding.*
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.prefs.SharedPreferencesOnboardingPreferences

/** Compose targets replace the old View listeners and overlay ComposeView. */
@Composable
internal fun EditorOnboarding(targets: List<OnboardingTarget>) {
    val context = LocalContext.current
    val preferences = remember(context) { SharedPreferencesOnboardingPreferences(context) }
    val steps = remember(context, targets) {
        listOf(
            R.string.onboarding_step1_title to R.string.editor_guide_drawer,
            R.string.onboarding_step2_title to R.string.onboarding_step2_subtitle,
            R.string.onboarding_step3_title to R.string.editor_guide_symbols,
        ).mapIndexed { index, (title, text) ->
            OnboardingStep("editor_step_$index", BubbleContent(context.getString(title), context.getString(text)), target = targets[index], bubblePlacement = BubblePlacement.Above)
        }
    }
    val controller = LaunchOnboarding(steps, OnboardingConfig(guideId = "editor_bottom_sheet_onboarding_v1", preferences = preferences))
    OnboardingOverlay(controller)
}
