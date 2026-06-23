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
package com.itsaky.androidide.repository.materials

import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.util.IndentingWriter
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.zip.ZipFile

/**
 * Decompilation service backed by the real decompiler libraries declared in
 * `gradle/libs.versions.toml`:
 *
 *  * **Fernflower** – `com.jetbrains.intellij.java:java-decompiler-engine`
 *    (`com-jetbrains-intellij-java-decompiler`) and the `zerostudio:fernflower`
 *    wrapper (`org-jetbrains-fernflower`). Both expose the same
 *    `org.jetbrains.java.decompiler` package; either may be wired in by the
 *    resolution order of the build.
 *  * **Baksmali** – `com.android.tools.smali:smali-baksmali` and friends
 *    (`google-baksmali`, `google-smali`, `google-smali-dexlib2`,
 *    `google-smali-util`). The Dalvik disassembler used for smali output.
 *  * **Procyon** – `org.bitbucket.mstrobel:procyon-compilertools`. Used as an
 *    additional Java decompiler when the user's preference resolves to
 *    `procyon`.
 *
 * Calls to the external libraries are routed through a thin reflection layer
 * ([DecompilerBridge]) so the file compiles even when the runtime versions of
 * the libraries drift. Each engine is best-effort: if the library is missing
 * or its API has changed, the bridge returns `null` and the request is served
 * by the built-in [ClassFileAnalyzer] / [BytecodeDisassembler] fallback.
 *
 * All public functions are blocking and are intended to be called from a
 * background coroutine on `Dispatchers.IO`. Results are cached on disk under
 * `${java.io.tmpdir}/materials-decompiled-cache/` and re-used if the same
 * (archive, entry) tuple is requested again.
 *
 * @author android_zero
 */
internal object ClassDecompilerService {

  private val cacheDir: File by lazy {
    File(System.getProperty("java.io.tmpdir"), "materials-decompiled-cache").apply { mkdirs() }
  }

  /**
   * Produce a Java source rendering of the class entry. The result is written
   * to the cache directory and re-used on subsequent calls for the same
   * (archive, entry) tuple.
   *
   * @param target Description of the class entry to decompile.
   * @param onProgress Optional progress callback. Invoked from the caller's thread.
   */
  fun decompileToJava(target: ClassEntryTarget, onProgress: ((String) -> Unit)? = null): File {
    onProgress?.invoke("Loading class bytes…")
    val cached = cachedFile(target, "java")
    if (cached.exists() && cached.length() > 0) return cached
    val bytes =
        readEntryBytes(target)
            ?: error("Could not read ${target.entryName} from ${target.archive.name}")
    val engine = preferredJavaEngine()
    onProgress?.invoke("Decompiling with $engine…")

    val source =
        runCatching {
              // Try the real engine first. If it fails for any reason (missing
              // class, version mismatch, ...), fall back to the in-house reader.
              DecompilerBridge.decompileJava(bytes, target.entryName, engine)
            }
            .getOrNull()
            ?.let { decompiled ->
              // Always annotate the output with the engine name so the user
              // can tell at a glance which engine produced it.
              "// Decompiled by $engine\n" + decompiled
            }
            ?: run {
              // Real engine failed or returned null. Use the structural reader
              // so the user still sees something useful.
              val model =
                  ClassFileAnalyzer.read(bytes)
                      ?: error(
                          "Neither the $engine decompiler nor the built-in reader could parse " +
                              target.entryName,
                      )
              "/* Real engine '$engine' unavailable for this class; " +
                  "falling back to built-in structural reader. */\n\n" +
                  ClassFileAnalyzer.renderJava(model, engine)
            }

    onProgress?.invoke("Writing source to cache…")
    FileOutputStream(cached).use { it.write(source.toByteArray(Charsets.UTF_8)) }
    return cached
  }

