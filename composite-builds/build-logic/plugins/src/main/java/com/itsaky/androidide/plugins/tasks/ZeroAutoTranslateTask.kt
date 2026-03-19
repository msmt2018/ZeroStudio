package com.itsaky.androidide.plugins.tasks

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * ZeroAutoTranslateTask
 *
 *
 *
  *以下是一些说明与使用：
 
 注意：ZeroAutoTranslateTask是作为Composite builds来使用的，也就是类似全局变量环境一样，具体看官方文章：https://docs.gradle.org/6.8.1/userguide/composite_builds.html
 
 // PS：解决xml中的字符串国际化复杂和反复繁琐问题，一定程度上提高效率

// 友情提示：
// 将以下导入你的kts文件，使用命令：./gradlew translateStrings 来批量翻译指定字符串文件。
// 将什么语言翻译为什么语言这个没有作为tasks.register可选项，所以需要在ZeroAutoTranslateTask里面修改自己需要输出的国际化语言


在kts文件添加以下代码以及tasks.register
import com.itsaky.androidide.plugins.tasks.ZeroAutoTranslateTask

// 运行 ./gradlew translateStrings 来执行翻译
tasks.register<ZeroAutoTranslateTask>("translateStrings") {
    // 设置翻译源文件路径
    sourceXmlPath = "core/resources/src/main/res/values/development_test_resources.xml"

     //注意：目前谷歌翻译并非常用官方API，所以可能会存在封ip/无响应等问题导致翻译失败
    // 设置翻译引擎 (可选: GOOGLE_GTX, GOOGLE_WEB, BAIDU_WEB, YOUDAO_WEB, BING_WEB)
    translationEngine = "BING_WEB"

    // 设置输出目录
    // 翻译结果会生成在: 项目根目录/StringTranslation/values-xx/strings.xml
    translationOutputDirName = "StringTranslation"

    // 设置原始文件备份目录
    // 原始文件会备份在: 项目根目录/StringTranslation/backup/values-xx/strings.xml
    originalFileBackupDirName = "StringTranslation/backup"
}
 
 *
 * 功能：自动读取指定的 Android strings.xml 资源，使用多种翻译引擎翻译到多国语言。
 * @author android_zero
 */
open class ZeroAutoTranslateTask : DefaultTask() {

    @get:Input
    var sourceXmlPath: String = ""

    @get:Input
    var targetModules: List<String> = emptyList()

    /**
     * 选择翻译引擎。
     * 可选值: "GOOGLE_GTX" (默认, 无需Key), "GOOGLE_WEB", "YOUDAO_WEB", "BAIDU_WEB", "BING_WEB"
     */
    @get:Input
    var translationEngine: String = "BING_WEB"

    // 翻译输出根目录
    @get:Input
     var translationOutputDirName = "StringTranslation/backup"
    // 原始文件备份目录
    @get:Input
     var originalFileBackupDirName = "StringTranslation"

    // 语言代码映射：Map<Google/通用Code, Android文件夹后缀>
     private val targetLanguages = mapOf(
          // "en" to "values",
          // "ar" to "values-ar-rSA",
          // "bn" to "values-bn-rIN",
          "de" to "values-de-rDE",
          "es" to "values-es-rES",
          "fa" to "values-fa",
          "fil" to "values-fil",
          "fr" to "values-fr-rFR",
          "hi" to "values-hi-rIN",
          "id" to "values-in-rID",
          "it" to "values-it",
          "ja" to "values-ja",
          "ko" to "values-ko",
          "ml" to "values-ml",
          "pl" to "values-pl",
          "pt" to "values-pt-rBR",
          "ro" to "values-ro-rRO",
          // "ru" to "values-ru-rRU",
          "ta" to "values-ta",
          "th" to "values-th",
          // "tr" to "values-tr-rTR",
          "tk" to "values-tm-rTM",
          "vi" to "values-vi",
          "uk" to "values-uk",
          // "zh-CN" to "values-zh-rCN",
          "zh-TW" to "values-zh-rTW"
)



    init {
        group = "translate"
        description = "Automatically translate string resources using various engines."
    }

