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

package com.itsaky.androidide.models

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.blankj.utilcode.util.ImageUtils
import com.itsaky.androidide.resources.R
import java.io.File
import java.util.Locale

/**
 * Info about file extensions in the file tree view. 定义文件树视图中各种文件类型的扩展名、图标和属性。
 *
 * 1756-1800种File Mime Type ：https://mime.wcode.net/zh-hans →
 * core/common/src/main/java/com/itsaky/androidide/file/MimeTypeConstants.kt
 *
 * @property extension The file extension string. (文件扩展名字符串)
 * @property icon The drawable resource ID for the file icon. (文件图标的资源ID)
 * @property tintAttr The theme attribute ID to tint the icon with (e.g.). If 0, the icon retains
 *   its original color. (用于着色图标的主题属性ID，0表示保持原色)
 * @author Akash Yadav (早期贡献者，提供基础源码)
 * @author android_zero
 */
enum class FileExtension(
    val extension: String,
    @DrawableRes val icon: Int,
    @AttrRes val tintAttr: Int = 0, // Default to 0 (no tint)
) {
  // Java related - keeping original colors mostly
  JAVA("java", R.drawable.ic_file_type_text_java),
  JSP_JAVA("jsp", R.drawable.ic_file_type_text_java),
  JAV_JAVA("jav", R.drawable.ic_file_type_text_java),
  BSH_JAVA("bsh", R.drawable.ic_file_type_text_java),

  // Kotlin related
  KT("kt", R.drawable.ic_file_type_text_k_kotlins),
  KTM("ktm", R.drawable.ic_file_type_text_k_kotlins),
  KTS("kts", R.drawable.ic_file_type_text_kotlin_gradle_script_light),

  // xml类型文件： xml", "xaml", "dtd", "plist", "ascx", "csproj", "wxi", "wxl", "wxs", "svg
  XML("xml", R.drawable.ic_file_type_xml),
  XML_XAML("xaml", R.drawable.ic_file_type_xml),
  XML_DTD("dtd", R.drawable.ic_file_type_xml),
  XML_PLIST("plist", R.drawable.ic_file_type_xml),
  XMLASCX("ascx", R.drawable.ic_file_type_xml),
  XML_CSPROJ("csproj", R.drawable.ic_file_type_code),
  XML_WXI("wxi", R.drawable.ic_file_type_xml),
  XML_WXL("wxl", R.drawable.ic_file_type_xml),
  XML_WXS("wxs", R.drawable.ic_file_type_xml),
  // XML("svg", R.drawable.ic_file_type_xml),

  // Groovy：gsh", "groovy", "gradle", "gvy", "gy
  GRADLE_GROOVY("gradle", R.drawable.ic_file_type_text_gradle),
  GSH_GROOVY("gsh", R.drawable.ic_file_type_text_gradle),
  GVY_GROOVY("gvy", R.drawable.ic_file_type_text_gradle),
  GY_GROOVY("gy", R.drawable.ic_file_type_text_gradle),
  GROOVY("groovy", R.drawable.ic_file_type_text_gradle),

  // Example: Tinting properties file with colorPrimary
  PROPERTIES("properties", R.drawable.ic_file_type_text_setting_info),
  TEXT("txt", R.drawable.ic_file_type_txt_document_file),

  // Markdown类型文件
  MD_MARKDOWN("md", R.drawable.ic_file_type_code),
  MARKDOWN_MARKDOWN("markdown", R.drawable.ic_file_type_code),
  MDOWN_MARKDOWN("mdown", R.drawable.ic_file_type_code),
  MKD_MARKDOWN("mkd", R.drawable.ic_file_type_code),
  MKDN_MARKDOWN("mkdn", R.drawable.ic_file_type_code),
  MDOC_MARKDOWN("mdoc", R.drawable.ic_file_type_code),
  MDTEXT_MARKDOWN("mdtext", R.drawable.ic_file_type_code),
  MDTXT_MARKDOWN("mdtxt", R.drawable.ic_file_type_code),
  MDWN_MARKDOWN("mdwn", R.drawable.ic_file_type_code),
  LOG("log", R.drawable.ic_file_type_log),
  BACKUP_FILE("bak", R.drawable.ic_file_type_backup_files),

  // Config & Data
  JSON("json", R.drawable.ic_file_type_json),
  JSONL("jsonl", R.drawable.ic_file_type_json),
  JSONC("jsonc", R.drawable.ic_file_type_json),

  // Web Development & Scripting (Expanded)
  HTML_WEB("html", R.drawable.ic_file_type_html),
  HTML_XHT_WEB("xht", R.drawable.ic_file_type_html),
  HTM_WEB("htm", R.drawable.ic_file_type_html),
  HTM_HTMX_WEB("htmx", R.drawable.ic_file_type_html),
  XHTML_WEB("xhtml", R.drawable.ic_file_type_html),
  CSS_WEB("css", R.drawable.ic_file_type_css),
  SCSS_WEB("scss", R.drawable.ic_file_type_css),
  SASS_WEB("sass", R.drawable.ic_file_type_css),
  LESS_WEB("less", R.drawable.ic_file_type_css),
  STYL_WEB("styl", R.drawable.ic_file_type_css),
  JAVASCRIPT("js", R.drawable.ic_file_type_language_golang),
  MJS_JAVASCRIPT("mjs", R.drawable.ic_file_type_text_terminal_script),
  CJS_JAVASCRIPT("cjs", R.drawable.ic_file_type_text_terminal_script),
  MUT_JAVASCRIPT("mut", R.drawable.ic_file_type_text_terminal_script),
  JSCSRC_JAVASCRIPT("jscsrc", R.drawable.ic_file_type_text_terminal_script),
  JSHINTRC_JAVASCRIPT("jshintrc", R.drawable.ic_file_type_text_terminal_script),
  TS_TYPESCPIPT("ts", R.drawable.ic_file_type_code), // TypeScript
  MTS_JAVASCPIPT("mts", R.drawable.ic_file_type_code), // TypeScript
  TSX_TYPESCPIPT("tsx", R.drawable.ic_file_type_code),
  JSX_TYPESCPIPT("jsx", R.drawable.ic_file_type_code),
  CTS_TYPESCPIPT("cts", R.drawable.ic_file_type_code),
  VUE_WEB("vue", R.drawable.ic_file_type_html),
  SVELTE_WEB("svelte", R.drawable.ic_file_type_html),
  ASP_WEB("asp", R.drawable.ic_file_type_code),
  ASPX_WEB("aspx", R.drawable.ic_file_type_code),
  PHP_FILE_WEB("php", R.drawable.ic_file_type_php),
  PHPSAN_WEB("php3", R.drawable.ic_file_type_php),
  PHPSI_WEB("php4", R.drawable.ic_file_type_php),
  PHPWU_WEB("php5", R.drawable.ic_file_type_php),
  PHTML_WEB("phtml", R.drawable.ic_file_type_php),
  WASM_WEB("wasm", R.drawable.ic_file_type_binary),

  // Documents & Office Formats
  PDF("pdf", R.drawable.ic_file_type_word_document),
  DOC("doc", R.drawable.ic_file_type_word_document),
  DOCX("docx", R.drawable.ic_file_type_word_document),
  DOT("dot", R.drawable.ic_file_type_word_document),
  DOTX("dotx", R.drawable.ic_file_type_word_document),
  DOCM("docm", R.drawable.ic_file_type_word_document),
  XLS("xls", R.drawable.ic_file_type_word_document),
  XLSX("xlsx", R.drawable.ic_file_type_word_document),
  XLSM("xlsm", R.drawable.ic_file_type_word_document),
  XLT("xlt", R.drawable.ic_file_type_word_document),
  PPT("ppt", R.drawable.ic_file_type_word_document),
  PPTX("pptx", R.drawable.ic_file_type_word_document),
  PPS("pps", R.drawable.ic_file_type_word_document),
  PPSX("ppsx", R.drawable.ic_file_type_word_document),
  ODT("odt", R.drawable.ic_file_type_word_document),
  ODS("ods", R.drawable.ic_file_type_word_document),
  ODP("odp", R.drawable.ic_file_type_word_document),
  RTF("rtf", R.drawable.ic_file_type_txt_document_file),
  TEX("tex", R.drawable.ic_file_type_code), // LaTeX
  BIB("bib", R.drawable.ic_file_type_code), // BibTeX
  EPUB("epub", R.drawable.ic_file_type_compressed_archive), // Actually zip based
  MOBI("mobi", R.drawable.ic_file_type_binary),
  AZWSAN("azw3", R.drawable.ic_file_type_binary),

  // Fonts
  TTF("ttf", R.drawable.ic_custom_font),
  OTF("otf", R.drawable.ic_custom_font),
  WOFF("woff", R.drawable.ic_custom_font),
  WOFFER("woff2", R.drawable.ic_custom_font),
  EOT("eot", R.drawable.ic_custom_font),
  TTC("ttc", R.drawable.ic_custom_font),
  PFB("pfb", R.drawable.ic_custom_font),
  PFM("pfm", R.drawable.ic_custom_font),

  // Database & Data
  SQL("sql", R.drawable.ic_file_type_text_mysql),
  DB("db", R.drawable.ic_file_type_text_sqlite), // Changed to sqlite
  SQLITE("sqlite", R.drawable.ic_file_type_text_sqlite), // Changed to sqlite
  SQLITESAN("sqlite3", R.drawable.ic_file_type_text_sqlite), // Changed to sqlite
  MDB("mdb", R.drawable.ic_file_type_text_mysql),
  ACCDB("accdb", R.drawable.ic_file_type_text_mysql),
  NDF("ndf", R.drawable.ic_file_type_text_mysql),
  LDF("ldf", R.drawable.ic_file_type_text_mysql),
  MYD("myd", R.drawable.ic_file_type_text_mysql),
  MYI("myi", R.drawable.ic_file_type_text_mysql),
  DBF("dbf", R.drawable.ic_file_type_text_mysql),
  CSV("csv", R.drawable.ic_file_type_txt_document_file),
  TSV("tsv", R.drawable.ic_file_type_txt_document_file),
  PARQUET("parquet", R.drawable.ic_file_type_text_mysql),
  AVRO("avro", R.drawable.ic_file_type_text_mysql),

  // Audio
  MPSAN("mp3", R.drawable.ic_file_type_music),
  WAV("wav", R.drawable.ic_file_type_music),
  OGG("ogg", R.drawable.ic_file_type_music),
  FLAC("flac", R.drawable.ic_file_type_music),
  AAC("aac", R.drawable.ic_file_type_music),
  M4A("m4a", R.drawable.ic_file_type_music),
  WMA("wma", R.drawable.ic_file_type_music),
  AIFF("aiff", R.drawable.ic_file_type_music),
  APE("ape", R.drawable.ic_file_type_music),
  MID("mid", R.drawable.ic_file_type_music),
  MIDI("midi", R.drawable.ic_file_type_music),
  OPUS("opus", R.drawable.ic_file_type_music),
  AMR("amr", R.drawable.ic_file_type_music),
  PCM("pcm", R.drawable.ic_file_type_music),

  // Video
  MPSVIDEO("mp4", R.drawable.ic_file_type_video),
  MKV("mkv", R.drawable.ic_file_type_video),
  AVI("avi", R.drawable.ic_file_type_video),
  MOV("mov", R.drawable.ic_file_type_video),
  WMV("wmv", R.drawable.ic_file_type_video),
  FLV("flv", R.drawable.ic_file_type_video),
  WEBM("webm", R.drawable.ic_file_type_video),
  MSVVIDEO("m4v", R.drawable.ic_file_type_video),
  MPG("mpg", R.drawable.ic_file_type_video),
  MPEG("mpeg", R.drawable.ic_file_type_video),
  M2TS("m2ts", R.drawable.ic_file_type_video),
  TSVIDEO("ts", R.drawable.ic_file_type_video),
  GPSVIDEO("3gp", R.drawable.ic_file_type_video),
  VOB("vob", R.drawable.ic_file_type_video),
  OGV("ogv", R.drawable.ic_file_type_video),

  // Graphics & Design (Raster + Vector + Specific)
  JPG("jpg", R.drawable.ic_file_type_image),
  JPEG("jpeg", R.drawable.ic_file_type_image),
  JPE("jpe", R.drawable.ic_file_type_image),
  PNG("png", R.drawable.ic_file_type_image),
  GIF("gif", R.drawable.ic_file_type_image),
  BMP("bmp", R.drawable.ic_file_type_image),
  WEBP("webp", R.drawable.ic_file_type_image),
  HEIC("heic", R.drawable.ic_file_type_image),
  HEIF("heif", R.drawable.ic_file_type_image),
  AVIF("avif", R.drawable.ic_file_type_image),
  ICO("ico", R.drawable.ic_file_type_image),
  CUR("cur", R.drawable.ic_file_type_image),
  SVG("svg", R.drawable.ic_file_type_image),
  SVGZ("svgz", R.drawable.ic_file_type_image),
  PSD("psd", R.drawable.ic_file_type_image), // Photoshop
  AIIMAGE("ai", R.drawable.ic_file_type_image), // Illustrator
  EPS("eps", R.drawable.ic_file_type_image),
  CDR("cdr", R.drawable.ic_file_type_image), // CorelDraw
  INDD("indd", R.drawable.ic_file_type_image),
  TIFF("tiff", R.drawable.ic_file_type_image),
  TIF("tif", R.drawable.ic_file_type_image),
  RAW("raw", R.drawable.ic_file_type_image),
  CRERIMAGE("cr2", R.drawable.ic_file_type_image), // Canon
  NEF("nef", R.drawable.ic_file_type_image), // Nikon
  ORF("orf", R.drawable.ic_file_type_image), // Olympus
  SRERIMAGE("sr2", R.drawable.ic_file_type_image), // Sony
  ARW("arw", R.drawable.ic_file_type_image),
  RWERIMAGE("rw2", R.drawable.ic_file_type_image),
  DNG("dng", R.drawable.ic_file_type_image),

  // 3D Models
  OBJ("obj", R.drawable.ic_file_type_binary),
  FBX("fbx", R.drawable.ic_file_type_binary),
  GLTF("gltf", R.drawable.ic_file_type_json),
  GLB("glb", R.drawable.ic_file_type_binary),
  BLEND("blend", R.drawable.ic_file_type_binary),
  STL("stl", R.drawable.ic_file_type_binary),
  DAE("dae", R.drawable.ic_file_type_xml),
  PLY("ply", R.drawable.ic_file_type_binary),

  // binary file
  ELF_FILE("so", R.drawable.ic_file_type_binary),
  BIN_FILE("bin", R.drawable.ic_file_type_binary),
  LOCK_FILE("lock", R.drawable.ic_file_type_binary),
  DAT_FILE("dat", R.drawable.ic_file_type_binary),
  CLASS_FILE("class", R.drawable.ic_file_type_binary),
  DEX_FILE("dex", R.drawable.ic_file_type_compressed_android),
  ODEX_FILE("odex", R.drawable.ic_file_type_compressed_android),
  VDEX_FILE("vdex", R.drawable.ic_file_type_compressed_android),

  // === Images (raster + animated + vector) ===
  // Raster
  PNG_IMAGE("png", R.drawable.ic_file_type_image),
  JPG_IMAGE("jpg", R.drawable.ic_file_type_image),
  JPEG_IMAGE("jpeg", R.drawable.ic_file_type_image),
  JPE_IMAGE("jpe", R.drawable.ic_file_type_image),
  JFIF_IMAGE("jfif", R.drawable.ic_file_type_image),
  WEBP_IMAGE("webp", R.drawable.ic_file_type_image),
  BMP_IMAGE("bmp", R.drawable.ic_file_type_image),
  DIB_IMAGE("dib", R.drawable.ic_file_type_image),
  HEIF_IMAGE("heif", R.drawable.ic_file_type_image),
  HEIC_IMAGE("heic", R.drawable.ic_file_type_image),
  HIF_IMAGE("hif", R.drawable.ic_file_type_image),
  TIFF_IMAGE("tiff", R.drawable.ic_file_type_image),
  TIF_IMAGE("tif", R.drawable.ic_file_type_image),
  ICO_IMAGE("ico", R.drawable.ic_file_type_image),
  CUR_IMAGE("cur", R.drawable.ic_file_type_image),
  APNG_IMAGE("apng", R.drawable.ic_file_type_image),
  AVIF_IMAGE("avif", R.drawable.ic_file_type_image),
  JP2_IMAGE("jp2", R.drawable.ic_file_type_image),
  J2K_IMAGE("j2k", R.drawable.ic_file_type_image),
  JPF_IMAGE("jpf", R.drawable.ic_file_type_image),
  JPX_IMAGE("jpx", R.drawable.ic_file_type_image),
  WBMP_IMAGE("wbmp", R.drawable.ic_file_type_image),
  // Animated
  GIF_IMAGE("gif", R.drawable.ic_file_type_image),
  // Vector
  SVG_IMAGE("svg", R.drawable.ic_file_type_image),
  SVGZ_IMAGE("svgz", R.drawable.ic_file_type_image),
  // Android vector drawable — also matches generic .xml files.  The image preview
  // fragment distinguishes by looking at file content (`<vector>` root).  We do NOT
  // add a separate enum entry for .xml because FileExtension uses extension as the
  // key (case-insensitive) and AndroidManifest.xml and friends already map to
  // FileExtension.XML — adding another would silently override the icon for
  // every XML file in the project tree.  See ImagePreviewFragment.supports().

  // 各操作系统的各类压缩包，二进制压缩包，软件包，程序包
  ZIP_FILE("zip", R.drawable.ic_file_type_compressed_archive),
  RAR_FILE("rar", R.drawable.ic_file_type_compressed_archive),
  SEVEN_ZIP_FILE("7z", R.drawable.ic_file_type_compressed_archive),
  S7Z_FILE("s7z", R.drawable.ic_file_type_compressed_archive),
  ZIPX_FILE("zipx", R.drawable.ic_file_type_compressed_archive),
  REV_FILE("rev", R.drawable.ic_file_type_compressed_archive),
  GZ_FILE("gz", R.drawable.ic_file_type_compressed_archive),
  BZIP_FILE("bz2", R.drawable.ic_file_type_compressed_archive),
  XZIP_FILE("xz", R.drawable.ic_file_type_compressed_archive),
  LZMAZIP_FILE("lzma", R.drawable.ic_file_type_compressed_archive),
  ZSTZIP_FILE("zst", R.drawable.ic_file_type_compressed_archive),
  LZSIZIP_FILE("lz4", R.drawable.ic_file_type_compressed_archive),
  LZOZIP_FILE("lzo", R.drawable.ic_file_type_compressed_archive),
  SZNPPYZIP_FILE("sznppy", R.drawable.ic_file_type_compressed_archive),
  TGZIP_FILE("tgz", R.drawable.ic_file_type_compressed_archive),
  TARBZERZIP_FILE("tar.bz2", R.drawable.ic_file_type_compressed_archive),
  TARGZIP_FILE("tar.gz", R.drawable.ic_file_type_compressed_archive),
  TARXZIP_FILE("tar.xz", R.drawable.ic_file_type_compressed_archive),
  TARZZIP_FILE("tar.z", R.drawable.ic_file_type_compressed_archive),
  TARZIP_FILE("tar", R.drawable.ic_file_type_compressed_archive),
  BINARY_ZSTD_FILE("zstd", R.drawable.ic_file_type_compressed_archive),
  TBZZIP_FILE("tbz2", R.drawable.ic_file_type_compressed_archive),
  TBZIP_FILE("tb2", R.drawable.ic_file_type_compressed_archive),
  TXZIP_FILE("txz", R.drawable.ic_file_type_compressed_archive),
  TARLZMA_FILE("tar.lzma", R.drawable.ic_file_type_compressed_archive),
  TARZST_FILE("tar.zst", R.drawable.ic_file_type_compressed_archive),
  TARLZ7_FILE("tar.lz4", R.drawable.ic_file_type_compressed_archive),
  TARLZO_FILE("tar.lzo", R.drawable.ic_file_type_compressed_archive),
  TARBIN_FILE("tar.bin", R.drawable.ic_file_type_compressed_archive),
  TARSNAPPY_FILE("tar.snappy", R.drawable.ic_file_type_compressed_archive),
  TAR7ZIP_FILE("tar.7z", R.drawable.ic_file_type_compressed_archive),
  XARZIP_FILE("xar", R.drawable.ic_file_type_compressed_archive),
  CPIOZIP_FILE("cpio", R.drawable.ic_file_type_compressed_archive),
  CPIOGZIP_FILE("cpio.gz", R.drawable.ic_file_type_compressed_archive),
  CPIOXZIP_FILE("cpio.xz", R.drawable.ic_file_type_compressed_archive),

  // 二进制压缩包  > 大多都是比如EXE，ISO，apk，deb，bin...
  BINARY_ELFGZIP_FILE("elf.gz", R.drawable.ic_file_type_compressed_archive),
  BINARY_ELFXZIP_FILE("elf.xz", R.drawable.ic_file_type_compressed_archive),
  CABZIP_FILE("cab", R.drawable.ic_file_type_compressed_archive),
  ARCZIP_FILE("arc", R.drawable.ic_file_type_compressed_archive),
  ARJZIP_FILE("arj", R.drawable.ic_file_type_compressed_archive),
  ACEZIP_FILE("ace", R.drawable.ic_file_type_compressed_archive),
  ALZZIP_FILE("alz", R.drawable.ic_file_type_compressed_archive),
  LZHZIP_FILE("lzh", R.drawable.ic_file_type_compressed_archive),
  LHAZIP_FILE("lha", R.drawable.ic_file_type_compressed_archive),
  ISOZIP_FILE("iso", R.drawable.ic_file_type_compressed_archive),
  IMG_FILE("img", R.drawable.ic_file_type_binary),
  VHD_FILE("vhd", R.drawable.ic_file_type_binary),
  VMDK_FILE("vmdk", R.drawable.ic_file_type_binary),
  QCOWER_FILE("qcow2", R.drawable.ic_file_type_binary),
  WIMZIP_FILE("wim", R.drawable.ic_file_type_compressed_archive),
  ESDZIP_FILE("esd", R.drawable.ic_file_type_compressed_archive),
  WINDOWS_BINARY_PACKAGE_EXEZIP_FILE("exe", R.drawable.ic_file_type_compressed_archive),
  WINDOWS_BINARY_PACKAGE_MSIZIP_FILE("msi", R.drawable.ic_file_type_compressed_archive),
  WINDOWS_DLL("dll", R.drawable.ic_file_type_dll),
  WINDOWS_SYS("sys", R.drawable.ic_file_type_binary),
  WINDOWS_COM("com", R.drawable.ic_file_type_binary),
  LINUX_BINARY_DEB_ZIP_FILE("deb", R.drawable.ic_system_linux),
  LINUX_BINARY_RPM_ZIP_FILE("rpm", R.drawable.ic_system_linux),
  EXECUTABLE_SCRIPT_RUN_ZIP_FILE("run", R.drawable.ic_file_type_text_terminal_script),
  EXECUTABLE_SCRIPT_SHGZIP_FILE(
      "sh.gz",
      R.drawable.ic_file_type_text_terminal_script,
  ), // shell 脚本压缩包
  KERNEL_KOGZIP_FILE("ko.gz", R.drawable.ic_file_type_compressed_archive),

  // apple iOS/MacOS
  APPLE_MACOS_DMG_FILE("dmg", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_STUFFIT_ZIP_FILE("sit", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_STUFFIT_HQX_ZIP_FILE("hqx", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_STUFFITX_ZIP_FILE("sitx", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_PKGZIP_FILE("pkg", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_MPKGZIP_FILE("mpkg", R.drawable.ic_file_type_compressed_ios),
  APPLE_MACOS_XIPZIP_FILE("xip", R.drawable.ic_file_type_compressed_ios),
  APPLE_IOS_PACKAGES_IPA__FILE("ipa", R.drawable.ic_file_type_compressed_ios),
  APPLE_BINARY_DYLIB("dylib", R.drawable.ic_file_type_binary),

  // Android packages
  APK("apk", R.drawable.ic_file_type_compressed_android),
  ANDROID_XAPK_ZIP_FILE("xapk", R.drawable.ic_file_type_compressed_android),
  ANDROID_APKS_ZIP_FILE("apks", R.drawable.ic_file_type_compressed_android),
  ANDROID_APKM_ZIP_FILE("apkm", R.drawable.ic_file_type_compressed_android),
  ANDROID_APEX_ZIP_FILE("apex", R.drawable.ic_file_type_compressed_android),
  ANDROID_OBB_ZIP_DATA_FILE("obb", R.drawable.ic_file_type_compressed_archive),
  ANDROID_AAB("aab", R.drawable.ic_file_type_compressed_android),

  // 嵌入式Linux压缩包
  SQUASHFSZIP_FILE("squashfs", R.drawable.ic_file_type_compressed_archive),
  CRAMFS_FILE("cramfs", R.drawable.ic_file_type_compressed_archive),

  // java生态压缩包
  DEV_JAVA_JAR("jar", R.drawable.ic_file_type_text_zip_archive),
  DEV_JAVA_WAR_FILE("war", R.drawable.ic_file_type_text_zip_archive),
  DEV_JAVA_EAR_FILE("ear", R.drawable.ic_file_type_text_zip_archive),
  DEV_JAVA_JMOD_FILE("jmod", R.drawable.ic_file_type_text_zip_archive),
  GZIP_BIN_FILE("gz.bin", R.drawable.ic_file_type_compressed_archive),
  XZIP_BIN_FILE("xz.bin", R.drawable.ic_file_type_compressed_archive),
  WARC_ZIP_FILE("warc", R.drawable.ic_file_type_compressed_archive),
  ARKZIP_FILE("ark", R.drawable.ic_file_type_compressed_archive),
  ARCGZIP_FILE("arc.gz", R.drawable.ic_file_type_compressed_archive),
  LZMA_BINARY_ZIP_FILE("lzma.bin", R.drawable.ic_file_type_compressed_archive),
  TOML("toml", R.drawable.ic_file_type_text_toml), // Tom's Obvious, Minimal Language
  POM("pom", R.drawable.ic_file_type_code),
  PRO("pro", R.drawable.ic_file_type_code),
  TPL("tpl", R.drawable.ic_file_type_code),
  PREFS("prefs", R.drawable.ic_file_type_code),
  KEYS("keys", R.drawable.ic_file_type_code),

  // YAML系列：yaml", "yml", "", "", ""),
  YML_YAML("yml", R.drawable.ic_file_type_code),
  YAML("yaml", R.drawable.ic_file_type_code),
  EYAML_YAML("eyaml", R.drawable.ic_file_type_code),
  EYML_YAML("eyml", R.drawable.ic_file_type_code),
  CFF_YAML("cff", R.drawable.ic_file_type_code),
  INI("ini", R.drawable.ic_file_type_code),
  CONF("conf", R.drawable.ic_file_type_code),
  CFG("cfg", R.drawable.ic_file_type_code),
  REG("reg", R.drawable.ic_file_type_code),
  tem_ft("ft", R.drawable.ic_file_type_code), // 模板
  QTSCRIPT_QS("qs", R.drawable.ic_file_type_code),
  QAT_QS_IN("qs.in", R.drawable.ic_file_type_code),
  QAT_QA_IN("qa.in", R.drawable.ic_file_type_code), // Quality Assurance Template
  ENTITLEMENTS("entitlements", R.drawable.ic_file_type_xml),
  MOBILEPROVISION("mobileprovision", R.drawable.ic_file_type_binary),
  KEYSTORE("keystore", R.drawable.ic_file_type_keystore),
  KEYSTORE_JKS("jks", R.drawable.ic_file_type_keystore),
  KEYSTORE_BKS("bks", R.drawable.ic_file_type_keystore),
  KEYSTORE_CER("cer", R.drawable.ic_file_type_keystore),
  KEYSTORE_CRT("crt", R.drawable.ic_file_type_keystore),
  KEYSTORE_PEM("pem", R.drawable.ic_file_type_keystore),
  KEYSTORE_PK8("pk8", R.drawable.ic_file_type_keystore),
  KEYSTORE_X509PEM("x509.PEM", R.drawable.ic_file_type_keystore),
  KEYSTORE_SF("SF", R.drawable.ic_file_type_keystore),
  KEYSTORE_CAT("cat", R.drawable.ic_file_type_keystore),
  KEYSTORE_PFX("pfx", R.drawable.ic_file_type_keystore),
  KEYSTORE_P12("p12", R.drawable.ic_file_type_keystore),
  KEYSTORE_ASC("asc", R.drawable.ic_file_type_keystore),
  KEYSTORE_GPG("gpg", R.drawable.ic_file_type_keystore),
  KEYSTORE_SIG("sig", R.drawable.ic_file_type_keystore),
  ANDROIDIDEROOT("androidide_root", R.drawable.ic_file_type_code),
  EDITORCONFIG("editorconfig", R.drawable.ic_file_type_editorconfig),
  GITATTRIBUTES("gitattributes", R.drawable.ic_file_type_git),
  GITGNORE("gitignore", R.drawable.ic_file_type_git),
  GITMODULES("gitmodules", R.drawable.ic_file_type_git),
  MAILMAP("mailmap", R.drawable.ic_email),

  // Native Build: Objective-C/Objective-C++，C/C++，cmake，c#，Rust，Golang，Swift，Make / GNU Make，Ada，D
  // language，Pascal / Delphi，MSBuild，Ninja，Bazel
  CLANGJJCPP("cpp", R.drawable.ic_file_type_clang_cpp), // clang++ 源码文件
  CLANGJJCC("cc", R.drawable.ic_file_type_clang_cpp), // clang++ 源码文件
  CLANGJJCXX("cxx", R.drawable.ic_file_type_clang_cpp), // clang++ 源码文件
  CLANGJJC("C", R.drawable.ic_file_type_clang_cpp), // clang++ 源码文件
  CLANGJJHXX("hxx", R.drawable.ic_file_type_clang_class), // clang++ 头文件
  CLANGJJHPP("hpp", R.drawable.ic_file_type_clang_class), // clang++ 头文件
  CLANGJJHH("hh", R.drawable.ic_file_type_clang_class), // clang++ 头文件
  CLANGJJH("h", R.drawable.ic_file_type_clang_h), // clang/++头文件
  CLANGJJ_H_IN("h.in", R.drawable.ic_file_type_clang_h), // clang/++头文件
  CLANGJJ_HXX_IN("hxx.in", R.drawable.ic_file_type_clang_h), // clang/++头文件
  CLANGJJ_CXX_IN("cxx.in", R.drawable.ic_file_type_clang_h), // clang/++头文件
  CLANGC("c", R.drawable.ic_file_type_clang_c), // clang 源码文件
  CLANGT("t", R.drawable.ic_file_type_clang_t), // clang 源码文件  TEMPLATE
  RE_STRUCTURED_TEXT("rst", R.drawable.ic_file_type_code),
  // Assembly
  ASM("asm", R.drawable.ic_file_type_clang_asm),
  ASM_S("s", R.drawable.ic_file_type_clang_asm),
  ASM_A51("a51", R.drawable.ic_file_type_clang_asm),
  INC("inc", R.drawable.ic_file_type_clang_h),
  NASM("nasm", R.drawable.ic_file_type_clang_asm),
  OBJCLANG("m", R.drawable.ic_file_type_clang_m), // Objective-C 源码文件
  OBJCLANGJJ("mm", R.drawable.ic_file_type_clang_m_small), // Objective-C++ 源码文件
  CLANGCS("cs", R.drawable.ic_file_type_net_clang), // c#lang 源码文件
  // CLANGCSPROJ("csproj", R.drawable.ic_file_type_net_clang), //c#lang 项目文件
  SLN("sln", R.drawable.ic_file_type_net_clang),
  VB("vb", R.drawable.ic_file_type_net_clang),
  FS("fs", R.drawable.ic_file_type_net_clang),
  FSX("fsx", R.drawable.ic_file_type_net_clang),
  CMAKE_CONFIGURATION("", R.drawable.ic_file_type_cmake), // cmake 配置文件
  CMAKE("cmake", R.drawable.ic_file_type_cmake), // cmake源码文件
  CMAKE_IN("cmake.in", R.drawable.ic_file_type_cmake), // cmake源码文件
  RUSTLANG("rs", R.drawable.ic_file_type_language_rust), // rust
  RLIB("rlib", R.drawable.ic_file_type_language_rust),
  GOLANG("go", R.drawable.ic_file_type_language_golang), // golang
  GOMOD("mod", R.drawable.ic_file_type_language_golang),
  SWIFT("swift", R.drawable.ic_file_type_language_swift), // swift
  ADB("adb", R.drawable.ic_file_type_code), // ada lang
  ADS("ads", R.drawable.ic_file_type_code), // ada lang
  DLANG("d", R.drawable.ic_file_type_code), // d lang
  PAS("pas", R.drawable.ic_file_type_code), // Pascal / Delphi
  DPR("dpr", R.drawable.ic_file_type_code), // Pascal / Delphi
  PP("pp", R.drawable.ic_file_type_code),
  FJIULING("f90", R.drawable.ic_file_type_code), // Fortran
  FJIUWU("f95", R.drawable.ic_file_type_code),
  FLINGSAN("f03", R.drawable.ic_file_type_code),
  FPRTRANF("f", R.drawable.ic_file_type_code), // Fortran
  FOR("for", R.drawable.ic_file_type_code), // Fortran
  GUNMAKEFILE("makefile", R.drawable.ic_file_type_language_build_system_bazel), // gun make
  GUNMK("mk", R.drawable.ic_file_type_language_build_system_bazel), // gun make
  DP("dp", R.drawable.ic_file_type_language_build_system_bazel), // 配置文件
  AC("ac", R.drawable.ic_file_type_language_build_system_bazel), // 配置文件
  AM("am", R.drawable.ic_file_type_language_build_system_bazel), // 配置文件
  NINJA("ninja", R.drawable.ic_file_type_language_build_system_ninja), // ninja
  BAZEL("bazel", R.drawable.ic_file_type_language_build_system_bazel), // bazel
  BUILD("build", R.drawable.ic_file_type_language_build_system_bazel), // bazel
  BZL("bzl", R.drawable.ic_file_type_language_build_system_bazel),
  PROTO("proto", R.drawable.ic_file_type_language_protobuf), // bazel

  // Scripting & Other Languages (Ruby, Perl, Lua, Python, R, Shell, etc.)
  BAT("bat", R.drawable.ic_file_type_text_terminal_script),
  CMD("cmd", R.drawable.ic_file_type_text_terminal_script),
  SHELL("sh", R.drawable.ic_file_type_text_terminal_script),
  TERMINAL_SCRIPT_RC("rc", R.drawable.ic_file_type_text_terminal_script),
  BASH("bash", R.drawable.ic_file_type_text_terminal_script),
  ZSH("zsh", R.drawable.ic_file_type_text_terminal_script),
  FISH("fish", R.drawable.ic_file_type_text_terminal_script),
  PS1("ps1", R.drawable.ic_file_type_text_terminal_script), // PowerShell
  VBS("vbs", R.drawable.ic_file_type_text_terminal_script),
  PYTHON("py", R.drawable.ic_file_type_python),
  PYTHON_PYI("pyi", R.drawable.ic_file_type_python),
  PYTHON_W("pyw", R.drawable.ic_file_type_python),
  PYTHON_C("pyc", R.drawable.ic_file_type_binary), // Compiled
  PYTHON_D("pyd", R.drawable.ic_file_type_binary), // DLL
  PYTHON_O("pyo", R.drawable.ic_file_type_binary),
  RUBY("rb", R.drawable.ic_file_type_code),
  ERB("erb", R.drawable.ic_file_type_code),
  GEMSPEC("gemspec", R.drawable.ic_file_type_code),
  PERL("pl", R.drawable.ic_file_type_code),
  PERL_MODULE("pm", R.drawable.ic_file_type_code),
  LUA("lua", R.drawable.ic_file_type_code),
  R_LANG("r", R.drawable.ic_file_type_code),
  R_DATA("RData", R.drawable.ic_file_type_binary),
  CLOJURE("clj", R.drawable.ic_file_type_code),
  CLJS("cljs", R.drawable.ic_file_type_code),
  EDN("edn", R.drawable.ic_file_type_code),
  SCALA("scala", R.drawable.ic_file_type_code),
  SC("sc", R.drawable.ic_file_type_code),
  DART("dart", R.drawable.ic_file_type_code),
  ELM("elm", R.drawable.ic_file_type_code),
  ERLANG("erl", R.drawable.ic_file_type_code),
  ELIXIR("ex", R.drawable.ic_file_type_code),
  ELIXIR_SCRIPT("exs", R.drawable.ic_file_type_code),
  HASKELL("hs", R.drawable.ic_file_type_code),
  LHS("lhs", R.drawable.ic_file_type_code),
  OCAML("ml", R.drawable.ic_file_type_code),
  OCAML_I("mli", R.drawable.ic_file_type_code),
  COBOL("cbl", R.drawable.ic_file_type_code),
  COBOL_COPY("cpy", R.drawable.ic_file_type_code),
  VERILOG("v", R.drawable.ic_file_type_code),
  SYSTEM_VERILOG("sv", R.drawable.ic_file_type_code),
  VHDL("vhdl", R.drawable.ic_file_type_code),
  DOCKERFILE("Dockerfile", R.drawable.ic_file_type_editorconfig),
  VAGRANTFILE("Vagrantfile", R.drawable.ic_file_type_editorconfig),

  // Checksums
  MDFIVE("md5", R.drawable.ic_file_type_text_setting_info),
  SHAONE("sha1", R.drawable.ic_file_type_text_setting_info),
  SHATWOFIVESIX("sha256", R.drawable.ic_file_type_text_setting_info),
  SHAFIVEONETWO("sha512", R.drawable.ic_file_type_text_setting_info),
  ASCSIG("asc", R.drawable.ic_file_type_license),

  // Specify folder settings icon
  GIT_DIRECTORY("", R.drawable.ic_folder_type_git), // .git
  GITHUB_DIRECTORY("", R.drawable.ic_folder_type_github), // github
  IDEA_DIRECTORY("", R.drawable.ic_folder_type_idea), // idea
  BENCHMARK_DIRECTORY("", R.drawable.ic_folder_type_benchmark), // benchmark
  GRADLE_DIRECTORY("", R.drawable.ic_folder_type_gradle), // gradle
  DOCS_TEXT_DIRECTORY("", R.drawable.ic_folder_type_docs), // docs and text
  IMAGE_DIRECTORY("", R.drawable.ic_folder_type_image), // image
  JAVA_DIRECTORY("", R.drawable.ic_folder_type_java), // java folder
  KOTLIN_DIRECTORY("", R.drawable.ic_folder_type_kotlin), // kotlin folder
  LOG_DIRECTORY("", R.drawable.ic_folder_type_log), // log
  SOURCE_SRC_MAIN_DIRECTORY("", R.drawable.ic_folder_type_code), // src/main
  TESTING_DIRECTORY("", R.drawable.ic_folder_type_testing), // test and testing
  WORKSPACE_DIRECTORY("", R.drawable.ic_folder_type_workspace), // workspace
  LINUX_DIRECTORY("", R.drawable.ic_folder_type_linux), // workspace
  ANDROID_DIRECTORY("", R.drawable.ic_folder_type_android), // workspace
  PRIVACY_DIRECTORY("", R.drawable.ic_internal_data), // workspace
  DIRECTORY("", R.drawable.ic_folder, R.attr.colorPrimary), // Default folder icon

  // No suffix file
  IMAGE("", R.drawable.ic_file_type_image),
  GRADLEW("", R.drawable.ic_file_type_text_terminal_script),
  LICENSE("", R.drawable.ic_file_type_license),

  // Unknown file
  UNKNOWN("", R.drawable.ic_file_type_unknown);

  /** Factory class for getting [FileExtension] instances. */
  class Factory {
    companion object {
      private val EXTENSION_MAP: Map<String, FileExtension>
      private val DIRECTORY_MAP: Map<String, FileExtension>
      private val EXACT_FILE_MAP: Map<String, FileExtension>

      init {
        // Initialize Extension Map
        val values = entries
        val extMap = HashMap<String, FileExtension>(values.size + 32)
        for (value in values) {
          if (value.extension.isNotEmpty()) {
            extMap[value.extension.lowercase(Locale.US)] = value
          }
        }
        EXTENSION_MAP = extMap

        // Initialize Exact File Name Map
        // Handling specific files like gradlew, LICENSE, CMakeLists.txt etc.
        val exactMap = HashMap<String, FileExtension>()
        exactMap["gradlew"] = GRADLEW
        exactMap["gradlew.bat"] = GRADLEW
        exactMap["LICENSE"] = LICENSE
        exactMap["LICENSE.txt"] = LICENSE
        exactMap["NOTICE"] = LICENSE
        // exactMap["CMakeLists.txt"] = CMAKE
        // exactMap["Dockerfile"] = DOCKERFILE
        // exactMap["Vagrantfile"] = VAGRANTFILE
        // exactMap["Makefile"] = MAKEFILE
        // exactMap["Rakefile"] = RUBY
        // exactMap["Gemfile"] = RUBY
        // exactMap["Podfile"] = RUBY
        // exactMap["mix.exs"] = ELIXIR
        // exactMap["Cargo.toml"] = RUST
        // exactMap["go.mod"] = GO
        // exactMap["go.sum"] = GO
        // exactMap["package.json"] = JS
        // exactMap["tsconfig.json"] = TS

        // Dotfiles
        exactMap[".editorconfig"] = EDITORCONFIG
        exactMap[".gitignore"] = GITGNORE
        exactMap[".gitattributes"] = GITATTRIBUTES
        exactMap[".gitmodules"] = GITMODULES
        exactMap[".gitkeep"] = GITMODULES

        // exactMap[".env"] = ENV
        // exactMap[".bashrc"] = SH
        // exactMap[".zshrc"] = SH
        // exactMap[".profile"] = SH
        // exactMap[".travis.yml"] = YAML
        // exactMap[".gitlab-ci.yml"] = YAML
        EXACT_FILE_MAP = exactMap

        // Initialize Directory Map
        val dirMap = HashMap<String, FileExtension>()

        // Helper to register multiple keys for one value
        fun registerDir(type: FileExtension, vararg keys: String) {
          for (key in keys) dirMap[key] = type
        }

        // register Specific folder name
        registerDir(WORKSPACE_DIRECTORY, "git", ".git", "workflows", "workspace")
        registerDir(GITHUB_DIRECTORY, "github", ".github")
        registerDir(IDEA_DIRECTORY, "idea", ".idea")
        registerDir(GRADLE_DIRECTORY, "gradle", ".gradle")
        registerDir(DOCS_TEXT_DIRECTORY, "text", "txt", "pdf", "doc", "docs")
        registerDir(IMAGE_DIRECTORY, "images", "image", "png", "jpg", "jpeg", "gif", "svg")
        registerDir(JAVA_DIRECTORY, "java", "javac", "javas", "jre", "jdk", "jar")
        registerDir(KOTLIN_DIRECTORY, ".kotlin", "kotlin", "kotlins")
        registerDir(LOG_DIRECTORY, "log", "logs", "logger", "logging")
        registerDir(BENCHMARK_DIRECTORY, "benchmark", "benchmarks")
        registerDir(SOURCE_SRC_MAIN_DIRECTORY, "main", "src", "build")
        registerDir(
            TESTING_DIRECTORY,
            "test",
            "tests",
            "testing",
            "javaTest",
            "kotlinTest",
            "androidTest",
            "commonTest",
            "gradleToolingTest",
            "lspTest",
            "unitTest",
            "测试",
            "experiment",
        )
        registerDir(
            LINUX_DIRECTORY,
            "linux",
            "Linux",
            "Ubuntu",
            "ubuntu",
            "debian",
            "Debian",
            "arch",
            "archlinux",
        )
        registerDir(ANDROID_DIRECTORY, "android", "Android")
        registerDir(
            PRIVACY_DIRECTORY,
            "privacy",
            "private",
            "PrivacySandbox",
            "隐私",
            "私密",
            "protected",
        )

        DIRECTORY_MAP = dirMap
      }

      /**
       * Get [FileExtension] for a directory name without requiring the directory to exist on disk.
       */
      @JvmStatic
      fun forDirectoryName(name: String): FileExtension = DIRECTORY_MAP[name] ?: DIRECTORY

      /**
       * Get [FileExtension] for the given file.
       *
       * @param file The file to check.
       * @return The corresponding FileExtension enum.
       */
      @JvmStatic
      fun forFile(file: File?): FileExtension {
        if (file == null) return UNKNOWN

        val name = file.name

        // Directory Check
        if (file.isDirectory) {
          return forDirectoryName(name)
        }

        val exactEnum = EXACT_FILE_MAP[name]
        if (exactEnum != null) return exactEnum

        val ext = file.extension
        if (ext.isNotEmpty()) {
          val extEnum = EXTENSION_MAP[ext.lowercase(Locale.US)]
          if (extEnum != null) return extEnum
        }

        if (ImageUtils.isImage(file)) {
          return IMAGE
        }

        return UNKNOWN
      }

      /**
       * Get [FileExtension] for the given extension.
       *
       * @param extension The extension string (without dot).
       * @return The FileExtension enum, or UNKNOWN if not found.
       */
      @JvmStatic
      fun forExtension(extension: String?): FileExtension {
        if (extension.isNullOrEmpty()) {
          return UNKNOWN
        }
        return EXTENSION_MAP[extension.lowercase(Locale.US)] ?: UNKNOWN
      }
    }
  }
}
