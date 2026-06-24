/*
 *  ZeroStudio IDE - 断点侧边栏 View
 *
 *  透明 View 覆盖在 CodeEditor 的 gutter 区域，
 *  绘制 6 种状态的断点图标。监听 CodeEditor 的滚动事件实时刷新。
 *
 *  6 种状态对应 6 个 drawable，通过选择器在不同的状态码下选
 *  用不同资源。当 drawable 缺失时退化为 Canvas 绘制。
 */

package com.itsaky.androidide.debugger.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.itsaky.androidide.debugger.BreakpointStateColors;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.util.List;

/**
 * 透明 View，覆盖在编辑器左侧 gutter 区域。
 *  监听 CodeEditor 的滚动 / 缩放 / 行号变化，绘制当前可见行的断点图标。
 *  点击触发 {@link OnBreakpointClickListener}。
 */
public class BreakpointSidebar extends View {

    public interface OnBreakpointClickListener {
        /** 短按：切换（添加/删除）该行断点。 */
        void onBreakpointClick(@NonNull String file, int line);
        /** 长按：弹出条件/禁用/删除菜单。 */
        void onBreakpointLongClick(@NonNull IdeBreakpoint bp);
    }

    private static final float GLYPH_RADIUS_DP = 5.0f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable private CodeEditor editor;
    @Nullable private String currentFile;
    @Nullable private OnBreakpointClickListener clickListener;
    @Nullable private GestureDetector gestureDetector;

    public BreakpointSidebar(Context context) {
        this(context, null);
    }