    @TaskAction
    fun translate() {
        val projectRoot = project.rootDir
        val sourceFile = if (sourceXmlPath.isNotEmpty()) {
            File(sourceXmlPath).let { if (it.isAbsolute) it else File(projectRoot, sourceXmlPath) }
        } else {
            File(projectRoot, "core/resources/src/main/res/values/strings.xml")
        }

        if (!sourceFile.exists()) {
            logger.error("ZeroTranslate: Source file not found: ${sourceFile.absolutePath}")
            return
        }

        val translator = TranslatorFactory.getTranslator(translationEngine)
        println(">>> 开始执行 Zero 自动翻译任务")
        println(">>> 引擎: ${translator.name}")
        println(">>> 源文件: ${sourceFile.absolutePath}")

        // 初始化引擎（如果需要，例如获取Token）
        try {
            translator.init()
        } catch (e: Exception) {
            logger.error("引擎初始化失败: ${e.message}")
            return
        }

        val sourceStrings = parseStringsFromXml(sourceFile)
        if (sourceStrings.isEmpty()) {
            println(">>> 源文件中未找到 <string> 标签，任务结束。")
            return
        }

        val translationRootDir = File(projectRoot, translationOutputDirName)
        val backupRootDir = File(projectRoot, originalFileBackupDirName)

        translationRootDir.mkdirs()
        backupRootDir.mkdirs()

        targetLanguages.forEach { (langCode, androidFolderName) ->
            // 引擎特定的语言代码转换
            val engineLangCode = translator.convertLangCode(langCode)
            
            processLanguage(
                engineLangCode,
                androidFolderName,
                sourceStrings,
                translationRootDir,
                backupRootDir,
                sourceFile,
                translator
            )
        }

        println(">>> Zero 自动翻译任务全部完成。")
    }

