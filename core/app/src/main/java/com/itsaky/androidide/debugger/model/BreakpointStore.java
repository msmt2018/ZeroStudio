/*
 *  ZeroStudio IDE - 断点持久化
 *
 *  把断点状态序列化到 IDE 数据目录，IDE 退出/启动时自动加载/保存。
 *  数据格式：JSON 数组，每个元素对应一个断点。
 *
 *  注意：file 路径使用绝对路径；启动时按 IDE 当前打开的 project 路径
 *  重新映射（如果有项目根）。
 */

package com.itsaky.androidide.debugger.model;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsaky.androidide.app.BaseApplication;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public final class BreakpointStore {

    private static final String TAG = "BreakpointStore";
    private static final String FILE_NAME = "debugger/breakpoints.json";

    /** PR-D7: 防抖窗口。300ms 内的多次 save() 合并成一次落盘。 */
    private static final long DEBOUNCE_MS = 300L;

    private static BreakpointStore INSTANCE;
    public static BreakpointStore getInstance() {
        if (INSTANCE == null) INSTANCE = new BreakpointStore();
        return INSTANCE;
    }

    private final Object lock = new Object();
    private boolean loaded = false;

    /** PR-D7: 防抖调度用的后台 HandlerThread。 */
    private final HandlerThread ioThread = new HandlerThread("BreakpointStore-IO");
    @NonNull private final Handler ioHandler;
    /** PR-D7: 当前的防抖 runnable;非空表示已有一次 save() 计划。 */
    @Nullable private Runnable pendingPersist;

    private BreakpointStore() {
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
    }

    /**
     * Load breakpoints from disk. Idempotent. Safe to call multiple times.
     */
    public void load() {
        synchronized (lock) {
            if (loaded) return;
            File file = getStoreFile();
            if (file == null || !file.exists()) {
                loaded = true;
                return;
            }
            try (FileReader r = new FileReader(file)) {
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = r.read()) != -1) sb.append((char) c);
                JSONArray arr = new JSONArray(sb.toString());
                BreakpointManager mgr = BreakpointManager.getInstance();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String f = o.optString("file", "");
                    int line = o.optInt("line", -1);
                    if (f.isEmpty() || line <= 0) continue;
                    String cond = o.optString("condition", null);
                    String log = o.optString("logMessage", null);
                    IdeBreakpoint.State state = IdeBreakpoint.State.NORMAL;
                    try {
                        state = IdeBreakpoint.State.valueOf(o.optString("state", "NORMAL"));
                    } catch (IllegalArgumentException ignored) {}
                    com.zerostudio.debugger.api.Breakpoint.HitCountMode mode =
                            com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS;
                    try {
                        mode = com.zerostudio.debugger.api.Breakpoint.HitCountMode
                                .valueOf(o.optString("hitCountMode", "ALWAYS"));
                    } catch (IllegalArgumentException ignored) {}
                    int hitCount = o.optInt("hitCount", 0);
                    IdeBreakpoint bp = new IdeBreakpoint(
                            UUID.randomUUID().toString(), f, line, cond, log,
                            mode, hitCount, state, -1L, 0);
                    applyExtendedFields(o, bp);
                    mgr.add(bp);
                }
                Log.i(TAG, "Loaded " + arr.length() + " breakpoints from " + file);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to load breakpoints: " + t.getMessage());
            } finally {
                loaded = true;
            }
        }
    }

    /**
     * Persist all breakpoints to disk. Called on every breakpoint change.
     */
    public void save() {
        synchronized (lock) {
            File file = getStoreFile();
            if (file == null) return;
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                List<IdeBreakpoint> all = BreakpointManager.getInstance().snapshot();
                JSONArray arr = new JSONArray();
                for (IdeBreakpoint bp : all) {
                    JSONObject o = new JSONObject();
                    o.put("file", bp.file);
                    o.put("line", bp.line);
                    if (bp.condition != null) o.put("condition", bp.condition);
                    if (bp.logMessage != null) o.put("logMessage", bp.logMessage);
                    o.put("state", bp.state.name());
                    writeExtendedFields(o, bp);
                    arr.put(o);
                }
                try (FileWriter w = new FileWriter(file)) {
                    w.write(arr.toString(2));
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to save breakpoints: " + t.getMessage());
            }
        }
    }

    /**
     * PR-D7: 防抖版的 persist —— 把多次连续 save() 合并为单次落盘。
     * 300ms 内再次调用会 reset 计时器。
     *
     * <p>同步取一次 snapshot 即可。Disk IO 异步在 BreakpointStore-IO 线程跑。
     */
    public void schedulePersist() {
        synchronized (lock) {
            if (pendingPersist != null) {
                ioHandler.removeCallbacks(pendingPersist);
            }
            // 先在调用线程上把 breakpoint snapshot 拉一份,
            // 避免后面落盘时再访问 BreakpointManager 时的并发问题。
            final List<IdeBreakpoint> snapshot;
            try {
                snapshot = BreakpointManager.getInstance().snapshot();
            } catch (Throwable t) {
                Log.w(TAG, "schedulePersist snapshot failed: " + t.getMessage());
                return;
            }
            final File file = getStoreFile();
            if (file == null) return;
            pendingPersist = () -> {
                synchronized (lock) {
                    pendingPersist = null;
                }
                writeToFile(file, snapshot);
            };
            ioHandler.postDelayed(pendingPersist, DEBOUNCE_MS);
        }
    }

    /**
     * PR-D7: 立即把当前所有断点落盘,并取消任何挂起的防抖。
     * 用于 IDE 退出 / Activity.onPause 等不能容忍延迟的场景。
     */
    public void flushNow() {
        synchronized (lock) {
            if (pendingPersist != null) {
                ioHandler.removeCallbacks(pendingPersist);
                pendingPersist = null;
            }
        }
        save();
    }

    /** PR-D7: 实际写文件的辅助方法,在 IO 线程调用。 */
    private void writeToFile(@NonNull File file, @NonNull List<IdeBreakpoint> all) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            JSONArray arr = new JSONArray();
            for (IdeBreakpoint bp : all) {
                JSONObject o = new JSONObject();
                o.put("file", bp.file);
                o.put("line", bp.line);
                if (bp.condition != null) o.put("condition", bp.condition);
                if (bp.logMessage != null) o.put("logMessage", bp.logMessage);
                o.put("state", bp.state.name());
                if (bp.hitCountMode
                        != com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS) {
                    o.put("hitCountMode", bp.hitCountMode.name());
                    o.put("hitCount", bp.hitCount);
                }
                arr.put(o);
            }
            try (FileWriter w = new FileWriter(file)) {
                w.write(arr.toString(2));
            }
        } catch (Throwable t) {
            Log.w(TAG, "writeToFile failed: " + t.getMessage());
        }
    }

    private static void applyExtendedFields(@NonNull JSONObject o, @NonNull IdeBreakpoint bp) {
        try { bp.kind = IdeBreakpoint.Kind.valueOf(o.optString("kind", "LINE")); }
        catch (IllegalArgumentException ignored) { bp.kind = IdeBreakpoint.Kind.LINE; }
        bp.temporary = o.optBoolean("temporary", false);
        bp.watchAccess = o.optBoolean("watchAccess", false);
        bp.watchModification = o.optBoolean("watchModification", true);
        bp.methodEntry = o.optBoolean("methodEntry", true);
        bp.methodExit = o.optBoolean("methodExit", false);
        bp.catchCaught = o.optBoolean("catchCaught", true);
        bp.catchUncaught = o.optBoolean("catchUncaught", true);
        bp.dependsOnBreakpointId = o.optString("dependsOnBreakpointId", null);
        bp.elementName = o.optString("elementName", null);
    }

    private static void writeExtendedFields(@NonNull JSONObject o, @NonNull IdeBreakpoint bp) throws Exception {
        if (bp.hitCountMode != com.zerostudio.debugger.api.Breakpoint.HitCountMode.ALWAYS) {
            o.put("hitCountMode", bp.hitCountMode.name());
            o.put("hitCount", bp.hitCount);
        }
        o.put("kind", bp.kind.name());
        if (bp.temporary) o.put("temporary", true);
        if (bp.watchAccess) o.put("watchAccess", true);
        if (!bp.watchModification) o.put("watchModification", false);
        if (!bp.methodEntry) o.put("methodEntry", false);
        if (bp.methodExit) o.put("methodExit", true);
        if (!bp.catchCaught) o.put("catchCaught", false);
        if (!bp.catchUncaught) o.put("catchUncaught", false);
        if (bp.dependsOnBreakpointId != null) o.put("dependsOnBreakpointId", bp.dependsOnBreakpointId);
        if (bp.elementName != null) o.put("elementName", bp.elementName);
    }

    @Nullable
    private File getStoreFile() {
        Context ctx;
        try {
            ctx = BaseApplication.getBaseInstance();
        } catch (Throwable t) {
            return null;
        }
        if (ctx == null) return null;
        File dataDir = ctx.getFilesDir();
        if (dataDir == null) return null;
        return new File(dataDir, FILE_NAME);
    }
}
