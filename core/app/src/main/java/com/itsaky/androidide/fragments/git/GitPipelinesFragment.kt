/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.R

/** CD/CI 流水线状态页面。 */
class GitPipelinesFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    links = GitHostWebLinks.resolveForCurrentProject()
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, "Refresh Pipelines") {
      links = GitHostWebLinks.resolveForCurrentProject()
      emitGitOperation("pipelines", "refresh_remote_links")
      Toast.makeText(context, "Pipeline links refreshed", Toast.LENGTH_SHORT).show()
    }

    addToolbarAction(R.drawable.ic_info_24, "Open Pipelines") {
      emitGitOperation("pipelines", "open_pipelines")
      openIfAny(links?.pipelinesUrl, "No remote repository detected")
    }

    addToolbarAction(R.drawable.ic_check_24, "Open Actions") {
      emitGitOperation("pipelines", "open_actions")
      openIfAny(links?.actionsUrl, "No workflow URL detected")
    }

    addToolbarAction(R.drawable.ic_add_24, "Task + Run YAML") {
      emitGitOperation("pipelines", "create_task_and_run_yaml")
      showRunWorkflowDialog()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    links = GitHostWebLinks.resolveForCurrentProject()

    view.findViewById<RecyclerView>(R.id.rv_branches)?.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = ActionLinksAdapter()
    }
  }

  private fun showRunWorkflowDialog() {
    val target = links
    if (target == null) {
      Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      return
    }

    val ctx = requireContext()
    val container =
        LinearLayout(ctx).apply {
          orientation = LinearLayout.VERTICAL
          val p = (16 * resources.displayMetrics.density).toInt()
          setPadding(p, p, p, p)
        }

    val taskInput = EditText(ctx).apply { hint = "Task title" }
    val yamlInput = EditText(ctx).apply { hint = "YAML file, e.g. ci.yml" }
    val refInput = EditText(ctx).apply { hint = "Branch/ref" }

    val defaultRef = GitHostWebLinks.getCurrentBranchName()
    taskInput.setText("CI Task: run yaml on $defaultRef")
    yamlInput.setText("ci.yml")
    refInput.setText(defaultRef)

    container.addView(taskInput)
    container.addView(yamlInput)
    container.addView(refInput)

    AlertDialog.Builder(ctx)
        .setTitle("Create Task & Run Workflow")
        .setView(container)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton("Open") { _, _ ->
          val taskTitle = taskInput.text?.toString()?.trim().orEmpty().ifBlank { "CI Task" }
          val yaml = yamlInput.text?.toString()?.trim().orEmpty().ifBlank { "ci.yml" }
          val ref = refInput.text?.toString()?.trim().orEmpty().ifBlank { defaultRef }
          openTaskAndWorkflow(target, taskTitle, yaml, ref)
        }
        .show()
  }

  private fun openTaskAndWorkflow(target: GitHostLinks, taskTitle: String, yaml: String, ref: String) {
    val taskBody =
        "Created from AndroidIDE. Remote=${target.remoteName}, ref=$ref, yaml=$yaml"
    val issueUrl = target.newTaskUrl(taskTitle, taskBody)
    val workflowUrl = target.workflowRunUrl(yamlFile = yaml, ref = ref)

    openExternalLink(issueUrl)
    openExternalLink(workflowUrl)
  }

  private fun openIfAny(url: String?, fallbackMsg: String) {
    if (url == null) {
      Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
    } else {
      openExternalLink(url)
    }
  }

  private inner class ActionLinksAdapter : RecyclerView.Adapter<ActionLinksAdapter.Holder>() {
    private val items =
        listOf(
            "Open Pipelines",
            "Open Actions",
            "Create Task + Run YAML",
        )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      holder.title.text = items[position]
      holder.itemView.setOnClickListener {
        when (position) {
          0 -> openIfAny(links?.pipelinesUrl, "No remote repository detected")
          1 -> openIfAny(links?.actionsUrl, "No workflow URL detected")
          else -> showRunWorkflowDialog()
        }
      }
    }

    override fun getItemCount() = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
    }
  }
}
