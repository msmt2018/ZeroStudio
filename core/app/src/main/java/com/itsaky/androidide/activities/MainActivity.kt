/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 * @author android_zero
 */
package com.itsaky.androidide.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.app.EdgeToEdgeIDEActivity
import com.itsaky.androidide.fragments.MainFragment
import com.itsaky.androidide.fragments.TemplateDetailsFragment
import com.itsaky.androidide.fragments.TemplateListFragment
import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.viewmodel.MainEvent
import com.itsaky.androidide.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A modern MainActivity built with Compose + MVM architecture + Material3
 *
 * @author android_zero
 */
class MainActivity : EdgeToEdgeIDEActivity() {

  private val viewModel by viewModels<MainViewModel>()

  private val onBackPressedCallback =
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          viewModel.apply {
            if (creatingProject.value == true) return@apply

            val newScreen =
                when (currentScreen.value) {
                  MainViewModel.SCREEN_TEMPLATE_DETAILS -> MainViewModel.SCREEN_TEMPLATE_LIST
                  MainViewModel.SCREEN_TEMPLATE_LIST -> MainViewModel.SCREEN_MAIN
                  else -> MainViewModel.SCREEN_MAIN
                }

            if (currentScreen.value != newScreen) {
              setScreen(newScreen)
            } else {
              isEnabled = false
              onBackPressedDispatcher.onBackPressed()
            }
          }
        }
      }

  override fun bindLayout(): View {
    return FrameLayout(this).apply { id = android.R.id.content }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // if (savedInstanceState == null) {
    // supportFragmentManager
    // .beginTransaction()
    // .add(android.R.id.content, MainFragment(), "tag_main")
    // .add(android.R.id.content, TemplateListFragment(), "tag_list")
    // .add(android.R.id.content, TemplateDetailsFragment(), "tag_details")
    // .commitNowAllowingStateLoss()
    // }

    viewModel.currentScreen.observe(this) { screen ->
      if (screen == -1) return@observe

      val fm = supportFragmentManager
      val transaction = fm.beginTransaction()

      var mainFrag = fm.findFragmentByTag("tag_main")
      var listFrag = fm.findFragmentByTag("tag_list")
      var detailsFrag = fm.findFragmentByTag("tag_details")

      // 懒加载：仅在需要显示该 Fragment 时才进行实例化并添加
      // 可选项优化方案：Navigation Component
      if (screen == MainViewModel.SCREEN_MAIN && mainFrag == null) {
        mainFrag = MainFragment()
        transaction.add(android.R.id.content, mainFrag, "tag_main")
      }
      if (screen == MainViewModel.SCREEN_TEMPLATE_LIST && listFrag == null) {
        listFrag = TemplateListFragment()
        transaction.add(android.R.id.content, listFrag, "tag_list")
      }
      if (screen == MainViewModel.SCREEN_TEMPLATE_DETAILS && detailsFrag == null) {
        detailsFrag = TemplateDetailsFragment()
        transaction.add(android.R.id.content, detailsFrag, "tag_details")
      }

      mainFrag?.let {
        if (screen == MainViewModel.SCREEN_MAIN) transaction.show(it) else transaction.hide(it)
      }
      listFrag?.let {
        if (screen == MainViewModel.SCREEN_TEMPLATE_LIST) transaction.show(it)
        else transaction.hide(it)
      }
      detailsFrag?.let {
        if (screen == MainViewModel.SCREEN_TEMPLATE_DETAILS) transaction.show(it)
        else transaction.hide(it)
      }

      transaction.commitAllowingStateLoss()

      onBackPressedCallback.isEnabled = screen != MainViewModel.SCREEN_MAIN
    }

    lifecycleScope.launch {
      viewModel.mainEvents.collect { event ->
        when (event) {
          is MainEvent.OpenProjectSuccess -> {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
              ToolsManager.initIfNeeded(this@MainActivity.application as BaseApplication, null).join()
            }
            val intent =
                Intent(this@MainActivity, EditorActivityKt::class.java).apply {
                  addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                  addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            startActivity(intent)
          }
          is MainEvent.ShowMessage -> {
            if (event.isError) flashError(event.messageResId) else flashInfo(event.messageResId)
          }
          is MainEvent.RequestConfirmOpen -> askProjectOpenPermission(event.projectDir)
        }
      }
    }

    viewModel.checkAndOpenLastProject()

    if (viewModel.currentScreen.value == -1 && viewModel.previousScreen == -1) {
      viewModel.setScreen(MainViewModel.SCREEN_MAIN)
    }

    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
  }

  private fun askProjectOpenPermission(root: File) {
    DialogUtils.newMaterialDialogBuilder(this)
        .setTitle(R.string.title_confirm_open_project)
        .setMessage(getString(R.string.msg_confirm_open_project, root.absolutePath))
        .setCancelable(false)
        .setPositiveButton(R.string.yes) { _, _ -> openProject(root) }
        .setNegativeButton(R.string.no, null)
        .show()
  }

  fun openProject(root: File) {
    viewModel.openProject(this, root)
  }

  override fun onDestroy() {
    ITemplateProvider.getInstance().release()
    super.onDestroy()
  }
}
