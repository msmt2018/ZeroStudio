/*
 *  ZeroStudio IDE - CommandValidator 单元测试 (PR-D9.4)
 *
 *  覆盖:
 *    - isSafeArg: 拒掉 shell 元字符、拒掉以 - 开头的字符串
 *    - isSafePackageName: 接受正常包名, 拒掉含元字符/空段/首字符非字母
 *    - isSafePath: 接受绝对/相对路径, 拒掉 shell 元字符/空格
 */

package com.itsaky.androidide.debugger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommandValidatorTest {

    @Test
    public void isSafeArg_acceptsNormal() {
        assertTrue(CommandValidator.isSafeArg("id"));
        assertTrue(CommandValidator.isSafeArg("id -u"));
        assertTrue(CommandValidator.isSafeArg("/system/bin/ls"));
    }

    @Test
    public void isSafeArg_rejectsEmpty() {
        assertFalse(CommandValidator.isSafeArg(""));
    }

    @Test
    public void isSafeArg_rejectsLeadingDash() {
        assertFalse(CommandValidator.isSafeArg("-rf"));
        assertFalse(CommandValidator.isSafeArg("--flag"));
    }

    @Test
    public void isSafeArg_rejectsShellMetachars() {
        assertFalse(CommandValidator.isSafeArg("a;b"));
        assertFalse(CommandValidator.isSafeArg("a&&b"));
        assertFalse(CommandValidator.isSafeArg("a||b"));
        assertFalse(CommandValidator.isSafeArg("a|b"));
        assertFalse(CommandValidator.isSafeArg("`whoami`"));
        assertFalse(CommandValidator.isSafeArg("$(whoami)"));
        assertFalse(CommandValidator.isSafeArg("a<b"));
        assertFalse(CommandValidator.isSafeArg("a>b"));
        assertFalse(CommandValidator.isSafeArg("a\nb"));
        assertFalse(CommandValidator.isSafeArg("a\tb"));
        assertFalse(CommandValidator.isSafeArg("a#b"));
        assertFalse(CommandValidator.isSafeArg("a\"b"));
    }

    @Test
    public void isSafePackageName_acceptsNormal() {
        assertTrue(CommandValidator.isSafePackageName("com.example"));
        assertTrue(CommandValidator.isSafePackageName("com.itsaky.androidide"));
        assertTrue(CommandValidator.isSafePackageName("a.b"));
        assertTrue(CommandValidator.isSafePackageName("com.example.app_v2"));
    }

    @Test
    public void isSafePackageName_rejectsEmpty() {
        assertFalse(CommandValidator.isSafePackageName(""));
    }

    @Test
    public void isSafePackageName_rejectsSingleSegment() {
        assertFalse(CommandValidator.isSafePackageName("com"));
        assertFalse(CommandValidator.isSafePackageName("a"));
    }

    @Test
    public void isSafePackageName_rejectsLeadingDot() {
        assertFalse(CommandValidator.isSafePackageName(".com.example"));
        assertFalse(CommandValidator.isSafePackageName("com..example"));
        assertFalse(CommandValidator.isSafePackageName("com.example."));
    }

    @Test
    public void isSafePackageName_rejectsShellMetachars() {
        assertFalse(CommandValidator.isSafePackageName("com.example;rm"));
        assertFalse(CommandValidator.isSafePackageName("com example"));
        assertFalse(CommandValidator.isSafePackageName("com.example`id`"));
        assertFalse(CommandValidator.isSafePackageName("com.example$(id)"));
    }

    @Test
    public void isSafePackageName_rejectsTooLong() {
        StringBuilder sb = new StringBuilder("com.");
        for (int i = 0; i < 300; i++) sb.append('a').append('.');
        assertFalse(CommandValidator.isSafePackageName(sb.toString()));
    }

    @Test
    public void isSafePath_acceptsNormal() {
        assertTrue(CommandValidator.isSafePath("/data/local/tmp/foo.apk"));
        assertTrue(CommandValidator.isSafePath("./relative"));
        assertTrue(CommandValidator.isSafePath("a/b/c.txt"));
        assertTrue(CommandValidator.isSafePath("/sdcard/Download/app-debug.apk"));
    }

    @Test
    public void isSafePath_rejectsEmpty() {
        assertFalse(CommandValidator.isSafePath(""));
    }

    @Test
    public void isSafePath_rejectsShellMetachars() {
        assertFalse(CommandValidator.isSafePath("/path;rm"));
        assertFalse(CommandValidator.isSafePath("/path with space"));
        assertFalse(CommandValidator.isSafePath("/path`id`"));
        assertFalse(CommandValidator.isSafePath("/path$(id)"));
        assertFalse(CommandValidator.isSafePath("/path|tee"));
        assertFalse(CommandValidator.isSafePath("/path&bg"));
    }
}