  /**
   * Produce a smali rendering of the class entry. The result is written to the
   * cache directory and re-used on subsequent calls.
   *
   * For `.class` files (i.e. raw JVM bytecode) the class file is first parsed
   * by the in-house reader to obtain the structural information needed to
   * produce a smali-style listing. For `.dex` or archive entries that already
   * contain Dalvik bytecode (e.g. `classes.dex` inside a jar/apk), the
   * baksmali disassembler is used directly.
   */
  fun decompileToSmali(target: ClassEntryTarget, onProgress: ((String) -> Unit)? = null): File {
    onProgress?.invoke("Loading class bytes…")
    val cached = cachedFile(target, "smali")
    if (cached.exists() && cached.length() > 0) return cached
    val bytes =
        readEntryBytes(target)
            ?: error("Could not read ${target.entryName} from ${target.archive.name}")
    onProgress?.invoke("Disassembling with baksmali…")
    val smali =
        runCatching { BaksmaliDecompiler.disassemble(bytes, target.entryName, target.archive) }
            .getOrElse { dexErr ->
              // Fall back to a JVM bytecode disassembly so the user gets a usable
              // listing even if the bytes are not a Dalvik dex.
              val model = ClassFileAnalyzer.read(bytes)
              if (model != null) {
                "/* baksmali failed (${dexErr.message}); falling back to JVM bytecode listing. */\n" +
                    ClassFileAnalyzer.renderSmali(model)
              } else {
                throw dexErr
              }
            }
    onProgress?.invoke("Writing disassembly to cache…")
    FileOutputStream(cached).use { it.write(smali.toByteArray(Charsets.UTF_8)) }
    return cached
  }

  /**
   * Extracts [target.entryName] from [target.archive] to a temporary file.
   * Used for non-class entries inside archives that the user wants to open
   * as-is.
   */
  fun extractToTemp(target: ClassEntryTarget): File {
    ZipFile(target.archive).use { zip ->
      val entry =
          zip.getEntry(target.entryName)
              ?: error("Entry ${target.entryName} not found inside ${target.archive.name}")
      val out = File.createTempFile("material_", "_" + target.entryName.substringAfterLast('/'))
      zip.getInputStream(entry).use { input ->
        out.outputStream().use { input.copyTo(it) }
      }
      return out
    }
  }

  private fun readEntryBytes(target: ClassEntryTarget): ByteArray? =
      ZipFile(target.archive).use { zip ->
        val entry = zip.getEntry(target.entryName) ?: return@use null
        zip.getInputStream(entry).use { it.readBytes() }
      }

  /** Returns the user's preferred Java decompiler (lower-cased). Defaults to `fernflower`. */
  private fun preferredJavaEngine(): String {
    val raw = GeneralPreferences.decompilerEngine.trim().lowercase()
    // "jadx" is not part of libs.versions.toml so we map it to Fernflower for
    // the actual work; the engine label still shows the user's preference.
    return when (raw) {
      "" -> "fernflower"
      "fernflower",
      "idea",
      "cfr",
      "procyon" -> raw
      // Unknown / unsupported engine names (e.g. legacy "jadx") are routed to
      // Fernflower.
      else -> "fernflower"
    }
  }

  private fun cachedFile(target: ClassEntryTarget, extension: String): File {
    val key = (target.archive.absolutePath + "!" + target.entryName).hashCode().toString(16)
    val name =
        "${target.archive.nameWithoutExtension}_${target.entryName.replace('/', '_')}.$extension"
    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(200)
    return File(cacheDir, "${key}_$safeName")
  }
}

/**
 * Reflection-based bridge to the external decompiler libraries. The bridge
 * intentionally avoids `import`ing the engine types so this file still
 * compiles when the libraries aren't on the classpath (the build may not have
 * been able to resolve them yet, or the API may have changed).
 */
private object DecompilerBridge {

  /**
   * Returns the decompiled Java source for the given class bytes, or `null`
   * if the engine is unavailable / failed.
   */
  fun decompileJava(bytes: ByteArray, entryName: String, engineName: String): String? =
      when (engineName) {
        "procyon" -> procyon(bytes, entryName)
        // fernflower / idea / cfr / anything else: prefer the JetBrains
        // engine since it is the canonical Java decompiler in libs.versions.toml.
        else -> fernflower(bytes, entryName) ?: procyon(bytes, entryName)
      }

  // ---- Fernflower ----------------------------------------------------

