/*
 * This file is part of ZeroStudio.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.itsaky.androidide.actions.sidebar

import android.content.Context
import android.zero.studio.termux.ui.fragments.TerminalHostFragment
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.resources.R
import kotlin.reflect.KClass

/**
 * Opens the native ZeroStudio-Terminal host in the editor sidebar.
 *
 * The legacy Termux Activity/Fragment route is intentionally not exposed here.
 */
class TerminalSidebarAction(context: Context, override val order: Int) :
    AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.terminal"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = TerminalHostFragment::class

  init {
    label = context.getString(R.string.title_terminal_host)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_terminal)
  }
}