    private fun processLanguage(
        langCode: String,
        folderName: String,
        sourceData: List<StringItem>,
        outputRootDir: File,
        backupRootDir: File,
        sourceFileForRef: File,
        translator: ITranslator
    ) {
        println("\n--- 处理语言: $folderName (Code: $langCode) ---")

        val targetFolder = File(outputRootDir, folderName)
        targetFolder.mkdirs()
        val targetFile = File(targetFolder, "strings.xml")

        val backupFolder = File(backupRootDir, folderName)
        backupFolder.mkdirs()

        // 尝试寻找项目现有的翻译文件进行增量翻译或备份
        val projectResourceDir = File(sourceFileForRef.parentFile.parentFile, folderName)
        val existingResourceFile = File(projectResourceDir, "strings.xml")

        if (existingResourceFile.exists()) {
            try {
                Files.copy(existingResourceFile.toPath(), File(backupFolder, "strings.xml").toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                // ignore
            }
        }
        
        // 读取现有的翻译（为了避免重复翻译，实际逻辑可根据需求增强）
        // val existingMap = if(existingResourceFile.exists()) parseStringsMap(existingResourceFile) else emptyMap()

        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()
        val doc = docBuilder.newDocument()
        val rootElement = doc.createElement("resources")
        doc.appendChild(rootElement)

        var currentLineEstimate = 2
        val nameSet = HashSet<String>()

        sourceData.forEach { srcItem ->
            if (nameSet.contains(srcItem.name)) return@forEach

            // 执行翻译
            var translatedValue = ""
            var retryCount = 0
            while (retryCount < 3) {
                try {
                    translatedValue = translator.translate(srcItem.value, "auto", langCode)
                    break
                } catch (e: Exception) {
                    retryCount++
                    println("  翻译失败 ($retryCount/3): ${e.message}，重试中...")
                    Thread.sleep(1000)
                    // 如果是Token过期等问题，尝试重新初始化
                    if (retryCount == 2) {
                        try { translator.init() } catch (ignore: Exception) {}
                    }
                }
            }

            if (translatedValue.isEmpty()) {
                translatedValue = srcItem.value // 失败降级为原文
                println("  [失败] 保持原文: ${srcItem.name}")
            }

            val stringElement = doc.createElement("string")
            stringElement.setAttribute("name", srcItem.name)
            stringElement.textContent = translatedValue
            rootElement.appendChild(stringElement)

            nameSet.add(srcItem.name)
            
            println("${srcItem.name} -> $translatedValue")
            currentLineEstimate++
            
            // 稍微延时避免被封IP
            Thread.sleep(Random().nextInt(200) + 100L) 
        }

        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

        val source = DOMSource(doc)
        val result = StreamResult(targetFile)
        transformer.transform(source, result)

        println("保存至: ${targetFile.absolutePath}")
    }

    private fun parseStringsFromXml(file: File): List<StringItem> {
        val items = mutableListOf<StringItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement.normalize()
            val nodeList = doc.getElementsByTagName("string")
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val name = element.getAttribute("name")
                    // translatable 默认为 true，如果显式设为 false 则跳过
                    val translatable = element.getAttribute("translatable")
                    if (translatable != "false" && name.isNotEmpty()) {
                         items.add(StringItem(name, element.textContent, 0))
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("XML解析失败", e)
        }
        return items
    }

    data class StringItem(val name: String, val value: String, val lineNumber: Int)

    // ==================================================================================
    // 内部工具类与接口 (Ported from Java logic)
    // ==================================================================================

    interface ITranslator {
        val name: String
        fun init() {}
        fun translate(text: String, from: String, to: String): String
        fun convertLangCode(lang: String): String = lang
    }

    object TranslatorFactory {
        fun getTranslator(type: String): ITranslator {
            return when (type.uppercase()) {
                "GOOGLE_WEB" -> GoogleWebTranslator()
                "YOUDAO_WEB" -> YoudaoWebTranslator()
                "BAIDU_WEB" -> BaiduWebTranslator()
                "BING_WEB" -> BingWebTranslator()
                else -> GoogleGtxTranslator() // 默认
            }
        }
    }
    
    // --- 1. Google GTX (Simple API) ---
    class GoogleGtxTranslator : ITranslator {
        override val name = "Google GTX"
        override fun translate(text: String, from: String, to: String): String {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"
            val response = HttpUtils.get(url).header("User-Agent", HttpUtils.UA).execute()
            // 解析: [[["Translated","Source",...]]]
            val json = JsonSlurper().parseText(response) as ArrayList<*>
            val segments = json[0] as ArrayList<*>
            val sb = StringBuilder()
            for (segment in segments) {
                val part = segment as ArrayList<*>
                sb.append(part[0].toString())
            }
            return fixFormat(sb.toString())
        }
    }

    // --- 2. Google Web (BatchExecute) ---
    class GoogleWebTranslator : ITranslator {
        override val name = "Google Web"
        override fun translate(text: String, from: String, to: String): String {
             // 谷歌Web版 RPC请求构造
            val rpcArg = listOf(listOf(text, from, to, true)) // text, source, target, boolean
            val rpcJson = JsonHelper.toJson(rpcArg)
            val freq = listOf(listOf("MkEWBc", rpcJson, null, "generic"))
            val fReqStr = JsonHelper.toJson(freq)
            
            val body = "f.req=${URLEncoder.encode(fReqStr, "UTF-8")}"
            val url = "https://translate.google.com/_/TranslateWebserverUi/data/batchexecute"
            
            val response = HttpUtils.post(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .execute()

            // 解析 Google 的特殊 JSON 格式: )]}' \n [ ... ]
            val cleanJson = response.substring(response.indexOf('['))
            val outerArray = JsonSlurper().parseText(cleanJson) as ArrayList<*>
            val innerDataStr = (outerArray[0] as ArrayList<*>)[2] as String
            val dataArray = JsonSlurper().parseText(innerDataStr) as ArrayList<*>
            
            // 路径: [1][0][0][5]... 这是一个复杂的嵌套，根据 GoogleWebTranslator2 逻辑适配
            val translationData = (((dataArray[1] as ArrayList<*>)[0] as ArrayList<*>)[0] as ArrayList<*>)[5] as ArrayList<*>
            
            val sb = StringBuilder()
            for (item in translationData) {
                val part = item as? ArrayList<*>
                if (part != null && part.isNotEmpty()) {
                    sb.append(part[0].toString())
                }
            }
            return fixFormat(sb.toString())
        }
        
        override fun convertLangCode(lang: String): String {
            return when(lang) {
                "zh-CN" -> "zh-CN" // Google web specific
                "zh-TW" -> "zh-TW"
                else -> lang
            }
        }
    }

    // --- 3. Youdao Web ---
    class YoudaoWebTranslator : ITranslator {
        override val name = "Youdao Web"
        private var cookie: String? = null

        override fun init() {
            // 获取 Cookie
            val conn = HttpUtils.get("http://m.youdao.com/translate").createConnection()
            conn.connect()
            val cookies = conn.headerFields["Set-Cookie"]
            if (cookies != null && cookies.isNotEmpty()) {
                cookie = cookies[0].split(";")[0]
            }
            conn.disconnect()
        }

        override fun translate(text: String, from: String, to: String): String {
            if (cookie == null) init()
            
            val t = System.currentTimeMillis()
            val salt = "$t${Random().nextInt(10)}"
            val signStr = "fanyideskweb$text$salt" + "Ygy_4c=r#e#4EX^NUGUc5" // 这是一个已知的旧Key，可能已失效，有道Web版反爬很严
            val sign = SignUtil.md5(signStr)
            
            // 映射语言代码
            val rFrom = if (from == "auto") "AUTO" else from
            val rTo = if (to == "zh-CN") "AUTO" else to // 有道简中通常是AUTO或zh-CHS

            val response = HttpUtils.post("https://fanyi.youdao.com/translate_o?smartresult=dict&smartresult=rule")
                .header("Cookie", cookie!!)
                .header("Referer", "https://fanyi.youdao.com/")
                .formData("i", text)
                .formData("from", rFrom)
                .formData("to", rTo)
                .formData("client", "fanyideskweb")
                .formData("salt", salt)
                .formData("sign", sign)
                .formData("lts", t.toString())
                .formData("bv", SignUtil.md5(HttpUtils.UA))
                .formData("doctype", "json")
                .formData("version", "2.1")
                .formData("keyfrom", "fanyi.web")
                .formData("action", "FY_BY_REALTlME")
                .execute()

            val json = JsonSlurper().parseText(response) as Map<*, *>
            if (json["errorCode"] as Int != 0) throw RuntimeException("Youdao Error: ${json["errorCode"]}")
            
            val results = json["translateResult"] as ArrayList<*>
            val sb = StringBuilder()
            for (res in results) {
                val parts = res as ArrayList<*>
                for (part in parts) {
                    val p = part as Map<*, *>
                    sb.append(p["tgt"])
                }
            }
            return fixFormat(sb.toString())
        }
        
         override fun convertLangCode(lang: String): String {
            return when(lang) {
                "zh-CN" -> "zh-CHS"
                "zh-TW" -> "zh-CHT" // 有道繁体
                else -> lang
            }
        }
    }

    // --- 4. Baidu Web ---
    class BaiduWebTranslator : ITranslator {
        override val name = "Baidu Web"
        private var token: String? = null
        private var gtk: String? = null
        private var cookie: String? = null

        override fun init() {
            // 1. Get BAIDUID
            cookie = "BAIDUID=${UUID.randomUUID().toString().replace("-", "").uppercase()}:FG=1;"
            
            // 2. Get Token & GTK
            val response = HttpUtils.get("https://fanyi.baidu.com/")
                .header("Cookie", cookie!!)
                .execute()
            
            val tokenMatcher = Pattern.compile("token: '([a-f0-9]+)'").matcher(response)
            if (tokenMatcher.find()) token = tokenMatcher.group(1)
            
            val gtkMatcher = Pattern.compile("window.gtk = '([0-9.]+)'").matcher(response)
            if (gtkMatcher.find()) gtk = gtkMatcher.group(1)
            
            if (token == null || gtk == null) throw RuntimeException("Baidu Init Failed: Token/GTK not found")
        }

        override fun translate(text: String, from: String, to: String): String {
            if (token == null) init()
            
            val sign = SignUtil.baiduSign(text, gtk!!)
            
            val response = HttpUtils.post("https://fanyi.baidu.com/v2transapi?from=$from&to=$to")
                .header("Cookie", cookie!!)
                .formData("from", from)
                .formData("to", to)
                .formData("query", text)
                .formData("transtype", "realtime")
                .formData("simple_means_flag", "3")
                .formData("sign", sign)
                .formData("token", token!!)
                .formData("domain", "common")
                .execute()

            val json = JsonSlurper().parseText(response) as Map<*, *>
            if (json.containsKey("error")) throw RuntimeException("Baidu Error: ${json["error"]}")
            
            val transResult = (json["trans_result"] as Map<*, *>)["data"] as ArrayList<*>
            val sb = StringBuilder()
            for (item in transResult) {
                val map = item as Map<*, *>
                sb.append(map["dst"])
                sb.append("\n")
            }
            return fixFormat(sb.toString().trim())
        }
        
        override fun convertLangCode(lang: String): String {
            return when(lang) {
                "zh-CN" -> "zh"
                "zh-TW" -> "cht"
                "ja" -> "jp"
                else -> lang
            }
        }
    }
    
    // --- 5. Bing Web ---
        class BingWebTranslator : ITranslator {
        override val name = "Bing Web"
        private var ig: String? = null
        private var iid: String? = null
        private var key: String? = null
        private var token: String? = null
        private val domain = "cn.bing.com"

        override fun init() {
            try {
                val html = HttpUtils.get("https://$domain/translator").execute()
                
                // 1. 获取 IG 和 IID
                ig = Regex("IG:\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                // 对应 Java 版的 <div id="rich_tta" data-iid="...">
                iid = Regex("data-iid=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                
                // 2. 解析 AbusePreventionHelper 数组 [key, token, timeout]
                val paramsMatch = Regex("params_AbusePreventionHelper\\s*=\\s*([^;]+);").find(html)
                if (paramsMatch != null) {
                    val jsonStr = paramsMatch.groupValues[1]
                    // 使用 JsonSlurper 解析数组
                    val arr = groovy.json.JsonSlurper().parseText(jsonStr) as List<*>
                    token = arr[1].toString()
                    key = arr[0].toString()
                }
                
                if (ig == null || iid == null || key == null || token == null) {
                    throw RuntimeException("Bing Init Failed: Missing parameters")
                }
            } catch (e: Exception) {
                throw RuntimeException("Bing Init Error: ${e.message}", e)
            }
        }

        override fun translate(text: String, from: String, to: String): String {
            // 尝试 2 次，处理 205 状态码重试逻辑
            for (i in 0 until 2) {
                if (ig == null) init()
                
                try {
                    val fromCode = convertLangCode(from)
                    val toCode = convertLangCode(to)
                    
                    val url = "https://$domain/ttranslatev3?IG=$ig&IID=${iid}.1"
                    val response = HttpUtils.post(url)
                        .formData("fromLang", fromCode)
                        .formData("text", text)
                        .formData("to", toCode)
                        .formData("token", token!!)
                        .formData("key", key!!)
                        .execute()

                    // 检查是否出现 205 错误 (需要刷新 IG)
                    if (response.contains("\"statusCode\":205")) {
                        ig = null // 触发重试时的重新初始化
                        continue
                    }

                    // 解析结果
                    val jsonArray = groovy.json.JsonSlurper().parseText(response) as List<*>
                    val sb = StringBuilder()
                    
                    for (item in jsonArray) {
                        val obj = item as Map<*, *>
                        val translations = obj["translations"] as? List<*>
                        translations?.forEach { 
                            val t = it as Map<*, *>
                            sb.append(t["text"])
                        }
                    }
                    return fixFormat(sb.toString())
                } catch (e: Exception) {
                    if (i == 1) throw e // 第二次尝试失败则抛出
                    ig = null // 否则清空参数尝试重新初始化
                }
            }
            throw RuntimeException("Translation failed after retries")
        }
        
        override fun convertLangCode(lang: String): String {
             return when(lang) {
                "auto"  -> "auto-detect"
                "zh-CN" -> "zh-Hans"
                "zh-TW" -> "zh-Hant"
                "iw"    -> "he"
                "hmn"   -> "mww"
                "tl"    -> "fil"
                else    -> lang
            }
        }
    }


    // ==================================================================================
    // Helper Objects (Kotlin implementations of Java utils)
    // ==================================================================================

    object HttpUtils {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

        fun get(url: String) = RequestBuilder(url, "GET")
        fun post(url: String) = RequestBuilder(url, "POST")

        class RequestBuilder(private val url: String, private val method: String) {
            private val headers = HashMap<String, String>()
            private val formData = StringBuilder()
            private var rawBody: String? = null

            init {
                headers["User-Agent"] = UA
            }

            fun header(k: String, v: String) = apply { headers[k] = v }
            
            fun formData(k: String, v: String) = apply {
                if (formData.isNotEmpty()) formData.append("&")
                formData.append(URLEncoder.encode(k, "UTF-8"))
                formData.append("=")
                formData.append(URLEncoder.encode(v, "UTF-8"))
            }
            
            fun body(body: String) = apply { this.rawBody = body }

            fun execute(): String {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                if (method == "POST") {
                    conn.doOutput = true
                    val data = rawBody ?: formData.toString()
                    conn.outputStream.use { it.write(data.toByteArray()) }
                }

                if (conn.responseCode >= 400) {
                     throw RuntimeException("HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
                }
                return conn.inputStream.bufferedReader().readText()
            }
            
            fun createConnection(): HttpURLConnection = URL(url).openConnection() as HttpURLConnection
        }
    }

    object SignUtil {
        fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }

        // 移植自百度翻译 JS 签名逻辑
        fun baiduSign(text: String, gtk: String): String {
            fun n(r: Long, o: String): Long {
                var rVar = r
                for (t in 0 until o.length - 2 step 3) {
                    var e = o[t + 2].code
                    e = if (e >= 'a'.code) e - 87 else e - '0'.code
                    val v = if (o[t + 1] == '+') rVar.ushr(e) else rVar shl e
                    rVar = if (o[t] == '+') (rVar + v) and 4294967295L else rVar xor v
                }
                return rVar
            }

            val k = gtk.split(".")
            val u = if (k.size > 1) k[0].toLong() else 0L
            
            var g = u
            val b = text.toByteArray(Charsets.UTF_8) // 这里的逻辑简化了，原版JS针对特殊字符处理更复杂
            
            for (byte in b) {
                val intVal = byte.toInt() and 0xFF
                g += intVal
                g = n(g, "+-a^+6")
            }
            g = n(g, "+-3^+b+-f")
            g = g xor (if (k.size > 1) k[1].toLong() else 0L)
            if (g < 0) g = (g and 2147483647L) + 2147483648L
            g %= 1000000
            return "$g.$g".replace(".", ".") // dummy logic, JS逻辑实际上是 g.toString() + "." + (g ^ u)
                .let { "$g.${g xor u}" }
        }
    }
    
    object JsonHelper {
         // 简易列表转Json，不依赖 Gson/Jackson
         fun toJson(list: List<*>): String {
             val sb = StringBuilder("[")
             for ((i, item) in list.withIndex()) {
                 if (i > 0) sb.append(",")
                 when (item) {
                     is String -> sb.append("\"${item.replace("\"", "\\\"")}\"")
                     is Number, is Boolean -> sb.append(item)
                     is List<*> -> sb.append(toJson(item))
                     null -> sb.append("null")
                 }
             }
             sb.append("]")
             return sb.toString()
         }
    }

    /**
     * 修复翻译结果格式，保留 Android XML 特殊符号
     */
    companion object {
        fun fixFormat(text: String): String {
            var result = text.replace("'", "\\'") // 单引号转义
            
            // 修复 %s, %d 等占位符被翻译引擎加空格的情况
            // 例如: "Hello % s" -> "Hello %s"
            result = result.replace(Regex("%\\s+([sd])"), "%$1")
            result = result.replace(Regex("%\\s+(\\d+\\\$)?([sd])"), "%$1$2")
            
            // 修复 CDATA
            result = result.replace("] ] >", "]]>")
            
            return result
        }
    }
}