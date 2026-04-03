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
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitHostBinding
import com.itsaky.androidide.fragments.git.menu.GitPopupManager
import kotlinx.coroutines.launch

/**
 * Git 功能模块的主宿主 Fragment，主要聚合全部fragment。
 *
 * @author android_zero
 */
class GitHostFragment : Fragment() {

  private var _binding: FragmentGitHostBinding? = null
  private val binding
    get() = _binding!!

  private lateinit var popupManager: GitPopupManager
  private val gitUiEventViewModel by activityViewModels<GitUiEventViewModel>()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitHostBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setupViewPager()
    setupPopupMenu()
    observeGitEvents()
  }

  private fun observeGitEvents() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        gitUiEventViewModel.events.collect { event ->
          when (event) {
            is GitUiEvent.Operation ->
                Toast.makeText(
                        requireContext(),
                        "Git ${event.section}: ${event.action}",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            is GitUiEvent.Error ->
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  private fun setupViewPager() {
    val adapter = GitPagerAdapter(this)
    binding.gitViewPager.adapter = adapter
    binding.gitViewPager.offscreenPageLimit = 1

    TabLayoutMediator(binding.gitTabLayout, binding.gitViewPager) { tab, position ->
          tab.text =
              when (position) {
                0 -> getString(R.string.title_projects) // Project/Files
                1 -> getString(R.string.changelist) // Changes
                2 -> getString(R.string.commits) // History
                3 -> getString(R.string.git_collaboration) // collaboration
                4 -> getString(R.string.branches) // Branches
                5 -> getString(R.string.stash) // Stash
                6 -> "Diff" // GitDiffFragment
                else -> getString(R.string.other)
              }
        }
        .attach()
  }

  private fun setupPopupMenu() {
    popupManager = GitPopupManager(requireContext())

    binding.btnGitMenu.setOnClickListener { anchorView -> popupManager.show(anchorView) }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private inner class GitPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    // 页面数量为 7 (0-6)
    override fun getItemCount(): Int = 7

    override fun createFragment(position: Int): Fragment {
      return when (position) {
        0 -> GitProjectsFragment()
        1 -> GitChangesFragment()
        2 -> GitHistoryFragment()
        3 -> GitCollaborationFragment()
        4 -> GitBranchesFragment()
        5 -> GitStashFragment()
        6 -> GitDiffFragment()
        else -> Fragment()
      }
    }
  }
}