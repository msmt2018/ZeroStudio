package com.zerostudio.language.ui;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.CallHierarchyService;
import com.zerostudio.language.service.CodeCompletionService;
import com.zerostudio.language.service.DiagnosticsService;
import com.zerostudio.language.service.DocumentSymbolsService;
import com.zerostudio.language.service.EditorIntegration;
import com.zerostudio.language.service.FindReferencesService;
import com.zerostudio.language.service.FoldingRangeService;
import com.zerostudio.language.service.FormatterService;
import com.zerostudio.language.service.HoverService;
import com.zerostudio.language.service.RenameService;
import com.zerostudio.language.service.TypeHierarchyService;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * IDE 侧 UI 集成协调器（示例 / 参考实现）。
 *
 * 设计目标：
 *  1. 把 ide-language 的所有服务统一暴露成 IDE 编辑器可消费的回调
 *  2. 回调接口尽量与平台无关（hover / completion / definition / references / outline / folding）
 *  3. 通过 {@link EditorIntegration} 把"跳转打开"动作桥接到 UI 端
 *
 * 用法示例：
 * <pre>{@code
 *   ProjectIndex index = new DefaultProjectIndex();
 *   LanguageServiceCoordinator ui = new LanguageServiceCoordinator(index);
 *   ui.setOnOpenFile(req -> openInEditor(req.file, req.range));
 *
 *   // 绑定到 Sora / CodeEditor：
 *   editor.subscribeHover((line, col) -> ui.computeHover(path, line, col));
 *   editor.subscribeCompletion((line, col, prefix) -> ui.computeCompletion(path, line, col, prefix));
 * }</pre>
 *
 * 注意：这是 reference / sample，真实项目中可以直接复用，也可以按 IDE 的事件系统
 * 重新实现回调。
 */
public final class LanguageServiceCoordinator {

    private final ProjectIndex index;
    private final HoverService hover;
    private final CodeCompletionService completion;
    private final FindReferencesService references;
    private final DocumentSymbolsService symbols;
    private final FoldingRangeService folding;
    private final FormatterService formatter;
    private final DiagnosticsService diagnostics;
    private final CallHierarchyService callHierarchy;
    private final TypeHierarchyService typeHierarchy;
    private final RenameService rename;
    private final EditorIntegration openBridge;
    private final JavaParserFacade parser = new JavaParserFacade();

    public LanguageServiceCoordinator(ProjectIndex index) {
        this.index = index;
        this.hover = new HoverService(index);
        this.completion = new CodeCompletionService(index);
        this.references = new FindReferencesService(index);
        this.symbols = new DocumentSymbolsService(index);
        this.folding = new FoldingRangeService();
        this.formatter = new FormatterService();
        this.diagnostics = new DiagnosticsService(index);
        this.callHierarchy = new CallHierarchyService(index);
        this.typeHierarchy = new TypeHierarchyService(index);
        this.rename = new RenameService(index);
        this.openBridge = new EditorIntegration();
    }

    // ---------------------- 索引辅助 ----------------------

    /**
     * 解析源码并加入索引。
     * UI 端在文件打开 / 编辑时调用此方法保持索引最新。
     */
    public void indexSource(String path, String content) {
        ParsedFile pf = parser.parse(path, content);
        index.index(pf);
    }

    public ProjectIndex index() { return index; }

    // ---------------------- 编辑器回调 ----------------------

    /**
     * 计算 Hover 内容。
     * @return null 表示没有可显示的 hover
     */
    public HoverService.HoverInfo computeHover(String filePath, int line, int column) {
        return hover.hover(filePath, line, column);
    }

    /**
     * 计算代码补全候选。
     * @return 按相关性排序的候选列表（可能为空）
     */
    public List<CodeCompletionService.Item> computeCompletion(String filePath, int line, int column, String prefix) {
        List<CodeCompletionService.Item> items = completion.complete(filePath, line, column, prefix);
        Collections.sort(items, (a, b) -> Integer.compare(b.priority, a.priority));
        return items;
    }

