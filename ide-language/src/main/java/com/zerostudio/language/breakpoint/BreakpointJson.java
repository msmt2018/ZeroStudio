package com.zerostudio.language.breakpoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 断点持久化：以 JSON-like 字符串序列化/反序列化断点。
 * 格式：
 *   {"breakpoints":[
 *      {"id":"bp1","file":"X.java","line":10,"kind":"LINE","enabled":true},
 *      {"id":"bp2","file":"Y.java","line":20,"kind":"CONDITIONAL","condition":"x>0","enabled":true}
 *   ]}
 * 这是一个极简实现 — 不引入 JSON 依赖。生产中可换成 Gson/Moshi/kotlinx.serialization。
 */
public final class BreakpointJson {

    private BreakpointJson() {}

    public static String serialize(Iterable<Breakpoint> bps) {
        StringBuilder sb = new StringBuilder("{\"breakpoints\":[");
        boolean first = true;
        for (Breakpoint bp : bps) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            kv(sb, "id", bp.id, true); sb.append(',');
            kv(sb, "file", bp.sourceFile, true); sb.append(',');
            kvInt(sb, "line", bp.line); sb.append(',');
            kv(sb, "kind", bp.kind.name(), true); sb.append(',');
            kvBool(sb, "enabled", bp.enabled);
            if (bp.kind == Breakpoint.Kind.CONDITIONAL) { sb.append(','); kv(sb, "condition", bp.condition, true); }
            if (bp.kind == Breakpoint.Kind.LOGPOINT)    { sb.append(','); kv(sb, "logMessage", bp.logMessage, true); }
            if (bp.kind == Breakpoint.Kind.EXCEPTION)   { sb.append(','); kv(sb, "exceptionType", bp.exceptionType, true); }
            if (bp.hitThreshold > 0) { sb.append(','); kvInt(sb, "hitThreshold", bp.hitThreshold); }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static List<Breakpoint> deserialize(String json) {
        List<Breakpoint> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        int idx = json.indexOf('[');
        if (idx < 0) return out;
        int end = json.lastIndexOf(']');
        if (end <= idx) return out;
        String body = json.substring(idx + 1, end);
        for (String obj : splitTopLevel(body, '{', '}')) {
            if (obj.isEmpty()) continue;
            java.util.Map<String, String> map = parseObject(obj);
            Breakpoint.Builder b = Breakpoint.builder()
                    .id(map.get("id"))
                    .sourceFile(map.get("file"))
                    .line(parseInt(map.get("line"), 0))
                    .enabled(parseBool(map.get("enabled"), true));
            String kind = map.get("kind");
            if ("CONDITIONAL".equals(kind)) b.condition(map.get("condition"));
            else if ("LOGPOINT".equals(kind)) b.logMessage(map.get("logMessage"));
            else if ("EXCEPTION".equals(kind)) b.exceptionType(map.get("exceptionType"));
            if (map.containsKey("hitThreshold")) b.hitThreshold(parseInt(map.get("hitThreshold"), 0));
            out.add(b.build());
        }
        return out;
    }

    private static void kv(StringBuilder sb, String k, String v, boolean str) {
        sb.append('"').append(k).append("\":");
        if (v == null) sb.append("null");
        else if (str) sb.append('"').append(escape(v)).append('"');
        else sb.append(v);
    }
    private static void kvInt(StringBuilder sb, String k, int v) { sb.append('"').append(k).append("\":").append(v); }
    private static void kvBool(StringBuilder sb, String k, boolean v) { sb.append('"').append(k).append("\":").append(v); }
    private static String escape(String s) {
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
                v = unescape(obj.substring(vs + 1, ve));
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
    private static String unescape(String s) {
        return s.replace("\\\\", "\u0001").replace("\\n", "\n").replace("\"", "\"")
                .replace("\u0001", "\\");
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        return Boolean.parseBoolean(s.trim());
    }
    private static java.util.List<String> splitTopLevel(String body, char open, char close) {
        java.util.List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == open) {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(body.substring(start, i));
                    start = -1;
                }
            }
        }
        return out;
    }
}
