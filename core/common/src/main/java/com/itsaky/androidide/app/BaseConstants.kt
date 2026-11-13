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

package com.itsaky.androidide.app

import com.itsaky.androidide.buildinfo.BuildInfo

class BaseConstants {
  companion object {
    const val NOTIFICATION_GRADLE_BUILD_SERVICE = "17571"
    const val TELEGRAM_GROUP_URL = "https://t.me/zerostudioide"
    const val TELEGRAM_CHANNEL_URL = "https://t.me/android_zero_studio"
    const val SPONSOR_URL = "${BuildInfo.PROJECT_SITE}/donate"
    const val DOCS_URL = "https://docs.androidide.com"
    const val CONTRIBUTOR_GUIDE_URL =
        "${BuildInfo.REPO_URL}/blob/dev/CONTRIBUTING.md" // BuildInfo.REPO_URL +
    // "/blob/dev/CONTRIBUTING.md"
    const val EMAIL = "msmt8382@gmail.com"
    const val QQ_Group = "https://qm.qq.com/q/y0DCfWlEbu"

    /** ABI: arm64-v8a (64-bit) */
    const val ABI_ARM64_V8A = "arm64-v8a"
    /** ABI: armeabi-v7a (32-bit) */
    const val ABI_ARMEABI_V7A = "armeabi-v7a"
    /** ABI: x86 (32-bit) */
    const val ABI_X86 = "x86"
    /** ABI: x86_64 (64-bit) */
    const val ABI_X86_64 = "x86_64"

    /* java version*/
    const val DEFAULT_JAVA_VERSION_25 = "25"
    const val DEFAULT_JAVA_VERSION_24 = "24"
    const val DEFAULT_JAVA_VERSION_23 = "23"
    const val DEFAULT_JAVA_VERSION_22 = "22"
    const val DEFAULT_JAVA_VERSION_21 = "18"
    const val DEFAULT_JAVA_VERSION_17 = "17"
    const val DEFAULT_JAVA_VERSION_13 = "13"
    const val DEFAULT_JAVA_VERSION_11 = "11"
    const val DEFAULT_JAVA_VERSION_9 = "9"
    const val DEFAULT_JAVA_VERSION_8 = "8"
  }
}
