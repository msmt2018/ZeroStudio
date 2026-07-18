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
package com.itsaky.androidide.actions.filetree

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分支切换 (Switch Branch) — 弹窗显示本地 + 远程两组分支, 点击可 git checkout。
 * 复刻 puppygit `Libgit2Helper.getBranchList` + `checkoutLocalBranchThenUpdateHead`
 * + `checkoutRemoteBranchThenDetachHead`。
 *
 * @author android_zero
 */
class GitBranchSwitchAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_branch_switch,
        iconRes = R.drawable.ic_git_branch,
    ) {

  override val id: String = "ide.editor.fileTree.git.branchSwitch"

  override suspend fun execAction(data: ActionData) {
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()
    val activity = data.requireActivity()

    // 1. 加载本地/远程分支
    val (locals, remotes, err) = withContext(Dispatchers.IO) {
        val l = (FileTreeGitOps.listLocalBranches() as? FileTreeGitOps.GitOpResult.Ok)?.data
            ?: return@withContext Triple(emptyList<Any>(), emptyList<Any>(), "Failed to list local branches")
        val r = (FileTreeGitOps.listRemoteBranches() as? FileTreeGitOps.GitOpResult.Ok)?.data
            ?: emptyList()
        Triple<Any, Any, String?>(l, r, null)
    }
    if (err != null) {
      flashError(activity.getString(R.string.git_msg_op_failed, err))
      return
    }

    @Suppress("UNCHECKED_CAST")
    val localList = (locals as List<com.catpuppyapp.puppygit.git.BranchNameAndTypeDto>)
    @Suppress("UNCHECKED_CAST")
    val remoteList = (remotes as List<com.catpuppyapp.puppygit.git.BranchNameAndTypeDto>)

    // 2. 构造弹窗 — 分组显示
    val sections = mutableListOf<Pair<String, List<com.catpuppyapp.puppygit.git.BranchNameAndTypeDto>>>()
    if (localList.isNotEmpty()) {
      sections.add(activity.getString(R.string.git_msg_local_branches) to localList)
    }
    if (remoteList.isNotEmpty()) {
      sections.add(activity.getString(R.string.git_msg_remote_branches) to remoteList)
    }
    if (sections.isEmpty()) {
      flashInfo(R.string.git_msg_no_repo)
      return
    }

    // 3. 渲染: 简单使用两段 ListView 拼起来, 点击即切换
    showBranchDialog(activity, sections)
  }

  private fun showBranchDialog(
      activity: com.itsaky.androidide.activities.editor.EditorHandlerActivity,
      sections: List<Pair<String, List<com.catpuppyapp.puppygit.git.BranchNameAndTypeDto>>>,
  ) {
    // 拼一个简单 array, header 不可点
    val rows = mutableListOf<Any>()  // Pair<Boolean, String> = (isHeader, label)
    val items = mutableListOf<com.catpuppyapp.puppygit.git.BranchNameAndTypeDto>()
    for ((headerLabel, list) in sections) {
      rows.add(Pair(true, headerLabel))
      for (b in list) {
        val star = if (b.isCurrent) "★ " else "  "
        rows.add(Pair(false, "$star${b.shortName}  ${b.shortOidStr}"))
        items.add(b)
      }
    }

    val builder = DialogUtils.newMaterialDialogBuilder(activity)
    builder.setTitle(R.string.git_msg_branch_dialog_title)

    // 使用 simple_list_item_1 即可, 头部行点击无效
    val adapter = object : ArrayAdapter<Any>(activity, android.R.layout.simple_list_item_1, rows) {
      override fun isEnabled(position: Int): Boolean {
        return rows[position] is Pair<*, *> && (rows[position] as Pair<Boolean, *>).first == false
      }

      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        val row = rows[position]
        if (row is Pair<*, *> && (row as Pair<Boolean, *>).first == true) {
          // header 样式
          tv.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
          tv.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        return view
      }
    }
    builder.setAdapter(adapter) { _, which ->
      // 找到这一行对应的 branch
      val clicked = rows[which]
      if (clicked is Pair<*, *> && (clicked as Pair<Boolean, *>).first == true) {
        // header, 忽略
        return@setAdapter
      }
      // 计算该行在 items 列表中的索引 (仅非 header 行算)
      val idx = (0 until which).count { (rows[it] as Pair<Boolean, *>).first == false }
      val branch = items.getOrNull(idx) ?: return@setAdapter
      // 切换分支
      switchBranch(activity, branch)
    }
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.show()
  }

  private fun switchBranch(
      activity: com.itsaky.androidide.activities.editor.EditorHandlerActivity,
      branch: com.catpuppyapp.puppygit.git.BranchNameAndTypeDto,
  ) {
    // 二次确认 (如果当前分支有未保存修改)
    AlertDialog.Builder(activity)
        .setTitle(R.string.git_action_branch_switch)
        .setMessage(activity.getString(R.string.git_msg_switch_branch_success, branch.shortName))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.git_action_branch_switch) { d, _ ->
            d.dismiss()
            // 实际切换
            actionScope.launch {
                val ret = withContext(Dispatchers.IO) {
                    FileTreeGitOps.switchBranch(branch.fullName, force = false)
                }
                when (ret) {
                    is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(
                        activity.getString(R.string.git_msg_switch_branch_success, branch.shortName)
                    )
                    is FileTreeGitOps.GitOpResult.Err -> flashError(
                        activity.getString(R.string.git_msg_op_failed, ret.msg)
                    )
                }
            }
        }
        .show()
  }
}
