package com.zerostudio.language.index;

import com.zerostudio.language.model.ParsedFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IndexAdapter {

    private final ProjectIndex delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String name;
    private final String path;

    public IndexAdapter(String name, String path) {
        this.name = name;
        this.path = path;
        this.delegate = new ProjectIndex();
    }

    public String getName() { return name; }
    public String getPath() { return path; }

    public void index(ParsedFile file) {
        delegate.index(file);
    }

    public void indexAsync(ParsedFile file) {
        executor.submit(() -> delegate.index(file));
    }

    public void indexAll(Collection<ParsedFile> files) {
        for (ParsedFile f : files) delegate.index(f);
    }

    public void indexAllAsync(Collection<ParsedFile> files) {
        executor.submit(() -> indexAll(files));
    }

    public void delete() {
        delegate.clear();
    }

    public ProjectIndex getDelegate() { return delegate; }

    public void shutdown() {
        executor.shutdown();
    }

    public List<ParsedFile> allFiles() {
        List<ParsedFile> files = new ArrayList<>();
        for (java.util.Map.Entry<String, ParsedFile> e : delegate.allFiles()) {
            files.add(e.getValue());
        }
        return files;
    }

    public ParsedFile fileFor(String className) { return delegate.fileFor(className); }
    public ParsedFile fileForPath(String path) { return delegate.fileForPath(path); }
    public List<String> fuzzySearch(String query, int max) { return delegate.fuzzySearch(query, max); }
    public List<String> allClasses() { return delegate.allClasses(); }
}
