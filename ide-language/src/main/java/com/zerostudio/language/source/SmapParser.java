package com.zerostudio.language.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 解析 Kotlin/Java 混合源码中的 SMAP（Source Map）属性。
 * SMAP 格式是 JSR-045 标准，断点调试器用它把生成的字节码行号映射回原始 .kt 源。
 *
 * SMAP 头部示例：
 *   SMAP
 *   filename.kt
 *   Kotlin
 *   *S Kotlin
 *   *F
 *   + 1 Main.kt
 *   1 Main.kt
 *   *L
 *   1#1,5:1
 *   3#2,3:6
 *   *E
 *
 * 行映射段格式: inputStartLine#inputFileId,inputLineCount:outputStartLine#outputFileId,outputLineCount
 */
public final class SmapParser {

    public static final class ParsedSmap {
        public final String defaultFile;
        public final Map<Integer, String> fileSection; // fileId -> filename
        public final NavigableMap<Integer, Integer> lineMapping; // outputLine -> inputLine (for defaultFile)

        private ParsedSmap(String defaultFile, Map<Integer, String> fileSection,
                           NavigableMap<Integer, Integer> lineMapping) {
            this.defaultFile = defaultFile;
            this.fileSection = fileSection;
            this.lineMapping = lineMapping;
        }

        public int inputLineForOutputLine(int outputLine) {
            if (lineMapping == null) return -1;
            Map.Entry<Integer, Integer> e = lineMapping.floorEntry(outputLine);
            return e != null ? e.getValue() + (outputLine - e.getKey()) : -1;
        }
    }

    public ParsedSmap parse(InputStream is) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            String defaultFile = null;
            String currentSection = null;
            int currentFileId = 0;
            Map<Integer, String> fileSection = new HashMap<>();
            NavigableMap<Integer, Integer> lineMap = new TreeMap<>();

            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if ("SMAP".equals(line)) continue;
                if (defaultFile == null) { defaultFile = line; continue; }
                if (line.startsWith("*")) {
                    String tag = line.substring(1).trim();
                    currentSection = tag;
                    if ("F".equals(tag) || "C".equals(tag)) {
                        // 文件段：下一行起格式 "id filename" 或 "+ id filename"
                    }
                    continue;
                }
                if ("E".equals(line)) continue;

                if ("F".equals(currentSection) || "C".equals(currentSection)) {
                    // 文件段
                    if (line.startsWith("+")) {
                        String[] parts = line.substring(1).trim().split("\\s+", 2);
                        if (parts.length == 2) {
                            currentFileId = Integer.parseInt(parts[0]);
                            fileSection.put(currentFileId, parts[1]);
                        }
                    } else {
                        String[] parts = line.trim().split("\\s+", 2);
                        if (parts.length == 2) {
                            currentFileId = Integer.parseInt(parts[0]);
                            fileSection.put(currentFileId, parts[1]);
                        }
                    }
                } else if ("L".equals(currentSection)) {
                    // 行段：inputStartLine#inputFileId,inputLineCount:outputStartLine#outputFileId,outputLineCount
                    LineMapping lm = parseLineMapping(line);
                    if (lm != null) {
                        for (int i = 0; i < lm.outputCount; i++) {
                            int out = lm.outputStart + i;
                            int in = lm.inputStart + i;
                            lineMap.put(out, in);
                        }
                    }
                }
            }
            return new ParsedSmap(defaultFile, fileSection, lineMap);
        }
    }

    private LineMapping parseLineMapping(String line) {
        // Format: 1#1,5:1
        try {
            int colon = line.indexOf(':');
            if (colon < 0) return null;
            String left = line.substring(0, colon);
            String right = line.substring(colon + 1);
            int[] in = parseShorthand(left);
            int[] out = parseShorthand(right);
            if (in == null || out == null) return null;
            return new LineMapping(in[0], in[1], out[0], out[1]);
        } catch (Exception e) { return null; }
    }

    /** "1#1,5" -> [1, 5] or "1,5" -> [1, 5] */
    private int[] parseShorthand(String s) {
        String[] parts = s.split(",");
        if (parts.length < 1) return null;
        int start, count;
        int hash = parts[0].indexOf('#');
        if (hash >= 0) {
            start = Integer.parseInt(parts[0].substring(0, hash));
        } else {
            start = Integer.parseInt(parts[0]);
        }
        if (parts.length >= 2) {
            count = Integer.parseInt(parts[1]);
        } else {
            count = 1;
        }
        return new int[] { start, count };
    }

    private static final class LineMapping {
        final int inputStart;
        final int inputCount;
        final int outputStart;
        final int outputCount;

        LineMapping(int inputStart, int inputCount, int outputStart, int outputCount) {
            this.inputStart = inputStart;
            this.inputCount = inputCount;
            this.outputStart = outputStart;
            this.outputCount = outputCount;
        }
    }
}
