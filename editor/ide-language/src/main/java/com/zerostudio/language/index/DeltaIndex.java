package com.zerostudio.language.index;

import com.zerostudio.language.model.ParsedFile;

import java.util.HashSet;
import java.util.Set;

/**
 * Result of applying a series of file changes to a {@link ProjectIndex}.
 *
 * <p>Used by long-running indexing jobs to commit a batch atomically.
 */
public final class DeltaIndex {
    public final Set<String> added = new HashSet<>();
    public final Set<String> updated = new HashSet<>();
    public final Set<String> removed = new HashSet<>();

    public boolean isEmpty() {
        return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
    }

    /** Apply a single parsed-file update; classifies it as add or update. */
    public void recordUpdate(ParsedFile parsed) {
        if (removed.remove(parsed.path)) {
            updated.add(parsed.path);
        } else if (updated.contains(parsed.path)) {
            // already an update in this batch
        } else {
            added.add(parsed.path);
        }
    }

    public void recordRemoval(String path) {
        added.remove(path);
        updated.remove(path);
        removed.add(path);
    }
}
