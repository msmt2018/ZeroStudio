package com.zerostudio.language.service;

import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 调用栈导航：Step Into / Step Out 真实实现。
 * 用 FrameSnapshot 模拟 JDI StackFrame 列表，支持：
 *  - 前进到下一帧（Step Over）
 *  - 进入被调方法（Step Into — 由 StepFilter 控制是否跳过）
 *  - 退出当前方法（Step Out — 弹出栈）
 */
public final class CallNavigation {

    public enum Direction { INTO, OVER, OUT }

    /** 决定 Step Into 是否进入某个 frame 的过滤器 */
    public interface StepFilter {
        boolean shouldStepInto(StackFrame frame);
    }

    /** 默认过滤器：跳过 synthetic 帧与 simple getter */
    public static final StepFilter DEFAULT_FILTER = new StepFilter() {
        @Override
        public boolean shouldStepInto(StackFrame frame) {
            if (frame.methodName.startsWith("access$") || frame.methodName.contains("$")) return false;
            if (frame.methodName.startsWith("get") && frame.methodName.length() > 3
                    && Character.isUpperCase(frame.methodName.charAt(3))) return false;
            return true;
        }
    };

    private final Deque<StackFrame> callStack = new ArrayDeque<>();
    private StepFilter filter = DEFAULT_FILTER;
    private int currentIndex = 0;

    public void setFilter(StepFilter f) { this.filter = f != null ? f : DEFAULT_FILTER; }
    public void reset() { callStack.clear(); currentIndex = 0; }

    public void loadFrom(FrameSnapshot snapshot) {
        reset();
        if (snapshot == null) return;
        // frames()[0] = top frame (per FrameSnapshot.topFrame convention)
        // addLast preserves that order so callStack.peek() returns the top.
        for (StackFrame f : snapshot.frames()) callStack.addLast(f);
        currentIndex = 0;
    }

    public Optional<SourcePosition> step(Direction dir) {
        if (callStack.isEmpty()) return Optional.empty();
        switch (dir) {
            case OVER: {
                // 移动到栈中下一个位置（向调用者方向）
                int nextIdx = currentIndex + 1;
                if (nextIdx >= callStack.size()) return Optional.empty();
                StackFrame next = null;
                int i = 0;
                for (StackFrame f : callStack) {
                    if (i++ == nextIdx) { next = f; break; }
                }
                if (next == null) return Optional.empty();
                if (!filter.shouldStepInto(next)) return Optional.empty();
                currentIndex = nextIdx;
                return Optional.of(toPosition(next));
            }
            case INTO: {
                // 查找第一个通过 filter 的 frame（自栈顶向下）
                int idx = 0;
                for (StackFrame f : callStack) {
                    if (filter.shouldStepInto(f)) {
                        currentIndex = idx;
                        return Optional.of(toPosition(f));
                    }
                    idx++;
                }
                return Optional.empty();
            }
            case OUT: {
                // 弹出当前帧（末尾的），返回当前 top
                callStack.pollLast();
                StackFrame top = callStack.peek();
                if (top == null) return Optional.empty();
                return Optional.of(toPosition(top));
            }
        }
        return Optional.empty();
    }

    public Optional<SourcePosition> currentPosition() {
        if (callStack.isEmpty()) return Optional.empty();
        StackFrame f = callStack.stream().skip(currentIndex).findFirst().orElse(null);
        return f == null ? Optional.empty() : Optional.of(toPosition(f));
    }

    private SourcePosition toPosition(StackFrame f) {
        return new SourcePosition(f.sourcePath != null ? f.sourcePath : f.className,
                f.lineNumber, 1);
    }
}