    public BreakpointSidebar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BreakpointSidebar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1.5f));
        outlinePaint.setColor(Color.WHITE);
        hitRingPaint.setStyle(Paint.Style.STROKE);
        hitRingPaint.setStrokeWidth(dp(2f));
        hitRingPaint.setColor(Color.WHITE);
        setWillNotDraw(false);
        // Phase E5: 自定义 view 的无障碍设置。
        // 开启 focusable 后屏幕阅读器可以聚焦,并通过自定义
        // AccessibilityDelegate 把"切换断点"暴露为可执行 action。
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(context.getString(
                com.itsaky.androidide.R.string.debugger_a11y_bp_long_press));
        // 自定义 a11y action: 命中 toggle 行为
        ViewCompat.setAccessibilityDelegate(this, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    @NonNull View host,
                    @NonNull androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClickable(true);
                info.setLongClickable(true);
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat toggle =
                        new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                                        .AccessibilityActionCompat.ACTION_CLICK.getId(),
                                host.getContext().getString(
                                        com.itsaky.androidide.R.string.debugger_action_toggle_bp));
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat longPress =
                        new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .AccessibilityAction.ACTION_LONG_CLICK.getId(),
                                host.getContext().getString(
                                        com.itsaky.androidide.R.string.debugger_a11y_bp_long_press));
                info.addAction(toggle);
                info.addAction(longPress);
            }
        });
    }

    public void bind(@NonNull CodeEditor editor, @NonNull String file) {
        this.editor = editor;
        this.currentFile = BreakpointManager.normalize(file);
    }

    public void setOnBreakpointClickListener(@Nullable OnBreakpointClickListener l) {
        this.clickListener = l;
        // PR-D6: 每次设置 listener 时重建 GestureDetector,以确保
        // 闭包引用最新的 clickListener;在没 listener 的状态下 GestureDetector
        // 也保留(便于 a11y 事件)但 dispatch 内部判空。
        initGestureDetector();
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        // 必须返回 true 才能接收后续 onSingleTapUp/onLongPress
                        return true;
                    }
                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        if (clickListener == null || currentFile == null || editor == null) {
                            return false;
                        }
                        int row = rowAtY(e.getY());
                        if (row < 0) return false;
                        clickListener.onBreakpointClick(currentFile, row);
                        performClick();
                        return true;
                    }
                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (clickListener == null || currentFile == null || editor == null) {
                            return;
                        }
                        int row = rowAtY(e.getY());
                        if (row < 0) return;
                        IdeBreakpoint nearest = findNearest(currentFile, row);
                        if (nearest != null) {
                            clickListener.onBreakpointLongClick(nearest);
                        } else {
                            // 无最近断点时,长按也走"切换"路径(与单击一致)
                            clickListener.onBreakpointClick(currentFile, row);
                        }
                        performLongClick();
                    }
                });
        // 不需要长按超时二次触发 (我们已在 onLongPress 中处理)
        gestureDetector.setIsLongpressEnabled(true);
    }

    private int rowAtY(float y) {
        if (editor == null) return -1;
        int firstRow = editor.getFirstVisibleRow();
        float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return -1;
        return firstRow + (int) (y / rowHeight);
    }

    public void refresh() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (editor == null || currentFile == null) return;
        if (getWidth() <= 0 || getHeight() <= 0) return;

        List<IdeBreakpoint> bps = BreakpointManager.getInstance().forFile(currentFile);
        if (bps.isEmpty()) return;

        final float rowHeight = editor.getRowHeight();
        final float textOffset = editor.getOffsetX();
        final float firstVisibleRow = editor.getFirstVisibleRow();
        final int lastVisibleRow = firstVisibleRow + editor.getRowCountOnScreen() + 1;

        final float cx = getWidth() / 2f;
        final float r = dp(GLYPH_RADIUS_DP);

        for (IdeBreakpoint bp : bps) {
            if (bp.line < firstVisibleRow || bp.line > lastVisibleRow) continue;
            float topY = (bp.line - firstVisibleRow) * rowHeight;
            float cy = topY + rowHeight / 2f;
            if (cy < r || cy > getHeight() - r) continue;

            drawGlyph(canvas, cx, cy, r, bp);
        }
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float r, IdeBreakpoint bp) {
        int fill = BreakpointStateColors.colorForState(getContext(), bp.state);
        fillPaint.setColor(fill);
        canvas.drawCircle(cx, cy, r, fillPaint);
        outlinePaint.setColor(adjustAlpha(Color.WHITE, 0.85f));
        canvas.drawCircle(cx, cy, r, outlinePaint);

        if (bp.state == IdeBreakpoint.State.HIT) {
            hitRingPaint.setColor(adjustAlpha(Color.WHITE, 0.6f));
            canvas.drawCircle(cx, cy, r + dp(3f), hitRingPaint);
        }
        if (bp.state == IdeBreakpoint.State.DISABLED) {
            // 斜杠
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(2f));
            canvas.drawLine(cx - r, cy + r, cx + r, cy - r, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.CONDITION) {
            // 内部菱形 - 表示带条件
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx, cy - r * 0.5f, cx + r * 0.5f, cy, outlinePaint);
            canvas.drawLine(cx + r * 0.5f, cy, cx, cy + r * 0.5f, outlinePaint);
            canvas.drawLine(cx, cy + r * 0.5f, cx - r * 0.5f, cy, outlinePaint);
            canvas.drawLine(cx - r * 0.5f, cy, cx, cy - r * 0.5f, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.LOG) {
            // 内部"文"字形简化：两条横线 + 一条竖线 - 表示日志点
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            float armW = r * 0.6f;
            float armH = r * 0.35f;
            canvas.drawLine(cx - armW, cy - armH, cx + armW, cy - armH, outlinePaint);
            canvas.drawLine(cx - armW, cy,        cx + armW, cy,        outlinePaint);
            canvas.drawLine(cx,         cy - r * 0.6f, cx,       cy + r * 0.6f, outlinePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editor == null || currentFile == null) return super.onTouchEvent(event);
        if (clickListener == null) return super.onTouchEvent(event);
        // PR-D6: 委托给 GestureDetector,正确处理 DOWN/MOVE/UP/CANCEL
        // 以及单按时长按区分。原实现用 `event.getEventTime() - event.getDownTime() > 500L`
        // 仅在 ACTION_UP 时判定,既容易误触又没考虑用户在长按期间移动手指。
        if (gestureDetector == null) initGestureDetector();
        boolean handled = gestureDetector.onTouchEvent(event);
        // 让 View 自身的 clickable/longClickable 状态可以保持 a11y 行为
        if (event.getAction() == MotionEvent.ACTION_UP && !handled) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Nullable
    private IdeBreakpoint findNearest(@NonNull String file, int row) {
        IdeBreakpoint best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (IdeBreakpoint bp : BreakpointManager.getInstance().forFile(file)) {
            int d = Math.abs(bp.line - row);
            if (d < bestDelta && d <= 2) {
                bestDelta = d;
                best = bp;
            }
        }
        return best;
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
