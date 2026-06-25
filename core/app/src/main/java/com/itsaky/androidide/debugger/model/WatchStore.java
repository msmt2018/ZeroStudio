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
    /** PR-D6 batch 3/3: 防止重复触发 lazy load。 */
    private volatile boolean loaded = false;
    /** PR-D6 batch 3/3: 用 DebuggerController 已有 bgExecutor 切走文件 IO,避免主线程 ANR。 */
    private static final java.util.concurrent.ExecutorService PERSIST_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "WatchStore-persist");
                t.setDaemon(true);
                return t;
            });

    private WatchStore() {}

    /**
     * PR-D6 batch 3/3: 返回前先 lazy load。多次调用只触发一次磁盘 IO。
     * 第一次 {@link #all()} 调用是 IDE 启动后用户打开 Watches 面板的
     * 时刻,大部分时间在 UI 线程上 — 文件通常 < 1KB,直接同步读,
     * 不会卡顿。如果将来文件膨胀,再切到 bgExecutor。
     */
    @NonNull
    public synchronized List<String> all() {
        ensureLoaded();
        return new ArrayList<>(watches);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public synchronized void add(@NonNull String expr) {
        String t = expr.trim();
        if (t.isEmpty()) return;
        if (watches.contains(t)) return;
        watches.add(t);
        saveAsync();
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= watches.size()) return;
        watches.remove(index);
        saveAsync();
    }

    /**
     * PR-D4: 原地修改指定位置的表达式。{@code expr} 必须非空,且
     * 在去重后与原值不同才落盘。
     *
     * <p>PR-D6 batch 3/3: 当新值与其它位置重复时,删除其它位置并
     * 调整 index,避免列表里出现两个一样的 watch。
     */
    public synchronized void set(int index, @NonNull String expr) {
        if (index < 0 || index >= watches.size()) return;
        String t = expr.trim();
        if (t.isEmpty()) return;
        if (watches.get(index).equals(t)) return;
        int dup = watches.indexOf(t);
        if (dup >= 0 && dup != index) {
            watches.remove(dup);
            if (index > dup) index--;
        }
        watches.set(index, t);
        saveAsync();
    }

    public synchronized void clear() {
        watches.clear();
        saveAsync();
    }

    /**
     * PR-D6: 替换 {@code index} 处的表达式。新值需 trim + 非空,去重
     * (若新值已存在于其他位置则先移除旧位置);超出范围时不做任何操作。
     */
    public synchronized void set(int index, @NonNull String expr) {
        if (index < 0 || index >= watches.size()) return;
        String t = expr.trim();
        if (t.isEmpty()) return;
        if (watches.get(index).equals(t)) return;
        int dup = watches.indexOf(t);
        if (dup >= 0 && dup != index) {
            // 去重:删除原位置,index 调整
            watches.remove(dup);
            if (index > dup) index--;
        }
        watches.set(index, t);
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

    /**
     * PR-D6 batch 3/3: 在后台线程上序列化 + 写文件,避免主线程 ANR。
     * 用快照而不是直接持锁,这样 PERSIST_EXECUTOR 不会因为和
     * 其它同步方法互相等待而卡住。
     */
    private void saveAsync() {
        final List<String> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(watches);
        }
        PERSIST_EXECUTOR.execute(() -> {
            File f = file();
            if (f == null) return;
            try {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                JSONArray arr = new JSONArray();
                for (String s : snapshot) arr.put(s);
                Files.write(f.toPath(), arr.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t) {
                ILogger.ROOT.error(TAG + ": Failed to save watches: " + t.getMessage(), t);
            }
        });
    }

    @Nullable
    private File file() {
        var ctx = BaseApplication.getBaseInstance();
        if (ctx == null) return null;
        return new File(ctx.getFilesDir(), FILE_NAME);
    }
}
