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

package com.itsaky.androidide.templates.impl

import com.google.auto.service.AutoService
import com.itsaky.androidide.templates.ITemplateProvider
import com.itsaky.androidide.templates.Template
import com.itsaky.androidide.templates.TemplateCategory
import com.itsaky.androidide.templates.impl.basicActivity.basicActivityProject
import com.itsaky.androidide.templates.impl.bottomNavActivity.bottomNavActivityProject
import com.itsaky.androidide.templates.impl.composeActivity.composeActivityProject
import com.itsaky.androidide.templates.impl.emptyActivity.emptyActivityProject
import com.itsaky.androidide.templates.impl.navDrawerActivity.navDrawerActivityProject
import com.itsaky.androidide.templates.impl.noActivity.*
import com.itsaky.androidide.templates.impl.noAndroidXActivity.noAndroidXActivityProject
import com.itsaky.androidide.templates.impl.tabbedActivity.tabbedActivityProject
import com.itsaky.androidide.templates.impl.basicCpp.basicCppProject
import com.itsaky.androidide.templates.impl.nativeTemplate.imguiActivityProject.imguiActivityProject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of the [ITemplateProvider]. This class is responsible for discovering,
 * registering, and providing all available project templates, organized by categories.
 *
 * @author Akash Yadav
 * @author android_zero (Refactored for categorization)
 *
 * @property templatesByCategory The core data structure that holds templates grouped by their [TemplateCategory].
 * It uses a [ConcurrentHashMap] for thread-safe operations.
 *
 * @see ITemplateProvider
 * @see TemplateCategory
 * @see Template
 *
 * Work Flow:
 * 1.  Upon instantiation (via ServiceLoader), the `init` block calls `initializeTemplates()`.
 * 2.  `initializeTemplates()` populates the provider with default templates, assigning each to a specific category (e.g., `TemplateCategory.Mobile`).
 * 3.  The public methods (`getRegisteredCategories`, `getTemplatesFor`, etc.) provide access to this categorized data.
 * 4.  `reload()` and `release()` methods manage the lifecycle of the template cache.
 *
 * Usage:
 * To add a new template, simply call `registerTemplate()` within `initializeTemplates()`:
 * ```kotlin
 * // In initializeTemplates()
 * registerTemplate(TemplateCategory.Wear, myWearOsTemplate())
 * ```
 */
@Suppress("unused")
@AutoService(ITemplateProvider::class)
class TemplateProviderImpl : ITemplateProvider {

    private val templatesByCategory = ConcurrentHashMap<TemplateCategory, MutableList<Template<*>>>()

    init {
        initializeTemplates()
    }

    /**
     * Initializes and registers all default project templates into their respective categories.
     * This method serves as the central point for template registration. To add new templates,
     * they should be registered here.
     *
     * Work Flow:
     * 1.  Clears any previously loaded templates to ensure a fresh state.
     * 2.  Calls `registerTemplate` for each default project template, assigning it to the `TemplateCategory.Mobile` category.
     *
     * Example of adding a new template for a different category:
     * ```kotlin
     * // To add a template for Wear OS
     * registerTemplate(TemplateCategory.[Specify a category you need here], {The project template you created}())
     * ```
     */
    private fun initializeTemplates() {
        templatesByCategory.clear()

        // This is where you can add more templates to this or other categories.
        
         //This category is the original built-in templates for ZeroStudio
         registerTemplate(TemplateCategory.BasicZeroStudio, emptyActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, noActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, composeActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, basicActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, bottomNavActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, navDrawerActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, tabbedActivityProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, basicCppProject())
         registerTemplate(TemplateCategory.BasicZeroStudio, noAndroidXActivityProject())
         
         //Native build（C/C++/Cmake） template category
         registerTemplate(TemplateCategory.Native, imguiActivityProject())

    }

    /**
     * Registers a template under a specific category. If the category does not yet exist, it is created.
     * This method is thread-safe.
     *
     * @param category The [TemplateCategory] under which the template will be filed.
     * @param template The [Template] instance to register.
     */
    override fun registerTemplate(category: TemplateCategory, template: Template<*>) {
        val templates = templatesByCategory.computeIfAbsent(category) {
            // Use Collections.synchronizedList for thread-safe list operations if needed,
            // though computeIfAbsent on ConcurrentHashMap already provides atomicity for creation.
            Collections.synchronizedList(mutableListOf())
        }
        templates.add(template)
    }

    /**
     * Retrieves a list of all categories that currently have one or more templates registered.
     * The list is sorted according to the predefined order in [TemplateCategory.defaultCategories].
     *
     * @return An ordered, immutable list of [TemplateCategory].
     */
    override fun getRegisteredCategories(): List<TemplateCategory> {
        // Create a map for sorting order based on the default list.
        val defaultOrder = TemplateCategory.defaultCategories().withIndex().associate { it.value.key to it.index }
        // Sort the existing keys based on the default order. Categories not in the default list are placed at the end.
        return templatesByCategory.keys.sortedBy { defaultOrder[it.key] ?: Int.MAX_VALUE }
    }

    /**
     * Retrieves all templates registered for a given category.
     *
     * @param category The [TemplateCategory] for which to retrieve templates.
     * @return An immutable list of [Template] instances. Returns an empty list if the category is not found or has no templates.
     */
    override fun getTemplatesFor(category: TemplateCategory): List<Template<*>> {
        return templatesByCategory[category]?.let { Collections.unmodifiableList(it.toList()) } ?: emptyList()
    }

    /**
     * Finds a specific template by its unique ID across all categories.
     *
     * @param templateId The unique ID of the template to find.
     * @return The found [Template] instance, or `null` if no template with the given ID exists.
     */
    override fun getTemplate(templateId: String): Template<*>? {
        return templatesByCategory.values.flatten().find { it.templateId == templateId }
    }

    /**
     * Reloads all templates by clearing the current cache and re-initializing them from the source.
     * This is useful if the template definitions have changed.
     */
    override fun reload() {
        release()
        initializeTemplates()
    }

    /**
     * Releases all resources held by the templates and clears all registered templates and categories.
     * This should be called to free up memory when the provider is no longer needed.
     */
    override fun release() {
        templatesByCategory.values.forEach { list ->
            list.forEach { it.release() }
        }
        templatesByCategory.clear()
    }
}