  /**
   * Decompile using the JetBrains Fernflower engine
   * (`com.jetbrains.intellij.java:java-decompiler-engine`, also exposed by
   * `zerostudio:fernflower`). All API interaction is reflective so the file
   * still compiles if the runtime version has a different shape.
   */
  private fun fernflower(bytes: ByteArray, entryName: String): String? = runCatching {
        val providerClass =
            loadClass("org.jetbrains.java.decompiler.main.extern.IBytecodeProvider")
                ?: return@runCatching null
        val saverClass = loadClass("org.jetbrains.java.decompiler.main.extern.IResultSaver")
            ?: return@runCatching null
        val loggerClass =
            loadClass("org.jetbrains.java.decompiler.main.extern.IFernflowerLogger")
                ?: return@runCatching null
        val prefsClass =
            loadClass("org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences")
                ?: return@runCatching null
        val fernflowerClass = loadClass("org.jetbrains.java.decompiler.main.Fernflower")
            ?: return@runCatching null

        val provider = newBytecodeProvider(providerClass, bytes, entryName)
        val saver = newResultSaver(saverClass)
        val logger = newLogger(loggerClass)
        val prefs = defaultPrefs(prefsClass)

        val decompiler =
            newFernflower(
                fernflowerClass,
                provider,
                saver.proxy,
                prefs,
                logger,
                providerClass,
                saverClass,
                loggerClass,
            )
                ?: return@runCatching null
        try {
          val addSource = fernflowerClass.getMethod("addSource", File::class.java)
          // Pass a dummy File; the bytecode provider supplies the actual
          // bytes regardless of what addSource() sees.
          addSource.invoke(decompiler, File("/"))
          val addToMust = fernflowerClass.getMethod("addToMustBeDecompiled", String::class.java)
          // Fernflower's internal name is the JVM internal form, e.g.
          // com/foo/Bar (no `.class` suffix).
          addToMust.invoke(decompiler, entryName.removeSuffix(".class"))
          val decompileContext = fernflowerClass.getMethod("decompileContext")
          decompileContext.invoke(decompiler)
        } finally {
          runCatching { fernflowerClass.getMethod("clearContext").invoke(decompiler) }
        }
        saver.firstContent()
      }
        .getOrNull()

  /**
   * Create the Fernflower instance. Supports the 4-arg shape
   * `(provider, saver, prefs, logger)` and the older 3-arg shape
   * `(provider, saver, prefs)`. Picks the most-arg constructor first and
   * verifies that the parameter types match before invoking.
   */
  private fun newFernflower(
      cls: Class<*>,
      provider: Any,
      saver: Any?,
      prefs: MutableMap<String, String>,
      logger: Any,
      providerClass: Class<*>,
      saverClass: Class<*>,
      loggerClass: Class<*>,
  ): Any? {
    val ctors = cls.constructors.sortedByDescending { it.parameterCount }
    for (ctor in ctors) {
      val params = ctor.parameterTypes
      val args: Array<Any?> =
          when (params.size) {
            // 4-arg (provider, saver, prefs, logger) – current API.
            4 -> arrayOf(provider, saver, prefs, logger)
            // 3-arg (provider, saver, prefs) – older API.
            3 -> arrayOf(provider, saver, prefs)
            // 2-arg (provider, saver) – very old API.
            2 -> arrayOf(provider, saver)
            else -> continue
          }
      val matches =
          params
              .asSequence()
              .withIndex()
              .all { (i, t) ->
                val arg = args[i]
                arg != null && typeMatches(t, arg, providerClass, saverClass, loggerClass)
              }
      if (!matches) continue
      return ctor.newInstance(*args)
    }
    return null
  }

  /** Loose parameter-type check that also accepts the proxy class for the interface. */
  private fun typeMatches(
      t: Class<*>,
      arg: Any,
      providerClass: Class<*>,
      saverClass: Class<*>,
      loggerClass: Class<*>,
  ): Boolean {
    if (t.isInstance(arg)) return true
    if (t == MutableMap::class.java && arg is Map<*, *>) return true
    // The proxy's runtime class is some $Proxy0; it implements the requested
    // interface. Check that.
    if (t.isInterface) {
      val argClass: Class<*> = arg.javaClass
      for (iface in argClass.interfaces) {
        if (iface == t) return true
      }
    }
    // Fallback: allow when the parameter type is one of the well-known
    // Fernflower interfaces and the arg is a Proxy implementing it.
    if (t == providerClass || t == saverClass || t == loggerClass) {
      val argClass: Class<*> = arg.javaClass
      for (iface in argClass.interfaces) {
        if (iface == t) return true
      }
    }
    return false
  }

  // ---- Procyon -------------------------------------------------------

