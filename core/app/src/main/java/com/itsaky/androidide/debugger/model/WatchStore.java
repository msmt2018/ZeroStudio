/*
 *  ZeroStudio IDE - 监视表达式持久化
 *
 *  PR-4: 用户在「监视」Tab 中添加的表达式会保存到 files-dir/debugger/watches.json。
 *  与 BreakpointStore 模式相同，使用 BaseApplication.getBaseInstance() 拿 Context。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsaky.androidide.app.BaseApplication;
import com.itsaky.androidide.utils.ILogger;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public final class WatchStore {

    private static final String TAG = "WatchStore";
    private static final String FILE_NAME = "debugger/watches.json";
    private static final WatchStore INSTANCE = new WatchStore();

    public static WatchStore getInstance() { return INSTANCE; }

    @NonNull private final List<String> watches = new ArrayList<>();

    private WatchStore() {}

    @NonNull
    public synchronized List<String> all() {
        return new ArrayList<>(watches);
    }

    public synchronized void add(@NonNull String expr) {
        String t = expr.trim();
        if (t.isEmpty()) return;
        if (watches.contains(t)) return;
        watches.add(t);
        save();
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= watches.size()) return;
        watches.remove(index);
        save();
    }

    /**
     * PR-D4: 原地修改指定位置的表达式。{@code expr} 必须非空,且
     * 在去重后与原值不同才落盘。
     */
    public synchronized void set(int index, @NonNull String expr) {
        if (index < 0 || index >= watches.size()) return;
        String t = expr.trim();
        if (t.isEmpty()) return;
        String prev = watches.get(index);
        if (prev.equals(t)) return;
        // 去重:如果新表达式已存在,直接移除旧位置(避免重复显示)。
        int existing = watches.indexOf(t);
        if (existing >= 0 && existing != index) {
            watches.remove(index);
        } else {
            watches.set(index, t);
        }
        save();
    }

    public synchronized void clear() {
        watches.clear();
        save();
    }

    public synchronized void load() {
        watches.clear();
        File f = file();
        if (f == null || !f.exists()) return;
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                watches.add(arr.getString(i));
            }
            ILogger.ROOT.info(TAG + ": Loaded " + watches.size() + " watches");
        } catch (Throwable t) {
            ILogger.ROOT.error(TAG + ": Failed to load watches: " + t.getMessage(), t);
        }
    }

    public synchronized void save() {
        File f = file();
        if (f == null) return;
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            JSONArray arr = new JSONArray();
            for (String s : watches) arr.put(s);
            Files.write(f.toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            ILogger.ROOT.error(TAG + ": Failed to save watches: " + t.getMessage(), t);
        }
    }

    @Nullable
    private File file() {
        var ctx = BaseApplication.getBaseInstance();
        if (ctx == null) return null;
        return new File(ctx.getFilesDir(), FILE_NAME);
    }
}
