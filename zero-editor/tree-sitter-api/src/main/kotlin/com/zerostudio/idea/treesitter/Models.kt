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

package com.zerostudio.idea.treesitter

/**
 * @author android_zero
 *
 * 变更说明：
 * 映射 Tree-sitter 基础行列与点范围数据结构，完全零反射、内存友好。
 */
data class TSPoint(val row: Int, val column: Int)

data class TSRange(
    val startByte: Int,
    val endByte: Int,
    val startPoint: TSPoint,
    val endPoint: TSPoint
)

data class TSInputEdit(
    val startByte: Int,
    val oldEndByte: Int,
    val newEndByte: Int,
    val startPoint: TSPoint,
    val oldEndPoint: TSPoint,
    val newEndPoint: TSPoint
)

data class CodeSymbolTag(
    val startByte: Int,
    val endByte: Int,
    val nameStartByte: Int,
    val nameEndByte: Int,
    val lineStartByte: Int,
    val lineEndByte: Int,
    val startPoint: TSPoint,
    val syntaxTypeId: Int,
    val isDefinition: Boolean
)