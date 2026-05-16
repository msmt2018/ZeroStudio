package com.itsaky.androidide.plugins.tasks

import groovy.json.JsonSlurper
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * ZeroAutoTranslateTask
 *
 * 功能：自动读取指定的 Android strings.xml 资源，使用多种翻译引擎多线程并发翻译到多国语言。 支持多文件、多引擎调度、精准保持原始XML注释、空行、行号与格式不被破坏。
 *
 * 使用示例: tasks.register<ZeroAutoTranslateTask>("translateStrings") { // 支持多个文件，使用逗号隔开 sourceXmlPaths
 * = "core/resources/src/main/res/values/strings.xml, feature/src/main/res/values/strings.xml"
 *
 *     // 支持多个引擎，自动调度与负载均衡 (GOOGLE_WEB, BING_WEB, DEEPSEEK, BAIDU_WEB, BAIDU_API)
 *     translationEngines = "DEEPSEEK,BING_WEB,BAIDU_WEB"
 *     concurrency = 5 // 最大并发翻译线程数
 *     deepSeekApiKey = "sk-xxxxxxxx" // 使用DeepSeek时必填
 *     baiduAppId = "2016xxxx"        // 使用BAIDU_API时必填
 *     baiduKey = "xxx_xx"            // 使用BAIDU_API时必填
 *     translationOutputDirName = "StringTranslation"
 *     originalFileBackupDirName = "StringTranslation/backup"
 *
 * }
 */
open class ZeroAutoTranslateTask : DefaultTask() {

  @get:Input var sourceXmlPaths: String = ""

  @get:Input var targetModules: List<String> = emptyList()

  @get:Input var translationEngines: String = "GOOGLE_WEB"

  @get:Input var concurrency: Int = 3

  // API Keys (仅需在使用相关引擎时配置)
  @get:Input var deepSeekApiKey: String = ""
  @get:Input var baiduAppId: String = ""
  @get:Input var baiduKey: String = ""

  @get:Input var translationOutputDirName = "StringTranslation"

  @get:Input var originalFileBackupDirName = "StringTranslation/backup"

  // 语言代码映射：Map<通用Code, Android文件夹后缀>
  private val targetLanguages =
      mapOf(
          "en" to "values", // English (Default)
          "ar" to "values-ar-rSA", // Arabic
          "bn" to "values-bn-rIN", // Bengali
          "de" to "values-de-rDE", // German
          "es" to "values-es-rES", // Spanish
          "fa" to "values-fa", // Persian
          "fil" to "values-fil", // Filipino
          "fr" to "values-fr-rFR", // French
          "hi" to "values-hi-rIN", // Hindi
          "id" to "values-in-rID", // Indonesian
          "it" to "values-it", // Italian
          "ja" to "values-ja", // Japanese
          "ko" to "values-ko", // Korean
          "ml" to "values-ml", // Malayalam
          "pl" to "values-pl", // Polish
          "pt" to "values-pt-rBR", // Brazilian Portuguese
          "ro" to "values-ro-rRO", // Romanian
          "ru" to "values-ru-rRU", // Russian
          "ta" to "values-ta", // Tamil
          "th" to "values-th", // Thai
          "tk" to "values-tm-rTM", // Turkmen
          "tr" to "values-tr-rTR", // Turkish
          "uk" to "values-uk", // Ukrainian
          "vi" to "values-vi", // Vietnamese
          "zh-CN" to "values-zh-rCN", // Simplified Chinese
          "zh-TW" to "values-zh-rTW", // Traditional Chinese
      )

  init {
    group = "translate"
    description = "Automatically translate string resources using various concurrent engines."
  }

  @TaskAction
  fun translate() {
    val projectRoot = project.rootDir
    val files = sourceXmlPaths.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val enginesNames =
        translationEngines.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }

    if (files.isEmpty() || enginesNames.isEmpty()) {
      println(">>> 配置错误：源文件或翻译引擎为空")
      return
    }

    val translators = enginesNames.map { TranslatorFactory.getTranslator(it, this) }

    println(">>> 开始执行 Zero 自动翻译任务")
    println(">>> 启用引擎: ${translators.joinToString { it.name }} | 并发数: $concurrency")

    // 引擎初始化
    translators.forEach {
      try {
        it.init()
      } catch (e: Exception) {
        logger.error("引擎 ${it.name} 初始化失败: ${e.message}")
      }
    }

