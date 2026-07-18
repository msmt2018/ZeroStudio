package android.zero.studio.core.domain.provider

import android.zero.studio.core.data.local.provider.AppSeedColors
import android.zero.studio.core.data.local.provider.SeedColor

object SeedColorProvider {
    val seed = AppSeedColors.Color05.colors

    var primary: Int = seed.primary

    fun setSeedColor(seed: SeedColor) {
        primary = seed.primary
    }
}