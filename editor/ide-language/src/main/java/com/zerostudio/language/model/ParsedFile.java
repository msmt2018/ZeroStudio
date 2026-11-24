package com.zerostudio.language.model;
import java.util.Collections;
import java.util.List;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.lexer.Token;

public final class ParsedFile {
    public final String path;
    public final LanguageId language;
    /** 旧 API：包名。 */
    public final String packageName;
    /** 新 API：源文件最后修改时间（epoch ms）。 */
    public final long lastModified;
    public final String rawText;
    /** 新 API：抽取出的符号列表（resolver 直接消费）。 */
    public final List<Symbol> symbols;
    public final List<Reference> references;
    /** 新 API：词法分析得到的 token 列表。 */
    public final List<Token> tokens;
    /** 新 API：解析错误信息，无错为 null。 */
    public final String parseError;

    /** 旧 5 参数构造。 */
    public ParsedFile(String path, LanguageId language, String packageName,
                      List<Reference> references, String rawText) {
        this(path, language, packageName, 0L, rawText, null, references, null, null);
    }
    /** 新 8 参数构造（KotlinParser / CParser / CppParser 使用）。 */
    public ParsedFile(String path, LanguageId language, long lastModified,
                      String rawText, List<Symbol> symbols, List<Reference> references,
                      List<Token> tokens, String parseError) {
        this(path, language, "", lastModified, rawText, symbols, references, tokens, parseError);
    }
    /** 9 参数全功能构造。 */
    public ParsedFile(String path, LanguageId language, String packageName, long lastModified,
                      String rawText, List<Symbol> symbols, List<Reference> references,
                      List<Token> tokens, String parseError) {
        this.path = path;
        this.language = language;
        this.packageName = packageName == null ? "" : packageName;
        this.lastModified = lastModified;
        this.rawText = rawText;
        this.symbols = symbols == null ? Collections.emptyList() : symbols;
        this.references = references == null ? Collections.emptyList() : references;
        this.tokens = tokens == null ? Collections.emptyList() : tokens;
        this.parseError = parseError;
    }
}
