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
 */
package com.itsaky.androidide.utils

// import com.itsaky.androidide.actions.lsp.*
import android.content.Context
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_FILE_TABS
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_FILE_TREE
import com.itsaky.androidide.actions.ActionItem.Location.EDITOR_TOOLBAR
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.build.ProjectSyncAction
import com.itsaky.androidide.actions.build.QuickRunWithCancellationAction
import com.itsaky.androidide.actions.build.RunTasksAction
import com.itsaky.androidide.actions.code.CodeActionsMenu
import com.itsaky.androidide.actions.cursor.*
import com.itsaky.androidide.actions.editor.CopyAction
import com.itsaky.androidide.actions.editor.ColorQueryTextAction
import com.itsaky.androidide.actions.editor.ColorQueryToolbarAction
import com.itsaky.androidide.actions.editor.CutAction
import com.itsaky.androidide.actions.editor.ExpandSelectionAction
import com.itsaky.androidide.actions.editor.LongSelectAction
import com.itsaky.androidide.actions.editor.PasteAction
import com.itsaky.androidide.actions.editor.SelectAllAction
import com.itsaky.androidide.actions.editor.SystemTextMenuAction
import com.itsaky.androidide.actions.etc.DisconnectLogSendersAction
import com.itsaky.androidide.actions.etc.FindActionMenu
import com.itsaky.androidide.actions.etc.LaunchAppAction
import com.itsaky.androidide.actions.etc.OpenDeviceConnectionAction
import com.itsaky.androidide.actions.etc.PreviewComposeAction
import com.itsaky.androidide.actions.etc.PreviewLayoutAction
import com.itsaky.androidide.actions.etc.ReloadColorSchemesAction
import com.itsaky.androidide.actions.etc.markdown.MarkdownPreviewAction
import com.itsaky.androidide.actions.etc.image.ImagePreviewAction
import com.itsaky.androidide.actions.etc.universal.UniversalPreviewAction
import com.itsaky.androidide.actions.etc.audio.AudioPreviewAction
import com.itsaky.androidide.actions.etc.video.VideoPreviewAction
import com.itsaky.androidide.actions.etc.web.WebPreviewAction
import com.itsaky.androidide.actions.file.CloseAllFilesAction
import com.itsaky.androidide.actions.file.CloseFileAction
import com.itsaky.androidide.actions.file.CloseOtherFilesAction
import com.itsaky.androidide.actions.file.FormatCodeAction
import com.itsaky.androidide.actions.file.SaveFileAction
import com.itsaky.androidide.actions.filetree.ConvertPathToAction
import com.itsaky.androidide.actions.filetree.CopyPathAction
import com.itsaky.androidide.actions.filetree.DeleteAction
import com.itsaky.androidide.actions.filetree.GitAddAction
import com.itsaky.androidide.actions.filetree.GitBranchSwitchAction
import com.itsaky.androidide.actions.filetree.GitBranchesListAction
import com.itsaky.androidide.actions.filetree.GitCommitAction
import com.itsaky.androidide.actions.filetree.GitFetchAction
import com.itsaky.androidide.actions.filetree.GitForcePushAction
import com.itsaky.androidide.actions.filetree.GitLogAction
import com.itsaky.androidide.actions.filetree.GitOpenFullUiAction
import com.itsaky.androidide.actions.filetree.GitPullAction
import com.itsaky.androidide.actions.filetree.GitPushAction
import com.itsaky.androidide.actions.filetree.GitRevertAction
import com.itsaky.androidide.actions.filetree.GitRevertForceAction
import com.itsaky.androidide.actions.filetree.GitStatusAction
import com.itsaky.androidide.actions.filetree.GitUnstageAction
import com.itsaky.androidide.actions.filetree.NewFileAction
import com.itsaky.androidide.actions.filetree.NewFileOrFolderAction
import com.itsaky.androidide.actions.filetree.NewFolderAction
import com.itsaky.androidide.actions.filetree.OpenWithAction
import com.itsaky.androidide.actions.filetree.RefactorRenameAction
import com.itsaky.androidide.actions.filetree.RenameAction
import com.itsaky.androidide.actions.menu.EditorEditLineMenuAction
import com.itsaky.androidide.actions.text.RedoAction
import com.itsaky.androidide.actions.text.UndoAction

/**
 * Takes care of registering actions to the actions registry for the editor activity.
 *
 * @author Akash Yadav
 */
class EditorActivityActions {

