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


import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.tasks.ZeroAutoTranslateTask

plugins {
  id("com.android.library")
}



android {
  namespace = "${BuildConfig.packageName}.resources"
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.splashscreen)
  implementation(libs.google.material)
  api(libs.androidx.nav.ui)
  api(libs.androidx.nav.fragment)
}

// 运行 ./gradlew translateStrings 来执行翻译
// @author android_zero  github：android-zeros
tasks.register<ZeroAutoTranslateTask>("translateStrings") {
    // 设置翻译源文件路径
    sourceXmlPath = "core/resources/src/main/res/values/strings.xml"

     //注意：目前谷歌翻译并非常用官方API，所以可能会存在封ip/无响应等问题导致翻译失败
    // 设置翻译引擎 (可选: GOOGLE_GTX, GOOGLE_WEB, BAIDU_WEB, YOUDAO_WEB, BING_WEB)
    translationEngine = "BING_WEB"

    // 设置输出目录
    // 翻译结果会生成在: ＄Projects_Root_Dir/StringTranslation/values-xx/strings.xml
    translationOutputDirName = "StringTranslation"

    // 设置原始文件备份目录
    // 原始文件会备份在: 项目根目录/StringTranslation/backup/values-xx/strings.xml
    originalFileBackupDirName = "StringTranslation/backup"
}