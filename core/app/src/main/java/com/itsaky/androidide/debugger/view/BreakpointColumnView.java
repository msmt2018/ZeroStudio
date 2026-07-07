/*
 *  ZeroStudio IDE - 断点列控件 (Phase 20 重构)
 *
 *  完整镜像 sora-editor 的行号控件 (EditorRenderer.drawLineNumber)
 *  设计,但不绘制行号,只绘制断点状态图标 + 命中行水平高亮。
 *
 *  同步维度 (与行号控件 1:1):
 *    - 缩放: getTextSizePx (随用户调整)
 *    - 行高: getRowHeight (随字体大小变化)
 *    - 滚动: ScrollEvent → invalidate
 *    - 内容变化: ContentChangeEvent → invalidate
 *    - 行号边栏起点: getLineNumberMarginLeft + measureLineNumber
 *    - 缩放重布局: textSize 改变后重新 layout
 *    - 水平线同步: 与行号列共用 editor 内部渲染的 per-row separator (sora 1.6+)
 *
 *  命中行: 从 DebuggerController.sessionState().lastSuspendInfo() 拿 frame.lineNumber,
 *          整行贯穿水平高亮 (区别于单点状图标),让用户立刻看到当前停在第几行。
 *
 *  接口:
 *    - bind(CodeEditor, file)
 *    - setOnBreakpointActionListener(listener)
 *    - show() / hide()
 *
 *  位置: 紧贴行号控件左侧,大小与行号列相同 (高度 MATCH_PARENT,宽度跟 measureLineNumber)。
 */

package com.itsaky.androidide.debugger.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.BreakpointStateColors;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import java.util.List;

public class BreakpointColumnView extends View {

    public interface OnBreakpointActionListener {
        /** 单击空白行 -> 弹"断点类型选择"弹窗 (传 file + line + 点击屏幕坐标)。 */
        void onBreakpointClick(@NonNull String file, int line, float screenX, float screenY);
        /** 单击空白行 -> 仅在需要直接 toggle 时 (用户配置);默认由 Picker 处理。 */
        default void onBreakpointClick(@NonNull String file, int line) {
            onBreakpointClick(file, line, 0f, 0f);
        }
        /** 长按已有断点 -> 弹"详细设置"弹窗。 */
        void onBreakpointLongClick(@NonNull IdeBreakpoint bp, float screenX, float screenY);
        /** 单击已存在的断点 -> 弹"详细设置"弹窗 (切换行为由 dialog 决定)。 */
        void onBreakpointExistingClick(@NonNull IdeBreakpoint bp, float screenX, float screenY);
    }

    // 渲染常量
    private static final float GLYPH_RADIUS_DP = 5.0f;
    private static final float HIT_LINE_HEIGHT_DP = 2.0f;
    private static final int HIT_LINE_COLOR_BASE = 0xFFFF6F00;   // 深橙
    private static final int COLUMN_BG_TOP = 0x1A000000;          // 10% 顶部
    private static final int COLUMN_BG_BOTTOM = 0x00000000;       // 0% 底部
    private static final int DIVIDER_COLOR = 0x33FFFFFF;           // 20% 白 (per-row separator)

    // 状态机
    @Nullable private CodeEditor editor;
    @Nullable private String currentFile;
    @Nullable private OnBreakpointActionListener listener;
    @Nullable private GestureDetector gestureDetector;
    @Nullable private DebugSessionState.Listener sessionListener;
    private final List<SubscriptionReceipt<?>> subscriptions = new ArrayList<>();
    private int lastHitLine = -1;

    // 画笔
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint columnBgPaint = new Paint();
    private final Paint dividerPaint = new Paint();
    private final RectF tmpRect = new RectF();
    @Nullable private Shader columnBgShader;

    public BreakpointColumnView(Context context) {
        this(context, null);
    }

