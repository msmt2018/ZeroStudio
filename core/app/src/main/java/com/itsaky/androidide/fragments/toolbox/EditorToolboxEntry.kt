package com.itsaky.androidide.fragments.toolbox

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

/**
 * Pluggable descriptor for an editor toolbox entry.
 */
data class EditorToolboxEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val fragmentClass: KClass<out Fragment>,
)