    val translationRootDir = File(projectRoot, translationOutputDirName)
    val backupRootDir = File(projectRoot, originalFileBackupDirName)
    translationRootDir.mkdirs()
    backupRootDir.mkdirs()

    // 统一线程池执行并发任务
    val executor = Executors.newFixedThreadPool(concurrency)
    // 轮询分配引擎
    val engineIndex = AtomicInteger(0)

    try {
      for (filePath in files) {
        val sourceFile =
            File(filePath).let { if (it.isAbsolute) it else File(projectRoot, filePath) }
        if (!sourceFile.exists()) {
          logger.error("ZeroTranslate: 源文件未找到: ${sourceFile.absolutePath}")
          continue
        }

        println("\n>>> 正在处理源文件: ${sourceFile.name}")
        val templateText = sourceFile.readText(Charsets.UTF_8)
        val sourceStrings = parseStringsFromXmlText(templateText)

        if (sourceStrings.isEmpty()) {
          println("  未找到有效的 <string> 标签，跳过。")
          continue
        }

        targetLanguages.forEach { (langCode, androidFolderName) ->
          processLanguage(
              langCode,
              androidFolderName,
              sourceStrings,
              templateText,
              translationRootDir,
              backupRootDir,
              sourceFile,
              translators,
              executor,
              engineIndex,
          )
        }
      }
    } finally {
      executor.shutdown()
      println("\n>>> Zero 自动翻译任务全部完成。")
    }
  }

  private fun processLanguage(
      langCode: String,
      folderName: String,
      sourceData: List<StringItem>,
      templateText: String,
      outputRootDir: File,
      backupRootDir: File,
      sourceFileForRef: File,
      translators: List<ITranslator>,
      executor: java.util.concurrent.ExecutorService,
      engineIndex: AtomicInteger,
  ) {
    println("\n--- 翻译语言: $folderName (Code: $langCode) ---")
    val targetFolder = File(outputRootDir, folderName)
    targetFolder.mkdirs()
    val targetFile = File(targetFolder, sourceFileForRef.name)

    val backupFolder = File(backupRootDir, folderName)
    backupFolder.mkdirs()

    // 查找工程现有文件用于增量更新和备份
    val projectResourceDir = File(sourceFileForRef.parentFile.parentFile, folderName)
    val existingResourceFile = File(projectResourceDir, sourceFileForRef.name)

    val existingTranslations = ConcurrentHashMap<String, String>()
    if (existingResourceFile.exists()) {
      try {
        Files.copy(
            existingResourceFile.toPath(),
            File(backupFolder, sourceFileForRef.name).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        val existingText = existingResourceFile.readText(Charsets.UTF_8)
        parseStringsFromXmlText(existingText).forEach { existingTranslations[it.name] = it.value }
      } catch (e: Exception) {
        // Ignore backup issues
      }
    }
    // 如果我们输出目录已经有文件，也合并进来避免重复跑
    if (targetFile.exists()) {
      parseStringsFromXmlText(targetFile.readText(Charsets.UTF_8)).forEach {
        existingTranslations[it.name] = it.value
      }
    }

    // 筛选待翻译数据
    val toTranslate = sourceData.filter { !existingTranslations.containsKey(it.name) }
    val translatedMap = ConcurrentHashMap<String, String>()
    translatedMap.putAll(existingTranslations)

    if (toTranslate.isEmpty()) {
      println("  所有条目均已翻译，直接生成文件...")
      saveReconstructedXml(templateText, translatedMap, targetFile)
      return
    }

    val latch = CountDownLatch(toTranslate.size)

    toTranslate.forEach { srcItem ->
      executor.submit {
        var success = false
        var attempts = 0
        val maxAttempts = translators.size + 1 // 给足容错，尝试不同的引擎

        while (!success && attempts < maxAttempts) {
          val translator = translators[engineIndex.getAndIncrement() % translators.size]
          val targetLangCode = translator.convertLangCode(langCode)

          try {
            val result = translator.translate(srcItem.value, "auto", targetLangCode)
            if (result.isNotBlank()) {
              translatedMap[srcItem.name] = result
              println("  [${translator.name}] ${srcItem.name} -> $result")
              success = true
            }
          } catch (e: Exception) {
            attempts++
            println("[${translator.name}] 失败重试: ${e.message}")
            Thread.sleep(Random().nextInt(300) + 200L)
            // 触发重新初始化
            if (attempts > 1) {
              try {
                translator.init()
              } catch (ignore: Exception) {}
            }
          }
        }

        if (!success) {
          translatedMap[srcItem.name] = srcItem.value // 彻底失败降级为原文
          println("  [失败] 保持原文: ${srcItem.name}")
        }

        // 极短延时以防止部分接口高并发封禁IP
        Thread.sleep(Random().nextInt(150) + 50L)
        latch.countDown()
      }
    }

    // 等待当前语言的所有条目翻译完毕
    latch.await()

    // 重建 XML 文件，保留一切格式、注释和原始位置
    saveReconstructedXml(templateText, translatedMap, targetFile)
    println("  保存至: ${targetFile.absolutePath}")
  }

  /** 将翻译内容无损塞回原模板 XML 中 */
  private fun saveReconstructedXml(
      templateText: String,
      translations: Map<String, String>,
      targetFile: File,
  ) {
    val pattern = Pattern.compile("(<string\\s+([^>]+)>)(.*?)(</string>)", Pattern.DOTALL)
    val matcher = pattern.matcher(templateText)
    val sb = StringBuffer()

    while (matcher.find()) {
      val startTag = matcher.group(1)
      val attributes = matcher.group(2)
      val endTag = matcher.group(4)

      val nameMatch = Pattern.compile("name=\"([^\"]+)\"").matcher(attributes)
      var name = ""
      if (nameMatch.find()) name = nameMatch.group(1)

      val translatableMatch = Pattern.compile("translatable=\"([^\"]+)\"").matcher(attributes)
      var isTranslatable = true
      if (translatableMatch.find() && translatableMatch.group(1) == "false") {
        isTranslatable = false
      }

      if (isTranslatable && translations.containsKey(name)) {
        val newValue = fixFormat(translations[name]!!)
        matcher.appendReplacement(sb, Matcher.quoteReplacement(startTag + newValue + endTag))
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)))
      }
    }
    matcher.appendTail(sb)
    targetFile.writeText(sb.toString(), Charsets.UTF_8)
  }

  /** 正则无损解析 XML，抽取待翻译字符串内容 */
  private fun parseStringsFromXmlText(xmlText: String): List<StringItem> {
    val items = mutableListOf<StringItem>()
    val pattern = Pattern.compile("<string\\s+([^>]+)>(.*?)</string>", Pattern.DOTALL)
    val matcher = pattern.matcher(xmlText)

    while (matcher.find()) {
      val attributes = matcher.group(1)
      val value = matcher.group(2)

      val nameMatch = Pattern.compile("name=\"([^\"]+)\"").matcher(attributes)
      if (nameMatch.find()) {
        val name = nameMatch.group(1)
        val translatableMatch = Pattern.compile("translatable=\"([^\"]+)\"").matcher(attributes)
        var isTranslatable = true
        if (translatableMatch.find() && translatableMatch.group(1) == "false") {
          isTranslatable = false
        }
        if (isTranslatable && name.isNotEmpty()) {
          items.add(StringItem(name, value))
        }
      }
    }
    return items
  }

  data class StringItem(val name: String, val value: String)

  // ==================================================================================
  // 翻译引擎工厂及实现 (Converted from Java Logic)
  // ==================================================================================

  interface ITranslator {
    val name: String

    fun init() {}

    fun translate(text: String, from: String, to: String): String

    fun convertLangCode(lang: String): String = lang
  }

  object TranslatorFactory {
    fun getTranslator(type: String, task: ZeroAutoTranslateTask): ITranslator {
      return when (type) {
        "GOOGLE_WEB" -> GoogleWebTranslator()
        "BING_WEB" -> BingWebTranslator()
        "DEEPSEEK" -> DeepSeekTranslator(task.deepSeekApiKey)
        "BAIDU_WEB" -> BaiduWebTranslator()
        "BAIDU_API" -> BaiduApiTranslator(task.baiduAppId, task.baiduKey)
        else -> GoogleWebTranslator() // 默认
      }
    }
  }

  // --- 1. Google Web (Simple API) ---
  class GoogleWebTranslator : ITranslator {
    override val name = "Google Web"

    override fun translate(text: String, from: String, to: String): String {
      val url =
          "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=$from&tl=$to&q=${URLEncoder.encode(text, "UTF-8")}"
      val response = HttpUtils.get(url).execute()
      if (!response.startsWith("[[[")) throw RuntimeException("GoogleWeb Parse failed")

      val array = (JsonSlurper().parseText(response) as List<*>)[0] as List<*>
      val sb = StringBuilder()
      for (item in array) {
        sb.append((item as List<*>)[0].toString())
      }
      return sb.toString()
    }
  }

  // --- 2. Bing Web ---
  class BingWebTranslator : ITranslator {
    override val name = "Bing Web"
    private var ig: String? = null
    private var iid: String? = null
    private var key: String? = null
    private var token: String? = null
    private val domain = "cn.bing.com"

    @Synchronized
    override fun init() {
      if (ig != null) return
      try {
        val html = HttpUtils.get("https://$domain/translator").execute()
        ig = Regex("IG:\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        iid = Regex("data-iid=\"([^\"]+)\"").find(html)?.groupValues?.get(1)

        val paramsMatch = Regex("params_AbusePreventionHelper\\s*=\\s*([^;]+);").find(html)
        if (paramsMatch != null) {
          val arr = JsonSlurper().parseText(paramsMatch.groupValues[1]) as List<*>
          token = arr[1].toString()
          key = arr[0].toString()
        }
        if (ig == null || iid == null || key == null || token == null) {
          throw RuntimeException("Bing Params Missing")
        }
      } catch (e: Exception) {
        throw RuntimeException("Bing Init Error", e)
      }
    }

    override fun translate(text: String, from: String, to: String): String {
      for (i in 0..1) {
        if (ig == null) init()
        try {
          val url = "https://$domain/ttranslatev3?IG=$ig&IID=${iid}.1"
          val response =
              HttpUtils.post(url)
                  .formData("fromLang", from)
                  .formData("text", text)
                  .formData("to", to)
                  .formData("token", token!!)
                  .formData("key", key!!)
                  .execute()

          if (response.contains("\"statusCode\":205")) {
            ig = null
            continue
          }
          val jsonArray = JsonSlurper().parseText(response) as List<*>
          val sb = StringBuilder()
          for (item in jsonArray) {
            val obj = item as Map<*, *>
            val translations = obj["translations"] as? List<*>
            translations?.forEach { sb.append((it as Map<*, *>)["text"]) }
          }
          return sb.toString()
        } catch (e: Exception) {
          ig = null
          if (i == 1) throw e
        }
      }
      throw RuntimeException("Translation failed")
    }

    override fun convertLangCode(lang: String): String =
        when (lang) {
          "auto" -> "auto-detect"
          "zh-CN" -> "zh-Hans"
          "zh-TW" -> "zh-Hant"
          "iw" -> "he"
          "hmn" -> "mww"
          "tl" -> "fil"
          else -> lang
        }
  }

  // --- 3. DeepSeek Translator ---
  class DeepSeekTranslator(private val apiKey: String) : ITranslator {
    override val name = "DeepSeek AI"

    override fun translate(text: String, from: String, to: String): String {
      if (apiKey.isEmpty()) throw RuntimeException("DeepSeek API Key is empty")
      val targetName = convertLangCode(to)
      val systemPrompt =
          "You are a translation engine. Target language: $targetName. Translate the user text into the target language and output only the translated text. Do not add explanations or notes. Keep original line breaks and paragraph order. Keep punctuation style consistent with the source text. If the source text has no ending punctuation, do not add ending punctuation in translation. Do not translate placeholders and special tokens such as %s, %1\$d, {name}, {0}."

      val jsonBody =
          """
                {
                    "model": "deepseek-chat",
                    "temperature": 1.3,
                    "stream": false,
                    "messages":[
                        {"role": "system", "content": ${JsonHelper.escapeString(systemPrompt)}},
                        {"role": "user", "content": ${JsonHelper.escapeString(text)}}
                    ]
                }
            """
              .trimIndent()

      val response =
          HttpUtils.post("https://api.deepseek.com/chat/completions")
              .header("Authorization", "Bearer $apiKey")
              .jsonBody(jsonBody)
              .execute()

      val json = JsonSlurper().parseText(response) as Map<*, *>
      if (json.containsKey("error")) {
        throw RuntimeException("DeepSeek Error: ${(json["error"] as Map<*, *>)["message"]}")
      }
      val choices = json["choices"] as List<*>
      val message = (choices[0] as Map<*, *>)["message"] as Map<*, *>
      return message["content"].toString().trim()
    }

    override fun convertLangCode(lang: String): String =
        when (lang) {
          "zh-CN" -> "Simplified Chinese"
          "zh-TW" -> "Traditional Chinese"
          "en" -> "English"
          "ja" -> "Japanese"
          "ko" -> "Korean"
          "fr" -> "French"
          "de" -> "German"
          "ru" -> "Russian"
          "es" -> "Spanish"
          "ar" -> "Arabic"
          else -> lang
        }
  }

  // --- 4. Baidu Web Translator ---
  class BaiduWebTranslator : ITranslator {
    override val name = "Baidu Web"
    private var token: String? = null
    private var gtk: LongArray? = null
    private var cookie: String = ""

    @Synchronized
    override fun init() {
      if (gtk != null) return
      cookie = "BAIDUID=${UUID.randomUUID().toString().replace("-", "").uppercase()}:FG=1;"
      val html =
          HttpUtils.get("https://fanyi.baidu.com/translate").header("Cookie", cookie).execute()

      token = Regex("token\\s*[:=]\\s*['\"](.*?)['\"]").find(html)?.groupValues?.get(1)
      val gtkMatch = Regex("gtk\\s*[:=]\\s*['\"]([0-9]+)\\.([0-9]+)['\"]").find(html)?.groupValues
      if (gtkMatch != null) {
        gtk = longArrayOf(gtkMatch[1].toLong(), gtkMatch[2].toLong())
      }
      if (token == null || gtk == null) throw RuntimeException("BaiduWeb Init Failed")
    }

    override fun translate(text: String, from: String, to: String): String {
      if (token == null) init()
      val sign = SignUtil.signWeb(text, gtk!![0], gtk!![1])
      val response =
          HttpUtils.post("https://fanyi.baidu.com/basetrans")
              .header("Cookie", cookie)
              .formData("query", text)
              .formData("from", from)
              .formData("to", to)
              .formData("token", token!!)
              .formData("sign", sign)
              .execute()

      val json = JsonSlurper().parseText(response) as Map<*, *>
      if (json["errno"]?.toString() != "0") {
        gtk = null // trigger re-init
        throw RuntimeException("Baidu Error: ${json["errmsg"]}")
      }
      val trans = json["trans"] as List<*>
      val sb = StringBuilder()
      for ((i, item) in trans.withIndex()) {
        if (i != 0) sb.append('\n')
        sb.append((item as Map<*, *>)["dst"])
      }
      return sb.toString()
    }

    override fun convertLangCode(lang: String): String =
        when (lang) {
          "zh-CN" -> "zh"
          "zh-TW" -> "cht"
          "ja" -> "jp"
          "ko" -> "kor"
          "fr" -> "fra"
          "es" -> "spa"
          else -> lang
        }
  }

  // --- 5. Baidu API Translator (VIP) ---
  class BaiduApiTranslator(private val appId: String, private val key: String) : ITranslator {
    override val name = "Baidu API"

    override fun translate(text: String, from: String, to: String): String {
      if (appId.isEmpty() || key.isEmpty()) throw RuntimeException("Baidu API Config Empty")
      val salt = Random().nextInt(100000).toString()
      val sign = SignUtil.md5(appId + text + salt + key)
      val url =
          "https://fanyi-api.baidu.com/api/trans/vip/translate?q=${URLEncoder.encode(text, "UTF-8")}&from=$from&to=$to&appid=$appId&salt=$salt&sign=$sign"

      val response = HttpUtils.get(url).execute()
      val json = JsonSlurper().parseText(response) as Map<*, *>

      if (json.containsKey("error_code")) {
        throw RuntimeException("BaiduApi Error ${json["error_code"]}: ${json["error_msg"]}")
      }
      val transResult = json["trans_result"] as List<*>
      val sb = StringBuilder()
      for ((i, item) in transResult.withIndex()) {
        if (i != 0) sb.append('\n')
        sb.append((item as Map<*, *>)["dst"])
      }
      return sb.toString()
    }

    override fun convertLangCode(lang: String): String =
        when (lang) {
          "zh-CN" -> "zh"
          "zh-TW" -> "cht"
          "ja" -> "jp"
          "ko" -> "kor"
          "fr" -> "fra"
          "es" -> "spa"
          else -> lang
        }
  }

  // ==================================================================================
  // 内部通用工具模块
  // ==================================================================================

  object HttpUtils {
    const val UA = "Mozilla/5.0 (Linux; Android 10.0;)"

    fun get(url: String) = RequestBuilder(url, "GET")

    fun post(url: String) = RequestBuilder(url, "POST")

    class RequestBuilder(private val url: String, private val method: String) {
      private val headers = HashMap<String, String>()
      private val formData = StringBuilder()
      private var rawBody: ByteArray? = null

      init {
        headers["User-Agent"] = UA
      }

      fun header(k: String, v: String) = apply { headers[k] = v }

      fun formData(k: String, v: String) = apply {
        header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        if (formData.isNotEmpty()) formData.append("&")
        formData.append(URLEncoder.encode(k, "UTF-8"))
        formData.append("=")
        formData.append(URLEncoder.encode(v, "UTF-8"))
      }

      fun jsonBody(json: String) = apply {
        header("Content-Type", "application/json; charset=utf-8")
        this.rawBody = json.toByteArray(Charsets.UTF_8)
      }

      fun execute(): String {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        if (method == "POST") {
          conn.doOutput = true
          val data = rawBody ?: formData.toString().toByteArray(Charsets.UTF_8)
          conn.outputStream.use { it.write(data) }
        }

        if (conn.responseCode >= 400) {
          val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
          throw RuntimeException(err)
        }
        return conn.inputStream.bufferedReader().readText()
      }
    }
  }

  object JsonHelper {
    fun escapeString(text: String): String {
      val sb = StringBuilder("\"")
      for (c in text) {
        when (c) {
          '"' -> sb.append("\\\"")
          '\\' -> sb.append("\\\\")
          '\b' -> sb.append("\\b")
          '\u000C' -> sb.append("\\f")
          '\n' -> sb.append("\\n")
          '\r' -> sb.append("\\r")
          '\t' -> sb.append("\\t")
          else -> sb.append(c)
        }
      }
      sb.append("\"")
      return sb.toString()
    }
  }

  object SignUtil {
    fun md5(input: String): String {
      val md = MessageDigest.getInstance("MD5")
      val digest = md.digest(input.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }

    fun signWeb(text: String, key1: Long, key2: Long): String {
      val c = mutableListOf<Int>()
      var F = 0
      while (F < text.length) {
        var p = text[F].code
        if (128 > p) {
          c.add(p)
        } else {
          if (2048 > p) {
            c.add(p shr 6 or 192)
          } else {
            if (
                55296 == (64512 and p) &&
                    F + 1 < text.length &&
                    56320 == (64512 and text[F + 1].code)
            ) {
              p = 65536 + ((1023 and p) shl 10) + (1023 and text[++F].code)
              c.add(p shr 18 or 240)
              c.add(p shr 12 and 63 or 128)
            } else {
              c.add(p shr 12 or 224)
            }
            c.add(p shr 6 and 63 or 128)
          }
          c.add(63 and p or 128)
        }
        F++
      }
      val formula1 = "+-a^+6"
      val formula2 = "+-3^+b+-f"
      var v = key1
      for (i in c) {
        v += i
        v = n(v, formula1)
      }
      v = n(v, formula2)
      v = v xor key2
      if (0 > v) {
        v = (0x7fffffffL and v) + 0x80000000L
      }
      v %= 1000000
      return "$v.${v xor key1}"
    }

    private fun n(r: Long, o: String): Long {
      var rVar = r
      var t = 0
      while (t < o.length - 2) {
        var e = o[t + 2].code.toLong()
        e = if (e >= 'a'.code.toLong()) e - 87 else e - '0'.code.toLong()
        val shift = if (o[t + 1] == '+') rVar ushr e.toInt() else rVar shl e.toInt()
        rVar = if (o[t] == '+') (rVar + shift) and 0xffffffffL else rVar xor shift
        t += 3
      }
      return rVar
    }
  }

  companion object {
    fun fixFormat(text: String): String {
      var result = text.replace("'", "\\'") // 修复单引号，防止Android编译报错

      // 修复占位符中间的空格：如 % s -> %s, % 1 $ d -> %1$d
      result = result.replace(Regex("%\\s+([sd])"), "%$1")
      result = result.replace(Regex("%\\s+(\\d+)\\s*\\$\\s*([sd])"), "%$1$$$2")

      // 修复翻译引擎对 CDATA 加的错误空格
      result = result.replace("] ] >", "]]>")
      result = result.replace(Regex("<!\\s*\\[\\s*CDATA\\s*\\["), "<![CDATA[")

      return result
    }
  }
}
