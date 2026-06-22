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
    private final Map<String, Long> byLocation = new HashMap<>();
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

    /** Remove all one-shot breakpoints when the target suspends. */
    public void removeOneShots(@NonNull SuspendInfo info) {
        if (oneShot.isEmpty()) return;
        // For now, remove every one-shot breakpoint that has been verified.
        // A more sophisticated implementation would match the current
        // source location; the simple version is good enough for the IDE.
        for (Long id : new ArrayList<>(oneShot)) {
            Breakpoint bp = byId.get(id);
            if (bp != null && bp.requestId > 0) {
                // The source locator will clear the JDWP request.
                byId.remove(id);
                byLocation.remove(key(bp.sourceFile, bp.line));
            }
        }
        oneShot.clear();
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
