package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ProjectIndex 持久化：把索引以 JSON 形式保存到磁盘，支持跨 session 缓存。
 * 极简实现 — 不引入 JSON 依赖。
 */
public final class ProjectIndexJson {

    private ProjectIndexJson() {}

    public static String serialize(ProjectIndex idx) {
        if (idx == null) return "{}";
        StringBuilder sb = new StringBuilder("{\"classes\":[");
        boolean first = true;
        for (String fqn : idx.allClasses()) {
            ParsedFile pf = idx.fileFor(fqn);
            if (pf == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"fqn\":\"").append(esc(fqn)).append("\",")
              .append("\"file\":\"").append(esc(pf.path)).append("\",")
              .append("\"package\":\"").append(esc(pf.packageName)).append("\"}");
        }
        sb.append("],\"files\":[");
        first = true;
        for (Map.Entry<String, ParsedFile> e : new ArrayList<>(idx.allFiles())) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"path\":\"").append(esc(e.getKey())).append("\",")
              .append("\"language\":\"").append(e.getValue().language.name()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static void deserialize(String json, ProjectIndex idx) {
        if (json == null || idx == null) return;
        int filesArrStart = json.indexOf("\"files\":[");
        if (filesArrStart < 0) return;
        int filesArrEnd = json.indexOf(']', filesArrStart);
        if (filesArrEnd < 0) return;
        String body = json.substring(filesArrStart + "\"files\":[".length(), filesArrEnd);
        for (String obj : splitTopLevel(body, '{', '}')) {
            if (obj.isEmpty()) continue;
            Map<String, String> map = parseObject(obj);
            ParsedFile pf = new ParsedFile(
                    map.getOrDefault("path", ""),
                    LanguageId.valueOf(map.getOrDefault("language", "UNKNOWN")),
                    map.getOrDefault("package", ""),
                    new ArrayList<Reference>(),
                    ""
            );
            idx.index(pf);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    private static java.util.Map<String, String> parseObject(String obj) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        int i = 0;
        while (i < obj.length()) {
            int ks = obj.indexOf('"', i);
            if (ks < 0) break;
            int ke = obj.indexOf('"', ks + 1);
            if (ke < 0) break;
            String k = obj.substring(ks + 1, ke);
            int colon = obj.indexOf(':', ke);
            if (colon < 0) break;
            int vs = colon + 1;
            while (vs < obj.length() && Character.isWhitespace(obj.charAt(vs))) vs++;
            String v;
            int ve;
            if (obj.charAt(vs) == '"') {
                ve = findStringEnd(obj, vs + 1);
                v = obj.substring(vs + 1, ve);
            } else {
                ve = vs;
                while (ve < obj.length() && ",}".indexOf(obj.charAt(ve)) < 0) ve++;
                v = obj.substring(vs, ve).trim();
            }
            map.put(k, v);
            i = ve + 1;
        }
        return map;
    }
    private static int findStringEnd(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '"') return i;
        }
        return s.length();
    }
    private static java.util.List<String> splitTopLevel(String body, char open, char close) {
        java.util.List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == open) { if (depth == 0) start = i + 1; depth++; }
            else if (c == close) { depth--; if (depth == 0 && start >= 0) { out.add(body.substring(start, i)); start = -1; } }
        }
        return out;
    }
}