  companion object {

    @JvmStatic
    fun register(context: Context) {
      clear()
      val registry = ActionsRegistry.getInstance()
      var order = 0

      // Toolbar actions
      registry.registerAction(UndoAction(context, order++))
      registry.registerAction(RedoAction(context, order++))
      registry.registerAction(QuickRunWithCancellationAction(context, order++))
      registry.registerAction(RunTasksAction(context, order++))
      registry.registerAction(SaveFileAction(context, order++))
      registry.registerAction(EditorEditLineMenuAction(context, order++))
      registry.registerAction(CodeActionsMenu(context, order++))
      registry.registerAction(CursorActionsMenu(context, order++))
      registry.registerAction(PreviewLayoutAction(context, order++))
      registry.registerAction(PreviewComposeAction(context, order++))
      registry.registerAction(MarkdownPreviewAction(context, order++))
      registry.registerAction(ImagePreviewAction(context, order++))
      registry.registerAction(UniversalPreviewAction(context, order++))
      registry.registerAction(AudioPreviewAction(context, order++))
      registry.registerAction(VideoPreviewAction(context, order++))
      registry.registerAction(WebPreviewAction(context, order++))
      registry.registerAction(FindActionMenu(context, order++))
      registry.registerAction(ProjectSyncAction(context, order++))
      registry.registerAction(ReloadColorSchemesAction(context, order++))
      registry.registerAction(DisconnectLogSendersAction(context, order++))
      registry.registerAction(LaunchAppAction(context, order++))
      registry.registerAction(OpenDeviceConnectionAction(context, order++))
      registry.registerAction(ColorQueryToolbarAction(context, order++))

      // editor text actions
      registry.registerAction(SelectAllAction(context, order++))
      registry.registerAction(CutAction(context, order++))
      registry.registerAction(CopyAction(context, order++))
      registry.registerAction(PasteAction(context, order++))
      registry.registerAction(ColorQueryTextAction(context, order++))
      registry.registerAction(LongSelectAction(context, order++))
      registry.registerAction(ExpandSelectionAction(context, order++))
      registry.registerAction(FormatCodeAction(context, order++))
      registry.registerAction(SystemTextMenuAction(context, order++))
      registry.registerAction(EditorEditLineMenuAction(context, order++))

      // file tab actions
      registry.registerAction(CloseFileAction(context, order++))
      registry.registerAction(CloseOtherFilesAction(context, order++))
      registry.registerAction(CloseAllFilesAction(context, order++))

      // file tree actions
      registry.registerAction(CopyPathAction(context, order++))
      registry.registerAction(DeleteAction(context, order++))
      registry.registerAction(NewFileAction(context, order++))
      registry.registerAction(NewFolderAction(context, order++))
      registry.registerAction(OpenWithAction(context, order++))
      registry.registerAction(NewFileOrFolderAction(context, order++))
      registry.registerAction(RenameAction(context, order++))
      registry.registerAction(RefactorRenameAction(context, order++))
      registry.registerAction(ConvertPathToAction(context, order++))

      // git 文件级 actions (BaseFileTreeGitAction 会自动检查当前项目是否在 git 仓库中,
      // 不在 git 仓库中则全部隐藏, 所以可以无脑注册)
      registry.registerAction(GitAddAction(context, order++))
      registry.registerAction(GitUnstageAction(context, order++))
      registry.registerAction(GitRevertAction(context, order++))
      registry.registerAction(GitRevertForceAction(context, order++))
      registry.registerAction(GitOpenFullUiAction(context, order++))

      // git 仓库级 actions
      registry.registerAction(GitStatusAction(context, order++))
      registry.registerAction(GitCommitAction(context, order++))
      registry.registerAction(GitPullAction(context, order++))
      registry.registerAction(GitPushAction(context, order++))
      registry.registerAction(GitForcePushAction(context, order++))
      registry.registerAction(GitFetchAction(context, order++))
      registry.registerAction(GitBranchSwitchAction(context, order++))
      registry.registerAction(GitBranchesListAction(context, order++))
      registry.registerAction(GitLogAction(context, order++))
    }

    @JvmStatic
    fun clear() {
      // EDITOR_TEXT_ACTIONS should not be cleared as the language servers register actions there as
      // well
      val locations = arrayOf(EDITOR_TOOLBAR, EDITOR_FILE_TABS, EDITOR_FILE_TREE)
      val registry = ActionsRegistry.getInstance()
      locations.forEach(registry::clearActions)
    }
  }
}
