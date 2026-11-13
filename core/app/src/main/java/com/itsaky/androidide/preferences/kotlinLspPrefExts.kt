/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

package com.itsaky.androidide.preferences

import android.content.Context
import androidx.preference.Preference
import com.itsaky.androidide.R
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.lsp.kotlin.settings.KotlinServerSettings
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class KotlinLspPreferencesScreen(
    override val key: String = "idepref_kotlin_lsp",
    override val title: Int = string.idepref_kotlin_lsp_settings, // 临时复用，如有string定义请替换
    override val summary: Int? = null,
    override val icon: Int? = R.drawable.ic_language_java, // 可替换为 kotlin icon
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {
    init {
        addPreference(KotlinLazyCompilationPref())
        addPreference(KotlinInlayHintGroup())
        addPreference(KotlinDiagnosticsGroup())
    }
}

@Parcelize
private class KotlinLazyCompilationPref(
    override val key: String = "ide.kotlin.lazyCompilation",
    override val title: Int = string.idepref_kotlin_lazy_compilation,
    override val summary: Int? = null
) : SwitchPreference(
    setValue = { com.itsaky.androidide.preferences.internal.prefManager.putBoolean("ide.kotlin.lazyCompilation", it) },
    getValue = { com.itsaky.androidide.preferences.internal.prefManager.getBoolean("ide.kotlin.lazyCompilation", false) }
)

@Parcelize
private class KotlinInlayHintGroup(
    override val key: String = "idepref_kotlin_inlay_hints",
    override val title: Int = string.idepref_kotlin_inlay_hints,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
    init {
        addPreference(KotlinTypeHintPref())
        addPreference(KotlinParamHintPref())
        addPreference(KotlinChainedHintPref())
    }
}

@Parcelize
private class KotlinTypeHintPref(
    override val key: String = "ide.kotlin.inlayHints.type",
    override val title: Int = string.idepref_kotlin_type_hints,
) : SwitchPreference(
    setValue = { com.itsaky.androidide.preferences.internal.prefManager.putBoolean("ide.kotlin.inlayHints.type", it) },
    getValue = { com.itsaky.androidide.preferences.internal.prefManager.getBoolean("ide.kotlin.inlayHints.type", true) }
)

@Parcelize
private class KotlinParamHintPref(
    override val key: String = "ide.kotlin.inlayHints.parameter",
    override val title: Int = string.idepref_kotlin_parameter_hints,
) : SwitchPreference(
    setValue = { com.itsaky.androidide.preferences.internal.prefManager.putBoolean("ide.kotlin.inlayHints.parameter", it) },
    getValue = { com.itsaky.androidide.preferences.internal.prefManager.getBoolean("ide.kotlin.inlayHints.parameter", false) }
)

@Parcelize
private class KotlinChainedHintPref(
    override val key: String = "ide.kotlin.inlayHints.chained",
    override val title: Int = string.idepref_kotlin_chained_hints,
) : SwitchPreference(
    setValue = { com.itsaky.androidide.preferences.internal.prefManager.putBoolean("ide.kotlin.inlayHints.chained", it) },
    getValue = { com.itsaky.androidide.preferences.internal.prefManager.getBoolean("ide.kotlin.inlayHints.chained", false) }
)

@Parcelize
private class KotlinDiagnosticsGroup(
    override val key: String = "idepref_kotlin_diagnostics",
    override val title: Int = string.idepref_kotlin_diagnostics,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {
    init {
        addPreference(KotlinRemoveUnusedImportsPref())
    }
}

@Parcelize
private class KotlinRemoveUnusedImportsPref(
    override val key: String = "ide.kotlin.removeUnusedImports",
    override val title: Int = string.idepref_kotlin_remove_unused_imports,
) : SwitchPreference(
    setValue = { com.itsaky.androidide.preferences.internal.prefManager.putBoolean("ide.kotlin.removeUnusedImports", it) },
    getValue = { com.itsaky.androidide.preferences.internal.prefManager.getBoolean("ide.kotlin.removeUnusedImports", true) }
)