    /**
     * 查找所有引用：用于 "Find Usages" / References 视图。
     */
    public List<FindReferencesService.Match> findReferences(String filePath, int line, int column, boolean includeDeclaration) {
        return references.findReferences(filePath, line, column, includeDeclaration);
    }

    /**
     * 重命名当前位置的符号。
     * @return 重命名后的 WorkspaceEdit；失败返回 null
     */
    public RenameService.WorkspaceEdit renameSymbol(String filePath, int line, int column, String newName) {
        return rename.rename(filePath, line, column, newName);
    }

    /**
     * 把 WorkspaceEdit 应用到指定文件的文本上。
     */
    public String applyRename(RenameService.WorkspaceEdit edit, String filePath, String content) {
        return rename.applyToText(edit, filePath, content);
    }

    /**
     * 列出当前文件的大纲（Outline 视图）。
     */
    public List<DocumentSymbolsService.Symbol> listSymbols(String filePath) {
        return symbols.listSymbols(filePath);
    }

    /**
     * 折叠区域（用于 IDE 的代码折叠）。
     */
    public List<FoldingRangeService.FoldingRange> computeFolding(String content) {
        return folding.computeFoldingRanges(content);
    }

    /**
     * 格式化整个文件。
     */
    public String format(String content) {
        return formatter.format(content);
    }

    /**
     * 对当前文件做诊断检查，返回错误 / 警告列表。
     */
    public List<DiagnosticsService.Diagnostic> diagnose(String filePath) {
        return diagnostics.check(filePath);
    }

    /**
     * 对整个项目做诊断（增量同步场景下使用）。
     */
    public List<DiagnosticsService.Diagnostic> diagnoseAll() {
        return diagnostics.checkAll();
    }

    /**
     * 查找调用了 methodName 的所有位置（Callers）。
     */
    public List<CallHierarchyService.CallSite> callersOf(String methodName) {
        return callHierarchy.callersOf(methodName);
    }

    /**
     * 查找 methodName 调用了哪些方法（Callees）。
     */
    public List<CallHierarchyService.CallSite> calleesOf(String containingClass, String methodName) {
        return callHierarchy.calleesOf(containingClass, methodName);
    }

    /**
     * 给定 FQN，返回所有父类型。
     */
    public java.util.Set<String> supertypesOf(String fqn) {
        return typeHierarchy.supertypesOf(fqn);
    }

    /**
     * 给定 FQN，返回所有子类型。
     */
    public java.util.Set<String> subtypesOf(String fqn) {
        return typeHierarchy.subtypesOf(fqn);
    }

    // ---------------------- 跳转 ----------------------

    /**
     * 注册一个"打开文件"回调（IDE 编辑器可订阅这个动作）。
     */
    public void setOnOpenFile(Consumer<EditorIntegration.OpenRequest> handler) {
        openBridge.setOpenHandler(handler::accept);
    }

    /**
     * 触发一次"打开文件"动作。
     * 适用于 Go-to-Definition、Find References 的双击跳转等。
     */
    public void openAt(String filePath, SourcePosition pos) {
        openBridge.openRealFile(filePath, new SourceRange(pos, pos));
    }

    public void openRange(String filePath, SourceRange range) {
        openBridge.openRealFile(filePath, range);
    }

    public void openVirtual(String displayPath, String content, SourcePosition cursor) {
        openBridge.openVirtual(displayPath, content, cursor);
    }

    // ---------------------- 增量更新 ----------------------

    /**
     * 当文件被修改时调用，重新解析并刷新索引。
     */
    public void onFileChanged(String path, String newContent) {
        index.remove(path);
        indexSource(path, newContent);
    }

    /**
     * 当文件被删除时调用。
     */
    public void onFileDeleted(String path) {
        index.remove(path);
    }
}
