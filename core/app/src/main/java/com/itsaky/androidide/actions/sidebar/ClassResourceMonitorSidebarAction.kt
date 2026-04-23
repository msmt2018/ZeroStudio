package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.fragments.sidebar.ClassResourceMonitorFragment
import kotlin.reflect.KClass

/** Sidebar action for runtime class-level CPU/memory monitor. */
class ClassResourceMonitorSidebarAction(context: Context, override val order: Int) :
    AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.classResourceMonitor"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = ClassResourceMonitorFragment::class

  init {
    label = context.getString(R.string.class_resource_monitor)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_info)
  }
}
