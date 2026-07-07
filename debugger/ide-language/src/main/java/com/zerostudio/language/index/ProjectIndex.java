package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.List;
import java.util.Map;

/**
 * 项目级索引抽象：
 *  - 旧 API（index/remove/fileFor/allClasses/...）：旧 services 与持久化逻辑使用
 *  - 新 API（updateFile/removeFile/lookup/fileCount）：symbol resolver 使用
 *
 * 同时提供 {@link Lookup} 内部接口以支持按名 / FQN / 类型 / 文件的高频只读查询。
 */
public interface ProjectIndex {

    // —— 旧 API —— //

    /** 索引（或更新）一个已解析文件。 */
    void index(ParsedFile file);

    /** 移除指定路径对应的索引条目。 */
    void remove(String filePath);

    /** 清空整个索引。 */
    void clear();

    /** 给定包名返回其下所有类的 FQN。 */
    List<String> classesInPackage(String pkg);

    /** 按 FQN 查找对应的 ParsedFile。 */
    ParsedFile fileFor(String className);

    /** 严格检查：给定 FQN 是否在索引中作为 CLASS / TYPE 声明存在。 */
    boolean hasClass(String fqn);

    /** 按文件路径查找 ParsedFile。 */
    ParsedFile fileForPath(String path);

    /** 模糊查询：name 子串匹配，返回最多 max 个 FQN。 */
    List<String> fuzzySearch(String query, int max);

    /** 索引中所有类的 FQN。 */
    List<String> allClasses();

    /** 索引的文件总数。 */
    int totalFiles();

    /** 索引的类声明总数。 */
    int totalClasses();

    /** 文件路径 → ParsedFile 的快照。 */
    List<Map.Entry<String, ParsedFile>> allFiles();

    /** 简单通配符：{@code a.b.*} 返回以 a.b 开头的 FQN。 */
    List<String> matchWildcard(String pattern);

    // —— 新 API（resolver / 高频查询路径使用） —— //

    /** 更新（或新增）一个文件的索引。语义同 {@link #index(ParsedFile)}。 */
    void updateFile(ParsedFile parsed);

    /** 按文件路径移除。语义同 {@link #remove(String)}。 */
    void removeFile(String path);

    /** 返回当前快照的只读查询视图。 */
    Lookup lookup();

    /** 当前已索引的文件数。语义同 {@link #totalFiles()}。 */
    int fileCount();

    /**
     * 只读查询视图。实现应保证返回的列表是当前状态的不可变快照，
     * 避免外部迭代过程中索引被并发修改。
     */
    interface Lookup {
        List<Symbol> byName(String name);

        List<Symbol> byFqn(String fqn);

        List<Symbol> byKind(SymbolKind kindOnly);

        List<Symbol> inFile(String path);

        List<ParsedFile> files();

        List<ParsedFile> filesOfLanguage(LanguageId lang);

        ParsedFile file(String path);
    }
}
