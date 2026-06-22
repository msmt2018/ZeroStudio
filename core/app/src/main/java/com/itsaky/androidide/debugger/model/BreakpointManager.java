/*
 *  ZeroStudio IDE - 全局断点管理器
 *
 *  单一来源（single source of truth）：
 *  1. 编辑器 gutter 中的 6 状态图标
 *  2. 底部抽屉 “断点” 列表
 *  3. 调试 Action 菜单中的 “禁用/启用全部”
 *  三处都通过本管理器进行增删改查并收到通知。
 *
 *  PR-3：与 ide-debugger (com.zerostudio.debugger.api.Debugger) 通过
 *  installOnDebugger / uninstallFromDebugger 桥接。当 ide-debugger 报告
 *  breakpoint 状态变化时，调用 markVerified / markInvalid 同步回 UI 模型。
 *
 *  本类是线程安全的，所有变更都会在主线程派发。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsaky.androidide.utils.ILogger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单例：所有打开/未打开文件中的断点都登记在这里。
 */
public final class BreakpointManager {

    private static final String TAG = "BreakpointManager";
    private static final BreakpointManager INSTANCE = new BreakpointManager();

    public static BreakpointManager getInstance() {
        return INSTANCE;
    }

    /** 监听器接口 - UI 模块注册进来。 */
    public interface Listener {
        /** 断点列表发生任意变化（增/删/状态）。 */
        default void onBreakpointsChanged(@NonNull List<IdeBreakpoint> all) {}
        /** 单个断点状态变化。 */
        default void onBreakpointStateChanged(@NonNull IdeBreakpoint bp) {}
    }

    // file -> (line -> breakpoint)
    private final Map<String, Map<Integer, IdeBreakpoint>> byFile = new HashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    @Nullable private com.zerostudio.debugger.api.Debugger debugger;
    private boolean autoPersist = true;

    private BreakpointManager() {}

    public void addListener(@NonNull Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(@NonNull Listener l) { listeners.remove(l); }

    /** 关联到 ide-debugger 调试器（已连接到目标 App 之后）。 */
    public void bindDebugger(@Nullable com.zerostudio.debugger.api.Debugger dbg) {
        this.debugger = dbg;
    }

    @Nullable
    public com.zerostudio.debugger.api.Debugger debugger() {
        return debugger;
    }

    /**
     * 切换某行断点：已存在则删除，不存在则添加。
     * @return 添加或删除后的断点；null 表示操作失败（例如行号非法）。
     */
    @MainThread
    @Nullable
    public IdeBreakpoint toggle(@NonNull String file, int line) {
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.get(file);
            if (map != null && map.containsKey(line)) {
                IdeBreakpoint removed = map.remove(line);
                if (map.isEmpty()) byFile.remove(file);
                uninstallFromDebugger(removed);
                fireChanged();
                return null;
            } else {
                IdeBreakpoint bp = new IdeBreakpoint(file, line);
                if (map == null) {
                    map = new LinkedHashMap<>();
                    byFile.put(file, map);
                }
                map.put(line, bp);
                installOnDebugger(bp);
                fireChanged();
                return bp;
            }
        }
    }

