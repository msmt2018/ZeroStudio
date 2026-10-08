package com.itsaky.androidide.activities.editor.ui.screen

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.material.R.attr
import com.itsaky.androidide.utils.resolveAttr

/** Resolve the same user-selected theme used by Sora and hosted Fragments. */
@Composable
internal fun editorColorScheme(context: Context): ColorScheme {
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(context.resolveAttr(androidx.appcompat.R.attr.colorPrimary)),
        onPrimary = Color(context.resolveAttr(attr.colorOnPrimary)),
        primaryContainer = Color(context.resolveAttr(attr.colorPrimaryContainer)),
        onPrimaryContainer = Color(context.resolveAttr(attr.colorOnPrimaryContainer)),
        secondary = Color(context.resolveAttr(attr.colorSecondary)),
        onSecondary = Color(context.resolveAttr(attr.colorOnSecondary)),
        secondaryContainer = Color(context.resolveAttr(attr.colorSecondaryContainer)),
        onSecondaryContainer = Color(context.resolveAttr(attr.colorOnSecondaryContainer)),
        tertiary = Color(context.resolveAttr(attr.colorTertiary)),
        onTertiary = Color(context.resolveAttr(attr.colorOnTertiary)),
        tertiaryContainer = Color(context.resolveAttr(attr.colorTertiaryContainer)),
        onTertiaryContainer = Color(context.resolveAttr(attr.colorOnTertiaryContainer)),
        surface = Color(context.resolveAttr(attr.colorSurface)),
        onSurface = Color(context.resolveAttr(attr.colorOnSurface)),
        surfaceVariant = Color(context.resolveAttr(attr.colorSurfaceVariant)),
        onSurfaceVariant = Color(context.resolveAttr(attr.colorOnSurfaceVariant)),
        outline = Color(context.resolveAttr(attr.colorOutline)),
        outlineVariant = Color(context.resolveAttr(attr.colorOutlineVariant)),
        error = Color(context.resolveAttr(androidx.appcompat.R.attr.colorError)),
        onError = Color(context.resolveAttr(attr.colorOnError)),
        errorContainer = Color(context.resolveAttr(attr.colorErrorContainer)),
        onErrorContainer = Color(context.resolveAttr(attr.colorOnErrorContainer)),
        surfaceContainer = Color(context.resolveAttr(attr.colorSurfaceContainer)),
        surfaceContainerHigh = Color(context.resolveAttr(attr.colorSurfaceContainerHigh)),
        surfaceContainerHighest = Color(context.resolveAttr(attr.colorSurfaceContainerHighest)),
        surfaceContainerLow = Color(context.resolveAttr(attr.colorSurfaceContainerLow)),
        surfaceContainerLowest = Color(context.resolveAttr(attr.colorSurfaceContainerLowest)),
        background = Color(context.resolveAttr(android.R.attr.colorBackground)),
        onBackground = Color(context.resolveAttr(attr.colorOnSurface)),
        surfaceTint = Color(context.resolveAttr(androidx.appcompat.R.attr.colorPrimary)),
    )
}
