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

package com.itsaky.androidide.ui.themes

import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.compose.material3.ColorScheme
import com.itsaky.androidide.resources.R

/**
 * Themes in AndroidIDE.
 *
 * @author Akash Yadav
 * @author android_zero
 */
enum class IDETheme(
    @StyleRes val styleLight: Int,
    @StyleRes val styleDark: Int,
    @StringRes val title: Int,
    val isDynamic: Boolean = false,
    val schemeLight: ColorScheme? = null,
    val schemeDark: ColorScheme? = null,
) {

  /** Blue Wave theme. */
  BLUE_WAVE(
      R.style.Theme_AndroidIDE_BlueWave,
      R.style.Theme_AndroidIDE_BlueWave_Dark,
      R.string.theme_blue_wave,
  ),

  /** Sunny Glow theme. */
  SUNNY_GLOW(
      R.style.Theme_AndroidIDE_SunnyGlow,
      R.style.Theme_AndroidIDE_SunnyGlow_Dark,
      R.string.theme_sunny_glow,
  ),

  /** VSCode theme. */
  STYLE_VSCODE(
      R.style.Theme_AndroidIDE_VSCode,
      R.style.Theme_AndroidIDE_VSCode_Dark,
      R.string.theme_vscode,
  ),

  /** Crimson Red theme. */
  CRIMSON_RED(
      R.style.Theme_AndroidIDE_CrimsonRed,
      R.style.Theme_AndroidIDE_CrimsonRed_Dark,
      R.string.theme_crimson_red,
  ),

  /** Royal Amethyst theme. */
  ROYAL_AMETHYST(
      R.style.Theme_AndroidIDE_RoyalAmethyst,
      R.style.Theme_AndroidIDE_RoyalAmethyst_Dark,
      R.string.theme_royal_amethyst,
  ),

  /** Minty Fresh theme. */
  MINTY_FRESH(
      R.style.Theme_AndroidIDE_MintyFresh,
      R.style.Theme_AndroidIDE_MintyFresh_Dark,
      R.string.theme_minty_fresh,
  ),

  /** Slate Grey theme. */
  SLATE_GREY(
      R.style.Theme_AndroidIDE_SlateGrey,
      R.style.Theme_AndroidIDE_SlateGrey_Dark,
      R.string.theme_slate_grey,
  ),

  /** Sakura Pink theme. */
  SAKURA_PINK(
      R.style.Theme_AndroidIDE_SakuraPink,
      R.style.Theme_AndroidIDE_SakuraPink_Dark,
      R.string.theme_sakura_pink,
  ),

  /** Cyber Neon theme. */
  CYBER_NEON(
      R.style.Theme_AndroidIDE_CyberNeon,
      R.style.Theme_AndroidIDE_CyberNeon_Dark,
      R.string.theme_cyber_neon,
  ),

  /** Autumn Breeze theme. */
  AUTUMN_BREEZE(
      R.style.Theme_AndroidIDE_AutumnBreeze,
      R.style.Theme_AndroidIDE_AutumnBreeze_Dark,
      R.string.theme_autumn_breeze,
  ),

  /** Nordic Frost theme. */
  NORDIC_FROST(
      R.style.Theme_AndroidIDE_NordicFrost,
      R.style.Theme_AndroidIDE_NordicFrost_Dark,
      R.string.theme_nordic_frost,
  ),

  /** Oceanic Abyss theme. */
  OCEANIC_ABYSS(
      R.style.Theme_AndroidIDE_OceanicAbyss,
      R.style.Theme_AndroidIDE_OceanicAbyss_Dark,
      R.string.theme_oceanic_abyss,
  ),

  /** Volcanic Magma theme. */
  VOLCANIC_MAGMA(
      R.style.Theme_AndroidIDE_VolcanicMagma,
      R.style.Theme_AndroidIDE_VolcanicMagma_Dark,
      R.string.theme_volcanic_magma,
  ),

  /** Midnight Sun theme. */
  MIDNIGHT_SUN(
      R.style.Theme_AndroidIDE_MidnightSun,
      R.style.Theme_AndroidIDE_MidnightSun_Dark,
      R.string.theme_midnight_sun,
  ),

  /** Forest Moss theme. */
  FOREST_MOSS(
      R.style.Theme_AndroidIDE_ForestMoss,
      R.style.Theme_AndroidIDE_ForestMoss_Dark,
      R.string.theme_forest_moss,
  ),
  /** Coffee Roast theme. */
  COFFEE_ROAST(
      R.style.Theme_AndroidIDE_CoffeeRoast,
      R.style.Theme_AndroidIDE_CoffeeRoast_Dark,
      R.string.theme_coffee_roast,
  ),

  /** Deep Space theme. */
  DEEP_SPACE(
      R.style.Theme_AndroidIDE_DeepSpace,
      R.style.Theme_AndroidIDE_DeepSpace_Dark,
      R.string.theme_deep_space,
  ),

  /** Neon Synth theme. */
  NEON_SYNTH(
      R.style.Theme_AndroidIDE_NeonSynth,
      R.style.Theme_AndroidIDE_NeonSynth_Dark,
      R.string.theme_neon_synth,
  ),

  /** Toxic Lime theme. */
  TOXIC_LIME(
      R.style.Theme_AndroidIDE_ToxicLime,
      R.style.Theme_AndroidIDE_ToxicLime_Dark,
      R.string.theme_toxic_lime,
  ),

  /** Lavender Blush theme. */
  LAVENDER_BLUSH(
      R.style.Theme_AndroidIDE_LavenderBlush,
      R.style.Theme_AndroidIDE_LavenderBlush_Dark,
      R.string.theme_lavender_blush,
  ),

  /** Coral Reef theme. */
  CORAL_REEF(
      R.style.Theme_AndroidIDE_CoralReef,
      R.style.Theme_AndroidIDE_CoralReef_Dark,
      R.string.theme_coral_reef,
  ),

  /** Azure Sky theme. */
  AZURE_SKY(
      R.style.Theme_AndroidIDE_AzureSky,
      R.style.Theme_AndroidIDE_AzureSky_Dark,
      R.string.theme_azure_sky,
  ),

  /** Ink Paper theme. */
  INK_PAPER(
      R.style.Theme_AndroidIDE_InkPaper,
      R.style.Theme_AndroidIDE_InkPaper_Dark,
      R.string.theme_ink_paper,
  ),

  /** Golden Hour theme. */
  GOLDEN_HOUR(
      R.style.Theme_AndroidIDE_GoldenHour,
      R.style.Theme_AndroidIDE_GoldenHour_Dark,
      R.string.theme_golden_hour,
  ),

  /** Electric Violet theme. */
  ELECTRIC_VIOLET(
      R.style.Theme_AndroidIDE_ElectricViolet,
      R.style.Theme_AndroidIDE_ElectricViolet_Dark,
      R.string.theme_electric_violet,
  ),

  /** Zen Matcha theme. */
  ZEN_MATCHA(
      R.style.Theme_AndroidIDE_ZenMatcha,
      R.style.Theme_AndroidIDE_ZenMatcha_Dark,
      R.string.theme_zen_matcha,
  ),

  /** Arctic Ice theme. */
  ARCTIC_ICE(
      R.style.Theme_AndroidIDE_ArcticIce,
      R.style.Theme_AndroidIDE_ArcticIce_Dark,
      R.string.theme_arctic_ice,
  ),

  /** Carbon Red theme. */
  CARBON_RED(
      R.style.Theme_AndroidIDE_CarbonRed,
      R.style.Theme_AndroidIDE_CarbonRed_Dark,
      R.string.theme_carbon_red,
  ),

  /** Monet Rose theme. */
  MONET_ROSE(
      R.style.Theme_AndroidIDE_MonetRose,
      R.style.Theme_AndroidIDE_MonetRose_Dark,
      R.string.theme_monet_rose,
  ),

  /** Monet Peach theme. */
  MONET_PEACH(
      R.style.Theme_AndroidIDE_MonetPeach,
      R.style.Theme_AndroidIDE_MonetPeach_Dark,
      R.string.theme_monet_peach,
  ),

  /** Monet Amber theme. */
  MONET_AMBER(
      R.style.Theme_AndroidIDE_MonetAmber,
      R.style.Theme_AndroidIDE_MonetAmber_Dark,
      R.string.theme_monet_amber,
  ),

  /** Monet Lime theme. */
  MONET_LIME(
      R.style.Theme_AndroidIDE_MonetLime,
      R.style.Theme_AndroidIDE_MonetLime_Dark,
      R.string.theme_monet_lime,
  ),

  /** Monet Sage theme. */
  MONET_SAGE(
      R.style.Theme_AndroidIDE_MonetSage,
      R.style.Theme_AndroidIDE_MonetSage_Dark,
      R.string.theme_monet_sage,
  ),

  /** Monet Teal theme. */
  MONET_TEAL(
      R.style.Theme_AndroidIDE_MonetTeal,
      R.style.Theme_AndroidIDE_MonetTeal_Dark,
      R.string.theme_monet_teal,
  ),

  /** Monet Sky theme. */
  MONET_SKY(
      R.style.Theme_AndroidIDE_MonetSky,
      R.style.Theme_AndroidIDE_MonetSky_Dark,
      R.string.theme_monet_sky,
  ),

  /** Monet Indigo theme. */
  MONET_INDIGO(
      R.style.Theme_AndroidIDE_MonetIndigo,
      R.style.Theme_AndroidIDE_MonetIndigo_Dark,
      R.string.theme_monet_indigo,
  ),

  /** Monet Plum theme. */
  MONET_PLUM(
      R.style.Theme_AndroidIDE_MonetPlum,
      R.style.Theme_AndroidIDE_MonetPlum_Dark,
      R.string.theme_monet_plum,
  ),

  /** Monet Fuchsia theme. */
  MONET_FUCHSIA(
      R.style.Theme_AndroidIDE_MonetFuchsia,
      R.style.Theme_AndroidIDE_MonetFuchsia_Dark,
      R.string.theme_monet_fuchsia,
  ),

  /** MTGlacialBlue Theme */
  MTGLACIAlBLUE(
      R.style.Theme_AndroidIDE_MTGlacialBlue,
      R.style.Theme_AndroidIDE_MTGlacialBlue_Dark,
      R.string.theme_mtglacial_blue,
      isDynamic = false, // Explicitly set false
      schemeLight = mTGlacialBlueLightColorScheme,
      schemeDark = mTGlacialBlueDarkColorScheme,
  ),

  /** Material You theme (System Wallpaper). */
  MATERIAL_YOU(-1, -1, R.string.theme_material_you, isDynamic = true);

  companion object {
    /** The default theme. */
    val DEFAULT = MONET_FUCHSIA

    // Cache names map for O(1) lookup to avoid Enum.valueOf exceptions
    private val map: Map<String, IDETheme> by lazy { entries.associateBy { it.name } }

    /** Safe valueOf that doesn't throw exceptions and is faster. */
    fun safeValueOf(name: String?): IDETheme {
      if (name.isNullOrBlank()) return DEFAULT
      return map[name] ?: DEFAULT
    }
  }
}