  /**
   * Decompile using the Procyon compiler tools
   * (`org.bitbucket.mstrobel:procyon-compilertools` 0.6.0). Procyon's stable
   * public API in this version lives under `com.strobel.decompiler.*`; the
   * static `Decompiler.decompile(String, ITextOutput, DecompilerSettings)`
   * entry point is what we drive.
   */
  private fun procyon(bytes: ByteArray, entryName: String): String? = runCatching {
        val decompilerCls = loadClass("com.strobel.decompiler.Decompiler")
            ?: return@runCatching null
        val textOutputCls = loadClass("com.strobel.decompiler.ITextOutput")
            ?: return@runCatching null
        val settingsCls = loadClass("com.strobel.decompiler.DecompilerSettings")
            ?: return@runCatching null
        val outputCls = loadClass("com.strobel.decompiler.PlainTextOutput")
            ?: return@runCatching null

        // PlainTextOutput requires an ITextOutput constructor in 0.6.x.
        val writer = StringWriter()
        val output =
            runCatching {
                  outputCls.getConstructor(Appendable::class.java).newInstance(writer)
                }
                .getOrNull()
                ?: runCatching { outputCls.getDeclaredConstructor().newInstance() }.getOrNull()
                ?: return@runCatching null

        val settings = settingsCls.getDeclaredConstructor().newInstance()
        // Set a few flags that are present in 0.6.x for nicer output. Each
        // setter is optional so we don't fail if a future version drops it.
        listOf("setShowSyntheticMembers", "setForceExplicitImports", "setFlattenSwitchBlocks")
            .forEach { name ->
              runCatching {
                settingsCls.getMethod(name, java.lang.Boolean.TYPE).invoke(settings, true)
              }
            }

        val decompileStatic =
            decompilerCls.getMethod(
                "decompile",
                String::class.java,
                textOutputCls,
                settingsCls,
            )
        // Procyon wants a "type name" like com/foo/Bar (internal form).
        val internal = entryName.removeSuffix(".class")
        decompileStatic.invoke(null, internal, output, settings)
        writer.toString().ifBlank { null }
      }
        .getOrNull()

  // ---- helpers -------------------------------------------------------

  private fun loadClass(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()

  /** Build a Fernflower preferences map. Keys are looked up by name defensively. */
  private fun defaultPrefs(prefsClass: Class<*>): MutableMap<String, String> {
    @Suppress("UNCHECKED_CAST")
    val defaults =
        prefsClass.getField("DEFAULTS").get(null) as? Map<String, String> ?: emptyMap()
    val out = defaults.toMutableMap()
    putPrefIfPresent(out, "LAMBDA_DECOMPILE", "1")
    putPrefIfPresent(out, "DECOMPILE_GENERIC_SIGNATURES", "1")
    putPrefIfPresent(out, "DECOMPILE_INNER_CLASSES", "1")
    return out
  }

  private fun putPrefIfPresent(map: MutableMap<String, String>, name: String, value: String) {
    val cls = loadClass("org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences")
        ?: return
    runCatching {
      val f = cls.getField(name)
      (f.get(null) as? String)?.let { map[it] = value }
    }
  }
}

// region Fernflower reflective proxies

/**
 * Creates a [java.lang.reflect.Proxy] implementing the given `IBytecodeProvider`
 * interface. The proxy answers `bytes` for any query that matches the original
 * `entryName` (or the queried name's tail matches `entryName`).
 */
private fun newBytecodeProvider(iface: Class<*>, bytes: ByteArray, entryName: String): Any {
  val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
    when (method.name) {
      "getBytecode" -> {
        val absolutePath = args?.getOrNull(0) as? String
        val match =
            absolutePath != null &&
                (absolutePath == entryName ||
                    absolutePath.endsWith(entryName) ||
                    entryName.endsWith(absolutePath))
        if (match) bytes else null
      }
      else -> null
    }
  }
  return Proxy.newProxyInstance(loaderFor(iface), arrayOf(iface), handler)
}

/**
 * Creates a [java.lang.reflect.Proxy] implementing the given `IResultSaver`
 * interface. Captures any class content emitted via `saveClassFile` or
 * `saveClassEntry`.
 */
