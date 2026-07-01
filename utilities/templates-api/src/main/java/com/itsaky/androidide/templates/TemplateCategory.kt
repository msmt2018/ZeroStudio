package com.itsaky.androidide.templates

import androidx.annotation.DrawableRes
import com.itsaky.androidide.resources.R

/**
 * Defines the categories for project templates. Each category corresponds to a tab in the template
 * selection UI.
 *
 * @property key A unique identifier for the category.
 * @property title The title for the category to be displayed in the UI.
 * @property icon The optional drawable resource for the category icon.
 * @author android_zero
 * @since 2025-11-17
 *
 * Work Flow:
 * 1. Defines default template categories like Mobile, Wear, Tv, etc.
 * 2. Each category has a unique key, a display title, and an optional icon.
 * 3. These categories are used by TemplateListFragment to create tabs and organize templates.
 *
 * How to use: To add a new category, simply add a new `val` in the companion object: `val
 * MyCategory = TemplateCategory("my_key", "My Custom Category", R.drawable.ic_my_icon)`
 */
data class TemplateCategory(
    val key: String,
    val title: String,
    @DrawableRes val icon: Int? = null,
) {
  data class SubCategory(
      val parent: TemplateCategory,
      val key: String,
      val title: String,
      @DrawableRes val icon: Int? = null,
  )

  /**
   * @property Mobile "Phone and Tablet" category.
   * @property Wear "Wear OS" category.
   * @property Tv "Television" category.
   * @property Car "Automotive" category.
   * @property XR "XR" category.
   * @property Generic "Generic" category.
   *
   * Work Flow:
   * 1. Defines default template categories.
   * 2. Each category has a unique key, display title, and an optional icon.
   * 3. These categories will be used to create tabs in the template selection screen.
   */
  companion object {

    /** "Phone and Tablet" category. */
    val Mobile =
        TemplateCategory(
            "mobile",
            "AS Phone and Tablet",
            R.drawable.ic_template_devive_phones_tablets,
        )

    /** "Wear OS" category. */
    val Wear = TemplateCategory("wear", "AS Wear OS", R.drawable.ic_template_devive_smartwatch)

    /** "Television" category. */
    val Tv = TemplateCategory("tv", "AS Television", R.drawable.ic_template_devive_television)

    /** "Automotive" category. */
    val Car =
        TemplateCategory(
            "car",
            "AS Automotive",
            R.drawable.ic_template_devive_automotive_navigation_screen,
        )

    /** "XR" category for AR/VR applications. */
    val XR = TemplateCategory("xr", "AS XR", R.drawable.ic_template_devive_xr)

    /** "Generic" category for non-specific or multi-platform templates. */
    val Generic = TemplateCategory("generic", "Generic", R.drawable.ic_template_generic)

    /** Demo for managing native build projects such as NDK and CMake. * */
    val Native = TemplateCategory("native", "Native Build", R.drawable.cpp_configure)

    val BasicZeroStudio =
        TemplateCategory("ZeroStudio", "IDE Basic", R.drawable.ic_template_generic)

    val HybridFrameworks =
        TemplateCategory("HybridFrameworks", "Hybrid Frameworks", R.drawable.ic_template_generic)

    /** "Performance" category for baseline profiles, startup benchmarks, etc. */
    val Performance =
        TemplateCategory("performance", "Performance", R.drawable.ic_bolt_boost)

    private val subCategoriesByParent = linkedMapOf<TemplateCategory, MutableList<SubCategory>>()
    // A container used for comprehensively managing all objectives within a subcategory
    val All = SubCategory(parent = Generic, key = "all", title = "All")

    /*
     * Registered sub-category
     *
     * @parent Used for binding the main category
     * @key Registered subcategory field
     * @title Add subcategory title
     * @icon Subcategory icons are usually bound to the left side of the title
     */
    fun registerSubCategory(
        parent: TemplateCategory,
        key: String,
        title: String,
        @DrawableRes icon: Int? = null,
    ): SubCategory {
      val subCategory = SubCategory(parent = parent, key = key, title = title, icon = icon)
      val subCategories =
          subCategoriesByParent.getOrPut(parent) {
            mutableListOf<SubCategory>().apply { add(SubCategory(parent, All.key, All.title)) }
          }
      if (subCategories.none { it.key == key }) {
        subCategories.add(subCategory)
      }
      return subCategory
    }

    fun getSubCategories(parent: TemplateCategory): List<SubCategory> {
      return subCategoriesByParent[parent]?.toList()
          ?: listOf(SubCategory(parent, All.key, All.title))
    }

    /**
     * Returns a list of all default categories. It may affect the order of precedence
     *
     * @return A list of [TemplateCategory].
     */
    fun defaultCategories(): List<TemplateCategory> {
      return listOf(
          BasicZeroStudio,
          Performance,
          Mobile,
          Wear,
          Tv,
          Car,
          XR,
          Generic,
          Native,
          HybridFrameworks,
      )
    }
  }
}
