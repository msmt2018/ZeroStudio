package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.fragments.sidebar.DataFileTreeFragment
import kotlin.reflect.KClass

/** 侧边栏操作，用于显示数据文件树。 */
class DataFileTreeSidebarAction(context: Context, override val order: Int) :
    AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.dataFileTree"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = DataFileTreeFragment::class
  override val iconTintAttr = R.attr.colorPrimary

  init {
    // 设置标签
    label = context.getString(R.string.msg_data_file_tree)
    loadIcon(context, R.drawable.ic_internal_data)
  }
}