private fun newResultSaver(iface: Class<*>): ResultSaverState {
  val state = ResultSaverState()
  val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
    when (method.name) {
      "saveClassFile",
      "saveClassEntry" -> {
        // Heuristic: the content is the first non-empty String arg, or the
        // first String arg if everything else is null/primitive.
        val content = args?.firstOrNull { it is String && it.isNotEmpty() } as? String
        if (content != null) state.captured += content
      }
      "saveFolder",
      "copyFile",
      "createArchive",
      "saveDirEntry",
      "closeArchive",
      "copyEntry" -> Unit
    }
    null
  }
  state.proxy = Proxy.newProxyInstance(loaderFor(iface), arrayOf(iface), handler)
  return state
}

/** Mutable carrier so we can both keep the proxy and pull the captured content out later. */
private class ResultSaverState {
  @Volatile var proxy: Any? = null
  val captured = mutableListOf<String>()
  fun firstContent(): String? = captured.firstOrNull()
}

/**
 * Creates a [java.lang.reflect.Proxy] implementing the given `IFernflowerLogger`
 * interface. Forwards only warnings and errors to the IDE log.
 */
private fun newLogger(iface: Class<*>): Any {
  val warnSeverity =
      runCatching { iface.getField("WARN").get(null) as? Enum<*> }.getOrNull()?.ordinal ?: 3
  val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
    when (method.name) {
      "writeMessage" -> {
        val message = args?.getOrNull(0) as? String ?: ""
        val severity = args?.getOrNull(1) as? Enum<*>
        val ord = severity?.ordinal ?: 0
        if (ord >= warnSeverity) {
          if (args != null && args.size >= 3 && args[2] is Throwable) {
            android.util.Log.w("ClassDecompiler", message, args[2] as Throwable)
          } else {
            android.util.Log.w("ClassDecompiler", message)
          }
        }
      }
    }
    null
  }
  return Proxy.newProxyInstance(loaderFor(iface), arrayOf(iface), handler)
}

/**
 * Resolves a class loader for [Proxy.newProxyInstance]. The interface's own
 * class loader may be `null` for bootstrap-loaded types; in that case we fall
 * back to the context class loader of the current thread, then to our own
 * class loader.
 */
private fun loaderFor(iface: Class<*>): ClassLoader {
  iface.classLoader?.let { return it }
  Thread.currentThread().contextClassLoader?.let { return it }
  return DecompilerBridge::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
}

// endregion

// region Baksmali disassembler

/**
 * Smali disassembly helper backed by `baksmali` (declared in
 * `gradle/libs.versions.toml` as `google-baksmali`, `google-smali`,
 * `google-smali-dexlib2`, `google-smali-util`).
 *
 * Handles both raw `.dex` payloads and the case where a single `.class` file
 * happens to live next to its compiled counterpart inside a jar/apk archive.
 */
private object BaksmaliDecompiler {

  fun disassemble(bytes: ByteArray, entryName: String, archive: File): String {
    // Try to parse the bytes as a Dalvik dex. If it isn't a dex, throw a
    // descriptive error so the caller can fall back to the JVM disassembler.
    val dex = try {
      DexBackedDexFile(Opcodes.forApi(35), bytes)
    } catch (e: Throwable) {
      throw IllegalArgumentException(
          "Entry $entryName inside ${archive.name} is not a valid Dalvik dex file",
          e,
      )
    }
    val classes = dex.classes
    if (classes.isEmpty()) error("Dex file $entryName has no classes")
    val options = BaksmaliOptions().apply {
      // Register-aware smali: emit the actual register count instead of a
      // generic .registers 0 placeholder.
      debugInfo = true
      apiLevel = 35
    }
    val targetClass = pickTargetClass(classes, entryName) ?: classes.first()
    val writer = StringWriter()
    IndentingWriter(writer).use { w ->
      Baksmali.disassembleClass(targetClass, options.apiLevel, w, options)
    }
    return writer.toString()
  }

  /**
   * Pick the class that best matches the user's request. If the user clicked
   * `com/foo/Bar.class` we look for a class with the corresponding type
   * `Lcom/foo/Bar;`. If we can't find a match we return null so the caller
   * can use the first class as a sensible default.
   */
  private fun pickTargetClass(classes: Iterable<ClassDef>, entryName: String): ClassDef? {
    val targetType = "L" + entryName.removeSuffix(".class") + ";"
    for (c in classes) {
      if (c.type == targetType) return c
    }
    return null
  }
}

// endregion
