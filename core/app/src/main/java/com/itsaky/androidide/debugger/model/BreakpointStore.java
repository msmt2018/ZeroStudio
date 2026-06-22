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
import android.util.Log;
import androidx.annotation.NonNull;
import com.itsaky.androidide.app.BaseApplication;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class BreakpointStore {

    private static final String TAG = "BreakpointStore";
    private static final String FILE_NAME = "debugger/breakpoints.json";

    private static BreakpointStore INSTANCE;
    public static BreakpointStore getInstance() {
        if (INSTANCE == null) INSTANCE = new BreakpointStore();
        return INSTANCE;
    }

    private final Object lock = new Object();
    private boolean loaded = false;

    private BreakpointStore() {}

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
                    IdeBreakpoint.State state = IdeBreakpoint.State.NORMAL;
                    try {
                        state = IdeBreakpoint.State.valueOf(o.optString("state", "NORMAL"));
                    } catch (IllegalArgumentException ignored) {}
                    IdeBreakpoint bp = new IdeBreakpoint(
                            f, line, cond, state, -1L, o.optInt("hitCount", 0));
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
                    o.put("state", bp.state.name());
                    o.put("hitCount", bp.hitCount);
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
