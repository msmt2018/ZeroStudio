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
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.activities.main.MainScreen
import com.itsaky.androidide.activities.main.TemplateDetailsScreen
import com.itsaky.androidide.activities.main.TemplateListScreen
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.app.IDEActivity
import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.templates.ProjectTemplateRecipeResult
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.impl.ConstraintVerifier
import com.itsaky.androidide.utils.TemplateRecipeExecutor
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.viewmodel.MainEvent
import com.itsaky.androidide.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 现代版 MainActivity，基于纯粹的 Jetpack Compose 与 MVM 架构驱动。
 * 彻底剔除传统的 supportFragmentManager、FragmentTransaction 与 FrameLayout 视图容器。
 *
 * @author android_zero
 */
class MainActivity : IDEActivity() {

  private val viewModel by viewModels<MainViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 监听全局项目事件（打开成功、提示消息、权限确认等）
    lifecycleScope.launch {
      viewModel.mainEvents.collect { event ->
        when (event) {
          is MainEvent.OpenProjectSuccess -> {
            withContext(Dispatchers.IO) {
              ToolsManager.initIfNeeded(this@MainActivity.application as BaseApplication, null).join()
            }
            val intent = Intent(this@MainActivity, EditorActivityKt::class.java).apply {
              addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
              addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
          }
          is MainEvent.ShowMessage -> {
            if (event.isError) flashError(event.messageResId) else flashInfo(event.messageResId)
          }
          is MainEvent.RequestConfirmOpen -> {
            // 打开项目的二次确认交由 Compose 状态弹窗接管
            pendingConfirmOpenProject = event.projectDir
          }
        }
      }
    }

    viewModel.checkAndOpenLastProject()

    if (viewModel.currentScreen.value == -1 && viewModel.previousScreen == -1) {
      viewModel.setScreen(MainViewModel.SCREEN_MAIN)
    }
  }

  // 待确认打开的项目句柄
  private var pendingConfirmOpenProject by mutableStateOf<File?>(null)

  @Composable
  override fun ComposeContent() {
    val coroutineScope = rememberCoroutineScope()

    // 观察屏幕导航状态
    var currentScreen by remember {
      mutableIntStateOf(viewModel.currentScreen.value ?: MainViewModel.SCREEN_MAIN)
    }

    DisposableEffect(viewModel) {
      val observer = Observer<Int> { screen ->
        if (screen != -1) currentScreen = screen
      }
      viewModel.currentScreen.observe(this@MainActivity, observer)
      onDispose { viewModel.currentScreen.removeObserver(observer) }
    }

    // 监听创建项目中的状态
    var isCreatingProject by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
      val observer = Observer<Boolean> { isCreating ->
        isCreatingProject = isCreating
      }
      viewModel.creatingProject.observe(this@MainActivity, observer)
      onDispose { viewModel.creatingProject.removeObserver(observer) }
    }

    // Compose 原生系统返回键处理
    BackHandler(enabled = currentScreen != MainViewModel.SCREEN_MAIN && !isCreatingProject) {
      val newScreen = when (currentScreen) {
        MainViewModel.SCREEN_TEMPLATE_DETAILS -> MainViewModel.SCREEN_TEMPLATE_LIST
        MainViewModel.SCREEN_TEMPLATE_LIST -> MainViewModel.SCREEN_MAIN
        else -> MainViewModel.SCREEN_MAIN
      }
      viewModel.setScreen(newScreen)
    }

    // 根据 ViewModel 当前屏幕标识声明式切换视图
    when (currentScreen) {
      MainViewModel.SCREEN_MAIN -> {
        MainScreen(
            onNewProject = { viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST) },
            onOpenProject = { root -> openProject(root) },
            onCloneRepo = {
              // 占位提示或展示 Compose 版本的克隆仓库弹窗
              flashInfo(R.string.git_clone_repo)
            },
        )
      }
      MainViewModel.SCREEN_TEMPLATE_LIST -> {
        TemplateListScreen(
            onSelectTemplate = { template ->
              viewModel.template.value = template
              viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_DETAILS)
            },
            onBack = { viewModel.setScreen(MainViewModel.SCREEN_MAIN) },
        )
      }
      MainViewModel.SCREEN_TEMPLATE_DETAILS -> {
        TemplateDetailsScreen(
            template = viewModel.template.value,
            isCreating = isCreatingProject,
            onBack = { viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST) },
            onFinish = {
              val template = viewModel.template.value ?: return@TemplateDetailsScreen
              val isValid = template.parameters.fold(true) { valid, param ->
                if (param is StringParameter) valid && ConstraintVerifier.isValid(param.value, param.constraints)
                else valid
              }
              if (!isValid) {
                flashError(R.string.msg_invalid_project_details)
                return@TemplateDetailsScreen
              }

              viewModel.creatingProject.value = true
              coroutineScope.launch(Dispatchers.IO) {
                try {
                  val result = template.recipe.execute(TemplateRecipeExecutor())
                  withContext(Dispatchers.Main) {
                    viewModel.creatingProject.value = false
                    if (result is ProjectTemplateRecipeResult) {
                      viewModel.setScreen(MainViewModel.SCREEN_MAIN)
                      flashSuccess(R.string.project_created_successfully)
                      openProject(result.data.projectDir)
                    } else {
                      flashError(R.string.project_creation_failed)
                    }
                  }
                } catch (e: Exception) {
                  withContext(Dispatchers.Main) {
                    viewModel.creatingProject.value = false
                    flashError(e.message ?: "Failed to generate project template")
                  }
                }
              }
            },
        )
      }
    }

    // 打开上次项目的确认弹窗
    pendingConfirmOpenProject?.let { root ->
      AlertDialog(
          onDismissRequest = { pendingConfirmOpenProject = null },
          title = { Text(getString(R.string.title_confirm_open_project)) },
          text = { Text(getString(R.string.msg_confirm_open_project, root.absolutePath)) },
          confirmButton = {
            TextButton(onClick = {
              pendingConfirmOpenProject = null
              openProject(root)
            }) {
              Text(getString(R.string.yes))
            }
          },
          dismissButton = {
            TextButton(onClick = { pendingConfirmOpenProject = null }) {
              Text(getString(R.string.no))
            }
          },
      )
    }
  }

  fun openProject(root: File) {
    viewModel.openProject(this, root)
  }

  override fun onDestroy() {
    ITemplateProvider.getInstance().release()
    super.onDestroy()
  }
}