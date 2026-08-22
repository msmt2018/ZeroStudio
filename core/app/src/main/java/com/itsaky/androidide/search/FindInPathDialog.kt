package com.itsaky.androidide.fragments.sheets

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.databinding.DialogFindInPathBinding
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.search.AdvancedSearchResultsAdapter
import com.itsaky.androidide.search.CustomScopeType
import com.itsaky.androidide.search.FileHeaderResult
import com.itsaky.androidide.search.SearchResultItem
import com.itsaky.androidide.search.SearchScope
import com.itsaky.androidide.search.TextMatchResult
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.viewmodel.AdvancedSearchViewModel

/**
 * @author android_zero 全局高级搜索对话框 - 仿 Android Studio
 *
 * TODO：模块的对话框待改成PopupWindow + RecyclerView
 */
class FindInPathDialog : BottomSheetDialogFragment() {

  private var _binding: DialogFindInPathBinding? = null
  private val binding
    get() = _binding!!

  private val viewModel by viewModels<AdvancedSearchViewModel>()
  private lateinit var adapter: AdvancedSearchResultsAdapter

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.skipCollapsed = true
    dialog.behavior.isDraggable = false
    return dialog
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = DialogFindInPathBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupUI()
    setupListeners()
    setupObservers()

    val activity = requireActivity() as? EditorActivityKt
    val editorView = activity?.getCurrentEditor()
    val editor = editorView?.editor

    if (editor != null) {
      val cursor = editor.cursor
      if (cursor.isSelected) {
        // 获取选中文本
        val text = editor.text.subSequence(cursor.left, cursor.right).toString()
        binding.inputSearch.setText(text)
        viewModel.query = text
      }
    }
  }

  private fun setupUI() {
    adapter = AdvancedSearchResultsAdapter { item -> navigateToItem(item) }
    binding.recyclerView.layoutManager = LinearLayoutManager(context)
    binding.recyclerView.adapter = adapter

    val masks =
        arrayOf(
            "ALL",
            "*.java",
            "*.kt",
            "*.kts",
            "*.xml",
            "*.pro",
            "*.md",
            "*.json",
            "*.txt",
            "*.gradle",
            "*.c",
            "*.h",
        )
    val adapter =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, masks)
    binding.spinnerFileMask.adapter = adapter

    binding.scopeAll.isChecked = true
  }

  private fun setupListeners() {
    binding.scopeTabs.setOnCheckedStateChangeListener { _, checkedIds ->
      if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
      when (checkedIds[0]) {
        R.id.scopeAll -> viewModel.scope = SearchScope.ALL
        R.id.scopeModule -> selectModule()

        // XML 中定义的 ID
        R.id.scopeDirectory -> selectDirectory()
        R.id.scopeFile -> viewModel.scope = SearchScope.FILE

        // 如果 XML 中没有 scopeProject，就用 scopeAll 替代，如果有则取消注释
        // R.id.scopeProject -> viewModel.scope = SearchScope.ALL

        R.id.scopeScope -> showCustomScopeMenu()
      }
    }

    binding.inputSearch.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        performSearch()
        true
      } else false
    }

    binding.btnDoSearch.setOnClickListener { performSearch() }

    binding.toggleFilters.addOnButtonCheckedListener { _, checkedId, isChecked ->
      when (checkedId) {
        R.id.btnMatchCase -> viewModel.matchCase = isChecked
        R.id.btnWords -> viewModel.wholeWord = isChecked
        R.id.btnRegex -> viewModel.useRegex = isChecked
      }
    }

    binding.btnToggleReplace.setOnClickListener {
      val show = binding.inputReplaceContainer.visibility == View.GONE
      binding.inputReplaceContainer.visibility = if (show) View.VISIBLE else View.GONE
      viewModel.replacement = if (show) binding.inputReplace.text.toString() else null
    }

    binding.btnExclude.setOnClickListener { showExcludeConfigDialog() }
  }

  private fun performSearch() {
    val query = binding.inputSearch.text.toString()
    if (query.isEmpty()) return

    binding.inputSearch.clearFocus()

    val mask = binding.spinnerFileMask.selectedItem.toString()
    viewModel.fileMasks.clear()
    viewModel.fileMasks.add(mask)

    viewModel.query = query
    if (binding.inputReplaceContainer.isVisible) {
      viewModel.replacement = binding.inputReplace.text.toString()
    }

    val activity = requireActivity() as? EditorActivityKt
    val currentFile = activity?.getCurrentEditor()?.file
    viewModel.startSearch(currentFile)
  }

  private fun setupObservers() {
    viewModel.results.observe(viewLifecycleOwner) { list -> adapter.submitList(list) }

    viewModel.isSearching.observe(viewLifecycleOwner) { loading ->
      binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
      binding.btnDoSearch.isEnabled = !loading
    }

    viewModel.statusInfo.observe(viewLifecycleOwner) { status -> binding.tvStatus.text = status }
  }

  private fun selectModule() {
    val project = IProjectManager.getInstance().getWorkspace() ?: return
    val modules = project.androidProjects().toList()
    val names = modules.map { it.name }.toTypedArray()

    DialogUtils.newMaterialDialogBuilder(requireContext())
        .setTitle("Select Module")
        .setItems(names) { _, which ->
          val module = modules[which]
          viewModel.scope = SearchScope.MODULE
          viewModel.targetModule = module.projectDir
          binding.scopeModule.text = "Module: ${module.name}"
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> binding.scopeAll.isChecked = true }
        .show()
  }

  private fun selectDirectory() {
    // 实际应用中应调用文件选择器
    requireActivity().flashInfo("Directory picker integration required.")
    viewModel.scope = SearchScope.DIRECTORY
    viewModel.targetDirectory = IProjectManager.getInstance().projectDir
  }

  private fun showCustomScopeMenu() {
    val scopes = CustomScopeType.values()
    val names = scopes.map { it.label }.toTypedArray()

    DialogUtils.newMaterialDialogBuilder(requireContext())
        .setTitle("Select Scope")
        .setItems(names) { _, which ->
          viewModel.scope = SearchScope.SCOPE_CUSTOM
          viewModel.customScope = scopes[which]
          binding.scopeScope.text = scopes[which].label
        }
        .show()
  }

  private fun showExcludeConfigDialog() {
    val input = EditText(requireContext())
    input.hint = "e.g. build, .git, *.tmp (One per line)"
    input.setText(viewModel.excludePatterns.joinToString("\n"))

    DialogUtils.newMaterialDialogBuilder(requireContext())
        .setTitle("Exclude Patterns")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
          val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
          viewModel.excludePatterns.clear()
          viewModel.excludePatterns.addAll(lines)
        }
        .show()
  }

  private fun navigateToItem(item: SearchResultItem) {
    val activity = requireActivity() as? EditorActivityKt ?: return

    when (item) {
      is FileHeaderResult -> {
        activity.openFile(item.file)
        dismiss()
      }
      is TextMatchResult -> {
        activity.openFileAndSelect(item.file, item.matchRange)
        dismiss()
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    viewModel.stopSearch()
    _binding = null
  }
}
