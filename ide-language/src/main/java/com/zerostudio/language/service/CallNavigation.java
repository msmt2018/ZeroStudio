package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import java.util.*;

public final class CallNavigation {
    public enum Direction { INTO, OUT }

    private final LanguageService language;
    private final ArrayDeque<SourcePosition> stack = new ArrayDeque<>();

    public CallNavigation(LanguageService language) { this.language = language; }

    public void push(SourcePosition pos) { stack.push(pos); }

    public Optional<SourcePosition> step(Direction dir) {
        if (dir == Direction.OUT) return Optional.ofNullable(stack.pollFirst());
        return Optional.empty(); // step-into requires runtime info
    }

    public void clear() { stack.clear(); }
}