    public BreakpointColumnView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BreakpointColumnView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1.5f));
        outlinePaint.setColor(Color.WHITE);
        hitRingPaint.setStyle(Paint.Style.STROKE);
        hitRingPaint.setStrokeWidth(dp(2f));
        hitRingPaint.setColor(Color.WHITE);
        hitLinePaint.setStyle(Paint.Style.FILL);
        hitLinePaint.setColor(HIT_LINE_COLOR_BASE);
        columnBgPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(DIVIDER_COLOR);
        dividerPaint.setStrokeWidth(dp(0.5f));
        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(context.getString(
                R.string.debugger_a11y_bp_long_press));
        // 无障碍: 暴露 toggle / longClick 两个 action。
        ViewCompat.setAccessibilityDelegate(this,
                new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    @NonNull View host,
                    @NonNull androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClickable(true);
                info.setLongClickable(true);
                info.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                        .AccessibilityActionCompat(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .AccessibilityAction.ACTION_CLICK.getId(),
                                host.getContext().getString(
                                        R.string.debugger_action_toggle_bp)));
                info.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat
                        .AccessibilityActionCompat(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .AccessibilityAction.ACTION_LONG_CLICK.getId(),
                                host.getContext().getString(
                                        R.string.debugger_a11y_bp_long_press)));
            }
        });
        initGestureDetector();
    }

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }
                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        if (editor == null || currentFile == null || listener == null) {
                            return false;
                        }
                        int row = rowAtY(e.getY());
                        if (row < 0) return false;
                        IdeBreakpoint existing = BreakpointManager.getInstance().findAt(
                                currentFile, row);
                        int[] loc = new int[2];
                        getLocationOnScreen(loc);
                        float screenX = loc[0] + e.getX();
                        float screenY = loc[1] + e.getY();
                        if (existing != null) {
                            listener.onBreakpointExistingClick(existing, screenX, screenY);
                        } else {
                            listener.onBreakpointClick(currentFile, row, screenX, screenY);
                        }
                        performClick();
                        return true;
                    }
                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (editor == null || currentFile == null || listener == null) {
                            return;
                        }
                        int row = rowAtY(e.getY());
                        if (row < 0) return;
                        IdeBreakpoint nearest = findNearest(currentFile, row);
                        int[] loc = new int[2];
                        getLocationOnScreen(loc);
                        float screenX = loc[0] + e.getX();
                        float screenY = loc[1] + e.getY();
                        if (nearest != null) {
                            listener.onBreakpointLongClick(nearest, screenX, screenY);
                        } else {
                            // 空白处长按 = 等同于单击
                            listener.onBreakpointClick(currentFile, row, screenX, screenY);
                        }
                        performLongClick();
                    }
                });
        gestureDetector.setIsLongpressEnabled(true);
    }

    /** 绑定 CodeEditor + 文件路径。 */
    public void bind(@NonNull CodeEditor editor, @NonNull String file) {
        unbind();
        this.editor = editor;
        this.currentFile = BreakpointManager.normalize(file);
        subscribeEditorEvents();
        rebindSessionListener();
        rebuildColumnBg();
    }

    /** 切换绑定的文件路径。 */
    public void rebindFile(@NonNull String file) {
        this.currentFile = BreakpointManager.normalize(file);
        invalidate();
    }

    /** 释放订阅 + 重置内部状态。 */
    public void unbind() {
        for (SubscriptionReceipt<?> r : subscriptions) {
            try { r.unsubscribe(); } catch (Throwable ignored) {}
        }
        subscriptions.clear();
        if (sessionListener != null
                && DebuggerController.getInstance() != null
                && DebuggerController.getInstance().sessionState() != null) {
            try { DebuggerController.getInstance().sessionState().removeListener(sessionListener); }
            catch (Throwable ignored) {}
            sessionListener = null;
        }
        editor = null;
        currentFile = null;
        lastHitLine = -1;
    }

    public void setOnBreakpointActionListener(@Nullable OnBreakpointActionListener l) {
        this.listener = l;
    }

    public void refresh() { invalidate(); }

    @Nullable public CodeEditor editor() { return editor; }
    @Nullable public String currentFile() { return currentFile; }

    /** 静态构造: 把 BreakpointColumnView 挂到 editor 的 parent 上,放在行号左侧。 */
    @NonNull
    public static BreakpointColumnView attach(@NonNull CodeEditor editor, @NonNull String file) {
        ViewGroup parent = editor.getParent() instanceof ViewGroup
                ? (ViewGroup) editor.getParent() : null;
        if (parent == null) {
            // 没有 parent — 退化为 detached 模式:不挂载,只 bind
            BreakpointColumnView v = new BreakpointColumnView(editor.getContext());
            v.bind(editor, file);
            return v;
        }
        // 移除旧的 (同 editor 已有的话)
        BreakpointColumnView existing = findAttached(parent);
        if (existing != null) parent.removeView(existing);
        BreakpointColumnView v = new BreakpointColumnView(editor.getContext());
        parent.addView(v);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = android.view.Gravity.START;
        v.setLayoutParams(lp);
        v.bind(editor, file);
        v.layoutToMatchLineColumn();
        return v;
    }

    @Nullable
    private static BreakpointColumnView findAttached(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c instanceof BreakpointColumnView) return (BreakpointColumnView) c;
        }
        return null;
    }

    /**
     * 重做 layout 让 column 紧贴行号列左侧 (与 EditorRenderer 同步),
     * 宽度 = 行号列宽度 (measureLineNumber),高度 = MATCH_PARENT。
     * 在缩放 / 文本大小改变后,需要重做。
     *
     * <p>关键修复 (Phase 24 bug 修复):
     * <ul>
     *   <li>宽度严格使用 {@code measureLineNumber()}, 不再 fallback dp(36f) 过宽
     *   <li>当 {@code lineNumberMarginLeft <= 0} (布局未完成/行号未启用) 时
     *       设为 GONE, 避免占满整个编辑器区域
     * </ul>
     */
    public void layoutToMatchLineColumn() {
        if (editor == null) return;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
        float lineNumberStart = editor.getLineNumberMarginLeft();
        float lineColWidth = editor.measureLineNumber();
        // 布局未完成 / 行号未启用: 隐藏, 避免占满编辑器区域
        if (lineNumberStart <= 0f || lineColWidth <= 0f) {
            flp.width = 0;
            flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.leftMargin = 0;
            setLayoutParams(flp);
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        flp.width = Math.round(lineColWidth);
        flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        // column 起点 = 行号起点 - column 宽度 - 间隙 (紧贴行号列左侧)
        float x = Math.max(0f, lineNumberStart - flp.width - dp(2f));
        flp.leftMargin = Math.round(x);
        setLayoutParams(flp);
    }

    private void subscribeEditorEvents() {
        if (editor == null) return;
        try {
            SubscriptionReceipt<?> r1 = editor.subscribeEvent(ScrollEvent.class,
                    (event, sub) -> {
                        // 滚动: 不仅 invalidate, 还 layout (避免行号列被编辑器重排后未对齐)
                        layoutToMatchLineColumn();
                        invalidate();
                    });
            if (r1 != null) subscriptions.add(r1);
        } catch (Throwable ignored) {}
        try {
            SubscriptionReceipt<?> r2 = editor.subscribeEvent(ContentChangeEvent.class,
                    (event, sub) -> invalidate());
            if (r2 != null) subscriptions.add(r2);
        } catch (Throwable ignored) {}
        // 文本大小 / 缩放变化时,需要重做 layout。
        editor.post(() -> {
            layoutToMatchLineColumn();
            invalidate();
        });
    }

    private void rebindSessionListener() {
        try {
            DebugSessionState st = DebuggerController.getInstance().sessionState();
            if (sessionListener != null) st.removeListener(sessionListener);
            sessionListener = new DebugSessionState.Listener() {
                @Override public void onStateChanged(@NonNull DebugSessionState s) {
                    int newHit = -1;
                    if (s.isSuspended() && s.currentFrame() != null) {
                        newHit = s.currentFrame().lineNumber;
                    }
                    if (newHit != lastHitLine) {
                        lastHitLine = newHit;
                        invalidate();
                    }
                }
            };
            st.addListener(sessionListener);
            // 立即同步一次
            sessionListener.onStateChanged(st);
        } catch (Throwable t) {
            sessionListener = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildColumnBg();
    }

    private void rebuildColumnBg() {
        if (getHeight() <= 0) { columnBgShader = null; return; }
        columnBgShader = new LinearGradient(
                0, 0, 0, getHeight(),
                COLUMN_BG_TOP, COLUMN_BG_BOTTOM, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (editor == null || currentFile == null) return;
        if (getWidth() <= 0 || getHeight() <= 0) return;

        // 1. 列背景 (淡渐变,让"列"在视觉上从空白区域分离)
        if (columnBgShader != null) {
            columnBgPaint.setShader(columnBgShader);
            canvas.drawRect(0, 0, getWidth(), getHeight(), columnBgPaint);
        }

        // 2. 同步缩放后,重新对齐行号列
        layoutToMatchLineColumn();

        // 3. per-row 水平线 (与行号列共用) — 画在每个 row 底部
        drawRowSeparators(canvas);

        // 4. 命中行水平高亮
        if (lastHitLine > 0) drawHitLineHighlight(canvas, lastHitLine);

        // 5. 绘制所有可见行的断点
        drawBreakpoints(canvas);
    }

    private void drawRowSeparators(@NonNull Canvas canvas) {
        if (editor == null) return;
        float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return;
        int firstRow = editor.getFirstVisibleRow();
        if (firstRow < 0) firstRow = 0;
        int lastRow = firstRow + (int) Math.ceil(getHeight() / rowHeight) + 1;
        for (int r = firstRow; r <= lastRow; r++) {
            float y = editor.getRowTop(r) - editor.getOffsetY();
            if (y < -rowHeight || y > getHeight() + rowHeight) continue;
            canvas.drawLine(0, y, getWidth(), y, dividerPaint);
        }
    }

    private void drawHitLineHighlight(@NonNull Canvas canvas, int line) {
        if (editor == null) return;
        float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return;
        int firstRow = editor.getFirstVisibleRow();
        if (line < firstRow) return;
        float top = (line - firstRow) * rowHeight;
        float bottom = top + rowHeight;
        if (bottom < 0 || top > getHeight()) return;
        // 1. 整行贯穿水平条
        tmpRect.set(0, top + (rowHeight - dp(HIT_LINE_HEIGHT_DP)) / 2f,
                getWidth(), top + (rowHeight + dp(HIT_LINE_HEIGHT_DP)) / 2f);
        hitLinePaint.setColor(HIT_LINE_COLOR_BASE);
        canvas.drawRoundRect(tmpRect, dp(1f), dp(1f), hitLinePaint);
        // 2. 整行淡色背景 (8% 透明橙)
        hitLinePaint.setColor(0x22FF6F00);
        canvas.drawRect(0, top, getWidth(), bottom, hitLinePaint);
    }

    private void drawBreakpoints(@NonNull Canvas canvas) {
        if (editor == null || currentFile == null) return;
        List<IdeBreakpoint> bps = BreakpointManager.getInstance().forFile(currentFile);
        if (bps.isEmpty()) return;
        float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return;
        int firstRow = editor.getFirstVisibleRow();
        if (firstRow < 0) firstRow = 0;
        int lastRow = firstRow + (int) Math.ceil(getHeight() / rowHeight) + 1;
        float cx = getWidth() / 2f;
        float r = dp(GLYPH_RADIUS_DP);
        for (IdeBreakpoint bp : bps) {
            if (bp.line < firstRow || bp.line > lastRow) continue;
            if (bp.line <= 0) continue;
            float top = (bp.line - firstRow) * rowHeight;
            float cy = top + rowHeight / 2f;
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
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(2f));
            canvas.drawLine(cx - r, cy + r, cx + r, cy - r, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.CONDITION) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx, cy - r * 0.5f, cx + r * 0.5f, cy, outlinePaint);
            canvas.drawLine(cx + r * 0.5f, cy, cx, cy + r * 0.5f, outlinePaint);
            canvas.drawLine(cx, cy + r * 0.5f, cx - r * 0.5f, cy, outlinePaint);
            canvas.drawLine(cx - r * 0.5f, cy, cx, cy - r * 0.5f, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.DEPENDENT) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, outlinePaint);
            canvas.drawCircle(cx - r * 0.55f, cy, dp(1.4f), outlinePaint);
            canvas.drawCircle(cx + r * 0.55f, cy, dp(1.4f), outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.TEMPORARY) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.3f));
            canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.65f, outlinePaint);
            canvas.drawLine(cx - r * 0.65f, cy, cx + r * 0.65f, cy, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.EXCEPTION) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.15f, outlinePaint);
            canvas.drawCircle(cx, cy + r * 0.55f, dp(0.9f), outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.FIELD_WATCHPOINT) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawRect(cx - r * 0.55f, cy - r * 0.45f, cx + r * 0.55f, cy + r * 0.45f, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.METHOD) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.45f, cx - r * 0.1f, cy, outlinePaint);
            canvas.drawLine(cx - r * 0.1f, cy, cx - r * 0.55f, cy + r * 0.45f, outlinePaint);
            canvas.drawLine(cx + r * 0.55f, cy - r * 0.45f, cx + r * 0.1f, cy, outlinePaint);
            canvas.drawLine(cx + r * 0.1f, cy, cx + r * 0.55f, cy + r * 0.45f, outlinePaint);
        }
        if (bp.state == IdeBreakpoint.State.LOG) {
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
        if (listener == null) return super.onTouchEvent(event);
        // 只在断点列宽度内消费事件, 否则不消费让事件穿透到下层编辑器。
        // 防止 overlay View 因为 bounds 异常而吞掉整个编辑器区域的点击。
        final float x = event.getX();
        final int w = getWidth();
        if (w <= 0 || x < 0f || x > w) {
            return false;
        }
        if (gestureDetector == null) initGestureDetector();
        boolean handled = gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP && !handled) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        // performClick 仅在确认落在断点列内时由 GestureDetector 触发,
        // 这里直接转发到 super 处理无障碍 click 事件即可。
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        unbind();
        super.onDetachedFromWindow();
    }

    private int rowAtY(float y) {
        if (editor == null) return -1;
        int firstRow = editor.getFirstVisibleRow();
        if (firstRow < 0) firstRow = 0;
        float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return -1;
        return firstRow + (int) (y / rowHeight);
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