    @MainThread
    public void add(@NonNull IdeBreakpoint bp) {
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.get(bp.file);
            if (map == null) {
                map = new LinkedHashMap<>();
                byFile.put(bp.file, map);
            }
            map.put(bp.line, bp);
        }
        installOnDebugger(bp);
        fireChanged();
    }

    @MainThread
    public void remove(@NonNull String id) {
        IdeBreakpoint target = findById(id);
        if (target == null) return;
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.get(target.file);
            if (map != null) {
                map.remove(target.line);
                if (map.isEmpty()) byFile.remove(target.file);
            }
        }
        uninstallFromDebugger(target);
        fireChanged();
    }

    @MainThread
    public void removeAllForFile(@NonNull String file) {
        List<IdeBreakpoint> removed = new ArrayList<>();
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.remove(file);
            if (map != null) removed.addAll(map.values());
        }
        for (IdeBreakpoint bp : removed) uninstallFromDebugger(bp);
        if (!removed.isEmpty()) fireChanged();
    }

    @MainThread
    public void clear() {
        List<IdeBreakpoint> all = snapshot();
        synchronized (byFile) { byFile.clear(); }
        for (IdeBreakpoint bp : all) uninstallFromDebugger(bp);
        fireChanged();
    }

    @MainThread
    public void setCondition(@NonNull String id, @Nullable String condition) {
        IdeBreakpoint bp = findById(id);
        if (bp == null) return;
        bp.setCondition(condition);
        // 条件变化需要让 ide-debugger 重新安装该断点
        reinstallOnDebugger(bp);
        fireStateChanged(bp);
    }

    @MainThread
    public void setEnabled(@NonNull String id, boolean enabled) {
        IdeBreakpoint bp = findById(id);
        if (bp == null) return;
        bp.state = enabled
                ? (bp.condition != null && !bp.condition.isEmpty()
                        ? IdeBreakpoint.State.CONDITION
                        : IdeBreakpoint.State.NORMAL)
                : IdeBreakpoint.State.DISABLED;
        if (enabled) {
            installOnDebugger(bp);
        } else {
            uninstallFromDebugger(bp);
        }
        fireStateChanged(bp);
    }

    @MainThread
    public void disableAll() {
        List<IdeBreakpoint> all = snapshot();
        for (IdeBreakpoint bp : all) {
            if (bp.state != IdeBreakpoint.State.DISABLED) {
                bp.state = IdeBreakpoint.State.DISABLED;
                uninstallFromDebugger(bp);
            }
        }
        fireChanged();
    }

    @MainThread
    public void enableAll() {
        List<IdeBreakpoint> all = snapshot();
        for (IdeBreakpoint bp : all) {
            if (bp.state == IdeBreakpoint.State.DISABLED) {
                bp.state = IdeBreakpoint.State.NORMAL;
                installOnDebugger(bp);
            }
        }
        fireChanged();
    }

    @NonNull
    public List<IdeBreakpoint> forFile(@NonNull String file) {
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.get(file);
            if (map == null) return Collections.emptyList();
            return new ArrayList<>(map.values());
        }
    }

    @Nullable
    public IdeBreakpoint findAt(@NonNull String file, int line) {
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> map = byFile.get(file);
            if (map == null) return null;
            return map.get(line);
        }
    }

    @NonNull
    public synchronized List<IdeBreakpoint> snapshot() {
        List<IdeBreakpoint> out = new ArrayList<>();
        synchronized (byFile) {
            for (Map<Integer, IdeBreakpoint> map : byFile.values()) {
                out.addAll(map.values());
            }
        }
        return out;
    }

    /** 仅保留真实文件路径（不是新建/临时文件） */
    public boolean hasAnyIn(@NonNull String file) {
        synchronized (byFile) {
            Map<Integer, IdeBreakpoint> m = byFile.get(file);
            return m != null && !m.isEmpty();
        }
    }

    @Nullable
    public IdeBreakpoint findById(@NonNull String id) {
        synchronized (byFile) {
            for (Map<Integer, IdeBreakpoint> map : byFile.values()) {
                for (IdeBreakpoint bp : map.values()) {
                    if (id.equals(bp.id)) return bp;
                }
            }
        }
        return null;
    }

    @Nullable
    public IdeBreakpoint findByDebuggerId(long debuggerBpId) {
        synchronized (byFile) {
            for (Map<Integer, IdeBreakpoint> map : byFile.values()) {
                for (IdeBreakpoint bp : map.values()) {
                    if (bp.debuggerBpId == debuggerBpId) return bp;
                }
            }
        }
        return null;
    }

    // ---- 状态同步 (from ide-debugger) ----

    /** ide-debugger 报告该断点已验证 (server returned a valid location)。 */
    public void markVerified(@NonNull IdeBreakpoint bp) {
        if (bp.state == IdeBreakpoint.State.VERIFIED) return;
        bp.state = (bp.condition != null && !bp.condition.isEmpty())
                ? IdeBreakpoint.State.CONDITION
                : IdeBreakpoint.State.VERIFIED;
        fireStateChanged(bp);
    }

    /** ide-debugger 报告该断点位置无法解析。 */
    public void markInvalid(@NonNull IdeBreakpoint bp) {
        if (bp.state == IdeBreakpoint.State.INVALID) return;
        bp.state = IdeBreakpoint.State.INVALID;
        fireStateChanged(bp);
    }

    /** ide-debugger 报告该断点被命中。 */
    public void markHit(@NonNull IdeBreakpoint bp) {
        bp.hitCount++;
        bp.state = IdeBreakpoint.State.HIT;
        fireStateChanged(bp);
    }

    // ---- JDWP 桥接 ----

    private void installOnDebugger(@NonNull IdeBreakpoint bp) {
        if (debugger == null) return;
        if (!bp.isActive()) return;
        try {
            String cond = (bp.state == IdeBreakpoint.State.CONDITION) ? bp.condition : null;
            long id = debugger.addBreakpoint(bp.file, bp.line, cond);
            bp.debuggerBpId = id;
        } catch (Throwable t) {
            ILogger.debug(TAG, "installOnDebugger failed: " + t.getMessage());
        }
    }

    private void reinstallOnDebugger(@NonNull IdeBreakpoint bp) {
        if (debugger == null) return;
        try {
            if (bp.debuggerBpId > 0) debugger.removeBreakpoint(bp.debuggerBpId);
        } catch (Throwable ignored) {}
        bp.debuggerBpId = -1L;
        installOnDebugger(bp);
    }

    private void uninstallFromDebugger(@NonNull IdeBreakpoint bp) {
        if (debugger == null) return;
        if (bp.debuggerBpId <= 0) return;
        try {
            debugger.removeBreakpoint(bp.debuggerBpId);
        } catch (Throwable t) {
            ILogger.debug(TAG, "uninstallFromDebugger failed: " + t.getMessage());
        }
        bp.debuggerBpId = -1L;
    }

    private void fireChanged() {
        List<IdeBreakpoint> snap = snapshot();
        for (Listener l : listeners) {
            try { l.onBreakpointsChanged(snap); } catch (Throwable ignored) {}
        }
        if (autoPersist) BreakpointStore.getInstance().save();
    }

    private void fireStateChanged(@NonNull IdeBreakpoint bp) {
        for (Listener l : listeners) {
            try { l.onBreakpointStateChanged(bp); } catch (Throwable ignored) {}
        }
        if (autoPersist) BreakpointStore.getInstance().save();
    }

    /** 标准化文件路径（用于 byFile 键） */
    @NonNull
    public static String normalize(@NonNull File f) {
        try { return f.getCanonicalPath(); } catch (Throwable ignored) { return f.getAbsolutePath(); }
    }

    @NonNull
    public static String normalize(@NonNull String path) {
        return normalize(new File(path));
    }
}
