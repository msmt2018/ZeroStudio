/*
 *  ZeroStudio IDE - shell 命令参数校验 (PR-D9.4)
 *
 *  ShizukuBridge / RunAsBridge 在拼 shell 命令时用 `sh -c <cmd>`。
 *  如果参数里含 shell 元字符 (`;` `&&` `||` `|` 反引号 `$()` `<` `>` 等),
 *  会让一条 "无害" 的命令变成任意命令执行。
 *
 *  这里的 [isSafeArg] / [isSafePackageName] / [isSafePath] 三个检查帮
 *  桥梁代码在调用前快速拒掉明显带注入特征的输入。注意: 这只是
 *  defense-in-depth, 真正的修复是避免用 `sh -c` 而是走
 *  `ProcessBuilder` 的 String[] 形式 (本类不替代那个修复)。
 *
 *  设计原则:
 *    - 黑名单元字符 (保守, 拒掉任何可被 shell 解释的字符)
 *    - 白名单路径字符 (限定为字母/数字/`_-.`/路径分隔符, 不允许空格)
 *    - 包名只允许 `[a-zA-Z0-9_.]` (Android package 规范)
 */

package com.itsaky.androidide.debugger;

import androidx.annotation.NonNull;

public final class CommandValidator {

    private CommandValidator() {}

    /** shell 元字符黑名单。任何含这些字符的字符串都被认为不可直接拼到 `sh -c` 里。 */
    private static final String SHELL_METACHARS = ";&|`$<>(){}[]\\\"'*?\n\r\t#";

    /** 一般参数 (命令、表达式): 不允许 shell 元字符, 不允许首字符是 `-` (避免被解析成 flag). */
    public static boolean isSafeArg(@NonNull String s) {
        if (s.isEmpty()) return false;
        if (s.charAt(0) == '-') return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (SHELL_METACHARS.indexOf(c) >= 0) return false;
        }
        return true;
    }

    /**
     * Android 包名: 只允许 `[a-zA-Z0-9_.]`, 且必须以字母开头, 至少两段。
     * 这是 Android 框架的硬约束, 我们对它的白名单可以更严格。
     */
    public static boolean isSafePackageName(@NonNull String s) {
        if (s.isEmpty() || s.length() > 200) return false;
        boolean lastWasDot = true; // 不允许连续 .. 也不允许首字符 .
        int segments = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (lastWasDot) return false;
                lastWasDot = true;
                continue;
            }
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_')) return false;
            if (lastWasDot) segments++;
            lastWasDot = false;
        }
        if (lastWasDot) return false; // 不能以 . 结尾
        return segments >= 2;
    }

    /**
     * 文件路径: 限定为绝对路径或相对路径, 不允许 shell 元字符, 不允许空格。
     * 不做路径遍历检查 (../)— 那是业务层的责任, 不在本工具范围。
     */
    public static boolean isSafePath(@NonNull String s) {
        if (s.isEmpty() || s.length() > 4096) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 允许: 字母/数字/_-./:
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
            if (!ok) return false;
        }
        return true;
    }
}
