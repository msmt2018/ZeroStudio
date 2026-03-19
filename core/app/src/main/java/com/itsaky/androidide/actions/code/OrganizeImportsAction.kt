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

package com.itsaky.androidide.actions.code

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.hasRequiredData
import com.itsaky.androidide.actions.requireEditor
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.Content
import java.io.File
import java.util.Collections
import java.util.Locale

/**
 * An enhanced action to organize imports for multiple programming languages.
 *
 * Features:
 * 1. Multi-language support (Java, Kotlin, Groovy, C#, Python, JS/TS, Dart, Swift, etc.)
 * 2. Smart sorting: Lowercase (a-z) priority over Uppercase (A-Z).
 * 3. Seamless update: Uses replace() to prevent visual jumping.
 * 4. Supports organizing entire file or selected range.
 *
 * TODO：拓展开关设置选项：1.同名路径下增加空行。2.删除import之间的空行
 *
 * @author android_zero
 */
class OrganizeImportsAction(
    context: Context,
    override val order: Int
) : EditorActionItem {

    override val id: String = "ide.editor.action.organizeImports"
    override var label: String = context.getString(R.string.menu_organize_imports)
    override var visible: Boolean = true
    override var enabled: Boolean = true
    override var icon = ContextCompat.getDrawable(context, R.drawable.organize_layout)
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    /**
     * Defines the syntax rules for a specific language.
     * @param importKeywords List of keywords that start an import line (e.g., ["import", "from"]).
     * @param packageKeyword The keyword for package declaration (optional).
     * @param ignoredKeywords Keywords to ignore/strip during sorting (usually same as importKeywords + "static").
     * @param terminator Semicolon or other terminator to strip for comparison (optional).
     */
    private data class LanguageProfile(
        val importKeywords: List<String>,
        val packageKeyword: String? = null,
        val ignoredKeywords: List<String> = importKeywords,
        val terminator: String? = null
    )

    private val languages = HashMap<String, LanguageProfile>().apply {
        // Java, Groovy
        val javaStyle = LanguageProfile(listOf("import "), "package ", listOf("import ", "static "), ";")
        put("java", javaStyle)
        put("groovy", javaStyle)
        put("gradle", javaStyle) // Gradle usually follows Groovy syntax

        // Kotlin (No semicolon mandatory, but same structure)
        val kotlinStyle = LanguageProfile(listOf("import "), "package ", listOf("import "), null)
        put("kt", kotlinStyle)
        put("kts", kotlinStyle)
        put("ktm", kotlinStyle)

        // C# (uses 'using')
        val csharpStyle = LanguageProfile(listOf("using "), "namespace ", listOf("using ", "static "), ";")
        put("cs", csharpStyle)

        // JavaScript / TypeScript (ES6 Modules)
        val jsStyle = LanguageProfile(listOf("import ", "require("), null, listOf("import "), ";")
        put("js", jsStyle)
        put("ts", jsStyle)
        put("jsx", jsStyle)
        put("tsx", jsStyle)
        put("mjs", jsStyle)
        put("cjs", jsStyle)

        // Python (import x, from x import y)
        val pythonStyle = LanguageProfile(listOf("import ", "from "), null, listOf("import ", "from "), null)
        put("py", pythonStyle)
        put("pyi", pythonStyle)

        // Dart
        val dartStyle = LanguageProfile(listOf("import ", "export "), "library ", listOf("import ", "export "), ";")
        put("dart", dartStyle)

        // Swift
        val swiftStyle = LanguageProfile(listOf("import "), null, listOf("import "), null)
        put("swift", swiftStyle)
        
        // Lua
        val luaStyle = LanguageProfile(listOf("require ", "local "), null, listOf("require ", "local "), null)
        put("lua", luaStyle)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val file = data.get(File::class.java)
        if (file != null) {
            val ext = file.extension.lowercase(Locale.getDefault())
            // Visible only if we have a profile for this extension
            visible = languages.containsKey(ext)
            enabled = visible
        } else {
            visible = false
            enabled = false
        }
    }

    override suspend fun execAction(data: ActionData): Any {
        if (!data.hasRequiredData(File::class.java)) {
            return false
        }

        val file = data.requireFile()
        val ext = file.extension.lowercase(Locale.getDefault())
        val profile = languages[ext] ?: return false

        val editor = data.requireEditor()
        val text = editor.text
        val cursor = editor.cursor

        if (cursor.isSelected) {
            // Organize only within the selected lines
            organizeSelection(text, cursor.leftLine, cursor.rightLine, profile)
        } else {
            // Organize the whole file
            organizeWholeFile(text, profile)
        }
        return true
    }

    /**
     * Logic for organizing imports when a selection exists.
     */
    private fun organizeSelection(content: Content, startLine: Int, endLine: Int, profile: LanguageProfile) {
        val importLines = ArrayList<String>()
        val lineIndices = ArrayList<Int>()

        // Collect imports in the selected range
        for (i in startLine..endLine) {
            if (i >= content.lineCount) break
            val line = content.getLineString(i)
            if (isImportLine(line, profile)) {
                importLines.add(line)
                lineIndices.add(i)
            }
        }

        if (importLines.isEmpty() || importLines.size == 1) return

        // Sort using the language profile
        sortImports(importLines, profile)

        // Apply changes (Seamlessly)
        applyChangesSeamlessly(content, importLines, lineIndices)
    }

    /**
     * Logic for organizing imports for the entire file.
     */
    private fun organizeWholeFile(content: Content, profile: LanguageProfile) {
        val lineCount = content.lineCount
        val importLines = ArrayList<String>()
        val linesToRemove = ArrayList<Int>()

        var packageLineIndex = -1
        var firstImportIndex = -1

        // Scan document
        for (i in 0 until lineCount) {
            val line = content.getLineString(i)
            val trimmed = line.trim()

            // Check for package/namespace declaration
            if (profile.packageKeyword != null && trimmed.startsWith(profile.packageKeyword)) {
                packageLineIndex = i
            } 
            // Check for import lines
            else if (isImportLine(trimmed, profile)) {
                if (firstImportIndex == -1) {
                    firstImportIndex = i
                }
                importLines.add(line)
                linesToRemove.add(i)
            }
        }

        if (importLines.isEmpty()) return

        // Sort
        sortImports(importLines, profile)

        // Determine Insertion Point
        var insertAtLine = 0
        if (firstImportIndex != -1) {
            // If imports existed, use the position of the first found import
            insertAtLine = firstImportIndex
        } else if (packageLineIndex != -1) {
            // If no imports but package exists, insert after package
            insertAtLine = packageLineIndex + 1
        }
        
        // Check for Shebang or Header comments (simple heuristic) if inserting at top
        if (firstImportIndex == -1 && packageLineIndex == -1) {
            // Try to skip shebang (#!) or top-level file comments if possible
            // This is a basic check.
            if (content.lineCount > 0 && content.getLineString(0).startsWith("#!")) {
                insertAtLine = 1
            }
        }

        // Optimization: Check if contiguous block
        var isContiguous = true
        if (linesToRemove.isNotEmpty()) {
            val start = linesToRemove[0]
            for (k in 0 until linesToRemove.size) {
                if (linesToRemove[k] != start + k) {
                    isContiguous = false
                    break
                }
            }
            // Check if the contiguous block is exactly where we want to insert
            if (isContiguous && start == insertAtLine) {
                applyChangesSeamlessly(content, importLines, linesToRemove)
                return
            }
        }

        // Scattered imports case (Hard replacement)
        content.beginBatchEdit()
        try {
            // A. Remove old lines (bottom-up)
            linesToRemove.sortDescending()
            for (i in linesToRemove) {
                if (i < content.lineCount) {
                    val colCount = content.getColumnCount(i)
                    if (i < content.lineCount - 1) {
                        content.delete(i, 0, i + 1, 0)
                    } else {
                        content.delete(i, 0, i, colCount)
                    }
                }
            }

            // B. Adjust insertion point if it was below deleted lines (unlikely for imports, but safe to check)
            // Since imports are usually removed from below the package, and we insert after package, 
            // the insertion index (if based on package) implies stability. 
            // If based on firstImportIndex, that index is now logically "empty" or pointing to the next line.
            
            // C. Insert new block
            val sb = StringBuilder()
            val distinctImports = importLines.distinct()
            
            // Check if we need to prepend/append newlines for spacing
            // e.g., if inserting after package, ensure separating line if not present
            // For now, we strictly follow the file's original spacing style by just dumping imports.
            
            for (imp in distinctImports) {
                sb.append(imp).append('\n')
            }
            
            // Ensure insertion is valid
            val safeInsertLine = insertAtLine.coerceAtMost(content.lineCount)
            content.insert(safeInsertLine, 0, sb.toString())

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            content.endBatchEdit()
        }
    }

    /**
     * Checks if a line matches any of the import keywords for the language profile.
     */
    private fun isImportLine(line: String, profile: LanguageProfile): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        
        // Fast check
        for (keyword in profile.importKeywords) {
            if (trimmed.startsWith(keyword)) return true
        }
        return false
    }

    private fun sortImports(imports: MutableList<String>, profile: LanguageProfile) {
        Collections.sort(imports, ImportComparator(profile))
    }

    private fun applyChangesSeamlessly(
        content: Content,
        sortedImports: List<String>,
        lineIndices: List<Int>
    ) {
        val uniqueImports = sortedImports.distinct()
        
        content.beginBatchEdit()
        try {
            // Case 1: 1-to-1 replacement
            if (uniqueImports.size == lineIndices.size) {
                for (i in uniqueImports.indices) {
                    val lineIdx = lineIndices[i]
                    val newLineContent = uniqueImports[i]
                    val oldLineContent = content.getLineString(lineIdx)
                    
                    if (newLineContent != oldLineContent) {
                        content.replace(lineIdx, 0, lineIdx, content.getColumnCount(lineIdx), newLineContent)
                    }
                }
            } else {
                // Case 2: Size changed (duplicates removed), replace whole block
                // Assume indices sorted
                val sortedIndices = lineIndices.sorted()
                
                // Check contiguous
                var isContiguous = true
                for (k in 0 until sortedIndices.size - 1) {
                    if (sortedIndices[k+1] != sortedIndices[k] + 1) {
                        isContiguous = false
                        break
                    }
                }

                if (isContiguous) {
                    val startLine = sortedIndices.first()
                    val endLine = sortedIndices.last()
                    
                    val sb = StringBuilder()
                    for (imp in uniqueImports) {
                        sb.append(imp).append('\n')
                    }
                    val deleteEndLine = endLine + 1
                    content.replace(startLine, 0, deleteEndLine, 0, sb.toString())
                } else {
                    // Non-contiguous fallback
                    val insertAt = sortedIndices.first()
                    for (idx in sortedIndices.sortedDescending()) {
                        content.delete(idx, 0, idx + 1, 0)
                    }
                    val sb = StringBuilder()
                    for (imp in uniqueImports) {
                        sb.append(imp).append('\n')
                    }
                    content.insert(insertAt, 0, sb.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            content.endBatchEdit()
        }
    }

    /**
     * Context-aware comparator.
     * Extracts meaningful content based on the LanguageProfile.
     */
    private class ImportComparator(val profile: LanguageProfile) : Comparator<String> {
        override fun compare(s1: String, s2: String): Int {
            val c1 = extractSignificantPart(s1)
            val c2 = extractSignificantPart(s2)

            if (c1.isEmpty() && c2.isNotEmpty()) return -1
            if (c1.isNotEmpty() && c2.isEmpty()) return 1
            if (c1.isEmpty() && c2.isEmpty()) return 0

            val char1 = c1[0]
            val char2 = c2[0]

            val isLow1 = char1.isLowerCase()
            val isLow2 = char2.isLowerCase()

            // User Rule: a-z before A-Z
            if (isLow1 && !isLow2) return -1
            if (!isLow1 && isLow2) return 1

            return c1.compareTo(c2)
        }

        private fun extractSignificantPart(line: String): String {
            var temp = line.trim()
            
            // Strip keywords defined in profile
            // Loop through ignored keywords (e.g. "import ", "static ", "using ")
            var changed = true
            while (changed) {
                changed = false
                for (keyword in profile.ignoredKeywords) {
                    if (temp.startsWith(keyword)) {
                        temp = temp.substring(keyword.length).trim()
                        changed = true // Re-run loop in case of multiple keywords (e.g. "import static")
                    }
                }
            }

            // Strip terminator if exists
            if (profile.terminator != null && temp.endsWith(profile.terminator)) {
                temp = temp.substring(0, temp.length - profile.terminator.length).trim()
            }
            
            return temp
        }
    }
}