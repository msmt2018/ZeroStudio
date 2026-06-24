/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  The in-memory store of all breakpoints the user has set. Maps ids to
 *  breakpoints and keeps a quick lookup of (sourceFile, line) -> id.
 */

package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.api.Breakpoint;
import com.zerostudio.debugger.api.SuspendInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakpointStore {

    private final ConcurrentHashMap<Long, Breakpoint> byId = new ConcurrentHashMap<>();
    // PR-D6: 改 ConcurrentHashMap。JDWP 事件线程会在收到 EventRequest.SET 回调
    // 时调 add/remove;UI 线程调 get/findByLocation。
    private final Map<String, Long> byLocation = new ConcurrentHashMap<>();
    private final Set<Long> oneShot = ConcurrentHashMap.newKeySet();

    public void add(@NonNull Breakpoint bp) {
        byId.put(bp.id, bp);
        byLocation.put(key(bp.sourceFile, bp.line), bp.id);
    }

    public void remove(long id) {
        Breakpoint bp = byId.remove(id);
        if (bp != null) {
            byLocation.remove(key(bp.sourceFile, bp.line));
        }
        oneShot.remove(id);
    }

    @Nullable
    public Breakpoint get(long id) {
        return byId.get(id);
    }

    @Nullable
    public Breakpoint findByLocation(@NonNull String sourceFile, int line) {
        Long id = byLocation.get(key(sourceFile, line));
        return id == null ? null : byId.get(id);
    }

    @NonNull
    public Collection<Breakpoint> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public void setOneShot(long id, boolean value) {
        if (value) oneShot.add(id);
        else oneShot.remove(id);
    }

    public boolean isOneShot(long id) {
        return oneShot.contains(id);
    }

    /**
     * 移除所有 one-shot 断点(在程序挂起时调用)。返回被移除的 BP id 列表,
     * 供 {@link com.zerostudio.debugger.SourceLocator} 同步清理 JDWP 端
     * 的 EventRequest(本类不依赖 SourceLocator,职责分层清晰)。
     */
    @NonNull
    public List<Long> removeOneShots(@NonNull SuspendInfo info) {
        if (oneShot.isEmpty()) return Collections.emptyList();
        List<Long> removed = new ArrayList<>();
        // 先复制 keySet 避免 ConcurrentModificationException
        for (Long id : new ArrayList<>(oneShot)) {
            Breakpoint bp = byId.get(id);
            if (bp != null && bp.requestId > 0) {
                byId.remove(id);
                byLocation.remove(key(bp.sourceFile, bp.line));
                removed.add(id);
            } else if (bp != null) {
                // requestId==0 意味着还没在 VM 端注册,直接删本地即可
                byId.remove(id);
                byLocation.remove(key(bp.sourceFile, bp.line));
                removed.add(id);
            }
        }
        oneShot.clear();
        return removed;
    }

    public void clear() {
        byId.clear();
        byLocation.clear();
        oneShot.clear();
    }

    @NonNull
    public List<Breakpoint> snapshot() {
        return new ArrayList<>(byId.values());
    }

    private static String key(@NonNull String file, int line) {
        return file + ":" + line;
    }
}
