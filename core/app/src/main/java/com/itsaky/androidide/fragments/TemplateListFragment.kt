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

package com.itsaky.androidide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.TemplateGridAdapter
import com.itsaky.androidide.databinding.FragmentTemplateListTabbedBinding
import com.itsaky.androidide.preferences.internal.ProjectsPreferences
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.Template
import com.itsaky.androidide.templates.TemplateCategory
import com.itsaky.androidide.utils.ClassResourceMonitor
import com.itsaky.androidide.viewmodel.MainViewModel
import org.slf4j.LoggerFactory

/**
 * A fragment to show the list of available templates in a categorized, tabbed layout.
 *
 * This fragment uses a `TabLayout` and `ViewPager2` to display different categories of project
 * templates. Each tab corresponds to a `TemplateCategory` and contains a `RecyclerView` with a grid
 * of templates.
 *
 * @property currentLayoutStyle Defines the grid layout for the template list.
 * @author Akash Yadav
 * @author android_zero (Refactored for tabbed layout and fixed compiler issue)
 * @Work-Flow
 * 1. `onViewCreated` initializes the UI and fetches registered template categories from
 *    `ITemplateProvider`.
 * 2. A `CategoryPageAdapter` is created to manage the pages (RecyclerViews) within the
 *    `ViewPager2`.
 * 3. `TabLayoutMediator` links the `TabLayout` with the `ViewPager2`, setting tab titles and icons.
 * 4. Each page in the `ViewPager2` is a `RecyclerView` populated by a `TemplateGridAdapter` with
 *    templates for that specific category.
 * 5. The layout style (e.g., number of columns) is controlled by the `currentLayoutStyle` property.
 */
class TemplateListFragment :
    FragmentWithBinding<FragmentTemplateListTabbedBinding>(
        R.layout.fragment_template_list_tabbed,
        FragmentTemplateListTabbedBinding::bind,
    ) {

  // Get span count from preferences
  private val spanCount: Int
    get() = ProjectsPreferences.templateListSpanCount

  private val viewModel by viewModels<MainViewModel>(ownerProducer = { requireActivity() })
  private val templateProvider by lazy { ITemplateProvider.getInstance(reload = true) }

  companion object {
    private val log = LoggerFactory.getLogger(TemplateListFragment::class.java)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.exitButton.setOnClickListener { viewModel.setScreen(MainViewModel.SCREEN_MAIN) }

    setupTabsAndPager()
  }

  /** Sets up the TabLayout and ViewPager2 with categorized template data. */
  private fun setupTabsAndPager() = ClassResourceMonitor.trace(log) {
    log.debug("Setting up tabs and pager for templates.")
    val categories = templateProvider.getRegisteredCategories()

    if (categories.isEmpty()) {
      log.warn("No template categories are registered. The template list will be empty.")
      // Optionally, show an empty state view here.
      return@trace
    }

    val pagerAdapter =
        CategoryPageAdapter(
            categories = categories,
            templateProvider = templateProvider,
            spanCount = spanCount,
            onTemplateClick = { template ->
              viewModel.template.value = template
              viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_DETAILS)
            },
        )
    binding.viewPager.adapter = pagerAdapter

    TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
          val category = categories[position]
          tab.text = category.title
          category.icon?.let { tab.setIcon(it) }
        }
        .attach()
  }

  override fun onDestroyView() {
    // Important to avoid memory leaks with ViewPager2 adapter
    binding.viewPager.adapter = null
    super.onDestroyView()
  }
}

/**
 * An adapter for the ViewPager2 that creates a RecyclerView for each template category. This is
 * defined as a private top-level class to avoid potential Kotlin compiler bugs related to nested
 * inner classes within Fragments.
 *
 * @author android_zero
 */
private class CategoryPageAdapter(
    private val categories: List<TemplateCategory>,
    private val templateProvider: ITemplateProvider,
    private val spanCount: Int,
    private val onTemplateClick: (Template<*>) -> Unit,
) : RecyclerView.Adapter<CategoryPageAdapter.PageViewHolder>() {

  class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val recyclerView: RecyclerView = itemView.findViewById(R.id.template_grid_recycler_view)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
    val view =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_template_category_page, parent, false)
    return PageViewHolder(view)
  }

  override fun getItemCount(): Int = categories.size

  override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
    val category = categories[position]
    ClassResourceMonitor.trace(CategoryPageAdapter::class.java) {
      val templates = templateProvider.getTemplatesFor(category).filterIsInstance<ProjectTemplate>()

      holder.recyclerView.apply {
        layoutManager = GridLayoutManager(context, spanCount)
        adapter = TemplateGridAdapter(templates, onTemplateClick)
        setHasFixedSize(true)
      }
    }
  }
}
