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

package com.itsaky.androidide.actions

/**
 * 一个通用的样式约束类，用于定义Action按钮在UI中的外观。
 *
 * 通过在 [BaseEditorAction] 子类中设置此对象，可以自定义特定Action的显示样式。 UI渲染逻辑会读取这些属性并应用到对应的View上。
 *
 * @author: android_zero
 *
 * @property textSizeSp 字体大小，单位为 sp。如果为 null，则使用默认大小。
 * @property paddingHorizontalDp 水平内边距，单位为 dp。如果为 null，则使用默认内边距。
 * @property minWidthDp 最小宽度，单位为 dp。如果为 null，则不设置最小宽度。
 * @property typefaceStyle 字体样式，例如 Typeface.NORMAL, Typeface.BOLD。
 */
data class ActionStyle(
    val textSizeSp: Float? = null,
    val paddingHorizontalDp: Int? = null,
    val minWidthDp: Int? = null,
    val typefaceStyle: Int? = null,
)
