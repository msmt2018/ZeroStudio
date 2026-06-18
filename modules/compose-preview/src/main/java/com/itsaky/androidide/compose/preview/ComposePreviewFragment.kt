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

package com.itsaky.androidide.compose.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itsaky.androidide.compose.preview.databinding.FragmentComposePreviewBinding
import com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine
import com.itsaky.androidide.resources.R as ResourcesR
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Compose 预览 Fragment 入口 (v3).
 *
 * 与 v2 不同, 渲染由 [PreviewRenderEngine] 统一管理:
 * - 在 onViewCreated 中 attach engine 到根容器
 * - 在 PreviewState.Ready 触发 engine.render(...)
 * - 在 onDestroyView 中 engine.detach() 释放 dex 运行时
 */
class ComposePreviewFragment : Fragment() {

    private var _binding: FragmentComposePreviewBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding accessed after view destroyed")

    private val viewModel: ComposePreviewViewModel by viewModels()

    private var renderEngine: PreviewRenderEngine? = null

    private var sourceCode: String = DEFAULT_SOURCE
    private var onNavigateBack: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComposePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupPreview()
        observeState()

        val filePath = arguments?.getString(ARG_FILE_PATH) ?: ""
        viewModel.initialize(requireContext(), filePath)

        arguments?.getString(ARG_SOURCE_CODE)?.let {
            sourceCode = it
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onNavigateBack?.invoke() ?: parentFragmentManager.popBackStack()
        }
    }

    private fun setupPreview() {
        // 把 PreviewRenderEngine 挂到 previewContainer (FrameLayout), 由它创建/管理 ComposeView.
        // previewContainer 是 fragment_compose_preview.xml 中专门用于预览的 FrameLayout,
        // 使用它能避免在 ConstraintLayout 根上 addView 时尺寸为 0 的问题.
        renderEngine = PreviewRenderEngine(requireContext(), binding.previewContainer).also { it.attach() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.previewState.collect { state ->
                    handleState(state)
                }
            }
        }
    }

    private fun handleState(state: PreviewState) {
        binding.loadingIndicator.isVisible = state is PreviewState.Compiling || state is PreviewState.Building
        binding.initializingText.isVisible = state is PreviewState.Initializing ||
            state is PreviewState.Empty ||
            state is PreviewState.NeedsBuild ||
            state is PreviewState.Building
        binding.errorOverlay.isVisible = state is PreviewState.Error

        when (state) {
            is PreviewState.Idle -> {
                if (sourceCode.isNotBlank()) {
                    viewModel.compileNow(sourceCode)
                }
            }
            is PreviewState.Initializing -> {
                binding.initializingText.setText(ResourcesR.string.preview_initializing)
            }
            is PreviewState.Empty -> {
                binding.initializingText.setText(ResourcesR.string.preview_empty_title)
            }
            is PreviewState.Compiling -> {
                LOG.debug("Compiling...")
            }
            is PreviewState.Building -> {
                binding.initializingText.setText(ResourcesR.string.preview_building_project)
                binding.loadingIndicator.isVisible = true
            }
            is PreviewState.NeedsBuild -> {
                binding.initializingText.setText(ResourcesR.string.preview_build_required_title)
            }
            is PreviewState.Ready -> {
                val engine = renderEngine ?: return
                val config = state.previewConfigs.firstOrNull() ?: return
                // v3.4: 把整个 PreviewConfig 传给 engine, 让 @Preview 的
                // backgroundColor / showBackground / uiMode 真正生效.
                engine.render(
                    previewDex = state.dexFile,
                    projectDex = state.projectDexFiles,
                    className = state.className,
                    functionName = config.functionName,
                    previewConfig = config,
                )
            }
            is PreviewState.Error -> {
                showError(state)
            }
        }
    }

    private fun showError(state: PreviewState.Error) {
        binding.errorOverlay.isVisible = true
        binding.errorMessage.text = state.message

        val details = state.diagnostics.joinToString("\n") { diagnostic ->
            buildString {
                diagnostic.file?.let { append("$it:") }
                diagnostic.line?.let { append("$it:") }
                diagnostic.column?.let { append("$it ") }
                append("[${diagnostic.severity}] ")
                append(diagnostic.message)
            }
        }
        binding.errorDetails.text = details
        binding.errorDetails.isVisible = details.isNotBlank()
    }

    fun updateSource(source: String) {
        sourceCode = source
        viewModel.onSourceChanged(source)
    }

    fun setNavigateBackListener(listener: () -> Unit) {
        onNavigateBack = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderEngine?.detach()
        renderEngine = null
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        renderEngine?.detach()
        renderEngine = null
        LOG.warn("Low memory - released preview resources")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposePreviewFragment::class.java)

        private const val ARG_SOURCE_CODE = "source_code"
        private const val ARG_FILE_PATH = "file_path"

        private const val DEFAULT_SOURCE = """
package preview

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun Preview() {
    Text("Hello, Compose Preview!")
}
"""

        fun newInstance(sourceCode: String? = null, filePath: String? = null): ComposePreviewFragment {
            return ComposePreviewFragment().apply {
                arguments = Bundle().apply {
                    sourceCode?.let { putString(ARG_SOURCE_CODE, it) }
                    filePath?.let { putString(ARG_FILE_PATH, it) }
                }
            }
        }
    }
}
