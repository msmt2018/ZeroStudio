/*
 *  ZeroStudio IDE - 断点侧边栏 View (Phase 20 + Phase 22 + Phase 23 优化)
 *
 *  透明 View 覆盖在 CodeEditor 的 gutter 区域，绘制 14 种状态的断点图标
 *  + 命中次数徽章 + 命中行高亮。监听 CodeEditor 的滚动事件实时刷新。
 *
 *  状态/视觉:
 *    - 14 种状态 (NORMAL/INVALID/VERIFIED/CONDITION/LOG/DISABLED/HIT/EXCEPTION/
 *      FIELD_WATCHPOINT/METHOD/DEPENDENT/DEPENDENT_PENDING/TEMPORARY/INLINE)
 *    - 命中后右侧圆角徽章 + 数字 (9999+ 截断)
 *    - 内联断点 (LINE + elementName) dot 内右指三角
 *    - DISABLED 斜线, CONDITION 菱形, LOG 文字形, METHOD 双向箭头,
 *      EXCEPTION 感叹号, FIELD_WATCHPOINT 矩形, DEPENDENT 双圆点, TEMPORARY 十字
 *    - 命中行水平贯穿高亮 (跟 BreakpointColumnView 一致)
 *    - 命中状态外环脉冲 (hitRingPaint)
 *  交互:
 *    - 单击空白行 → OnBreakpointClickListener.onBreakpointClick(file, line, x, y)
 *    - 单击已存在断点 → onBreakpointExistingClick(bp, x, y)
 *    - 长按已有断点 → onBreakpointLongClick(bp, x, y)
 *  无障碍:
 *    - ACTION_CLICK → 切换 / 弹 picker
 *    - ACTION_LONG_CLICK → 上下文菜单
 *  事件流 (Phase 23):
 *    BreakpointGutterManager.show() 创建 → BreakpointGutterManager 内部
 *    可选 attach BreakpointSidebar 替代 BreakpointColumnView (用
 *    BreakpointGutterManager.useLegacySidebar = true 设置,或者等 Phase 24
 *    切回)。
 */

package com.itsaky.androidide.debugger.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.itsaky.androidide.debugger.BreakpointStateColors;
import com.itsaky.androidide.debugger.DebugSessionState;
import com.itsaky.androidide.debugger.DebuggerController;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.util.List;

/**
 * 透明 View，覆盖在编辑器左侧 gutter 区域。
 *  监听 CodeEditor 的滚动 / 缩放 / 行号变化，绘制当前可见行的断点图标 + 徽章。
 *  点击触发 {@link OnBreakpointClickListener}。
 */
public class BreakpointSidebar extends View {

    public interface OnBreakpointClickListener {
        /** 短按空白行 (无断点): 添加新断点。 */
        void onBreakpointClick(@NonNull String file, int line, float screenX, float screenY);
        /** 短按已有断点: 弹编辑 / 详细设置。 */
        void onBreakpointExistingClick(@NonNull IdeBreakpoint bp, float screenX, float screenY);
        /** 长按已有断点: 上下文菜单。 */
        void onBreakpointLongClick(@NonNull IdeBreakpoint bp, float screenX, float screenY);
    }

    private static final float GLYPH_RADIUS_DP = 5.0f;
    private static final float HIT_LINE_HEIGHT_DP = 2.0f;
    private static final int HIT_LINE_COLOR_BASE = 0xFFFF6F00;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitBadgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    @Nullable private CodeEditor editor;
    @Nullable private String currentFile;
    @Nullable private OnBreakpointClickListener clickListener;
    @Nullable private GestureDetector gestureDetector;
    @Nullable private DebugSessionState.Listener sessionListener;
    private int lastHitLine = -1;

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
        // Phase 22: 命中行水平贯穿高亮 (跟 BreakpointColumnView 同步)
        hitLinePaint.setStyle(Paint.Style.FILL);
        hitLinePaint.setColor(HIT_LINE_COLOR_BASE);
        // Phase 22: 命中次数徽章 (圆角药丸 + 白色数字)
        hitBadgePaint.setStyle(Paint.Style.FILL);
        hitBadgeTextPaint.setColor(Color.WHITE);
        hitBadgeTextPaint.setTextSize(sp(11));
        hitBadgeTextPaint.setTextAlign(Paint.Align.CENTER);
        hitBadgeTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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
        subscribeSessionState();
    }

    public void unbind() {
        unsubscribeSessionState();
        this.editor = null;
        this.currentFile = null;
    }

    /**
     * Phase 23 续: 重绑文件 (用户在不同文件间切换时调用)。
     * 跟 BreakpointGutterManager.rebindFile 配合,文件变时更新 currentFile
     * 并 invalidate 重画。
     */
    public void rebindFile(@NonNull String newFile) {
        this.currentFile = BreakpointManager.normalize(newFile);
        invalidate();
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
                        // Phase 23 续: 区分"空白行"和"已有断点"两个事件,
                        // 让 IDE 端 (EditorHandlerActivity) 走不同路径。
                        IdeBreakpoint existing = findAt(currentFile, row);
                        if (existing != null) {
                            clickListener.onBreakpointExistingClick(existing, e.getRawX(), e.getRawY());
                        } else {
                            clickListener.onBreakpointClick(currentFile, row,
                                    e.getRawX(), e.getRawY());
                        }
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
                            clickListener.onBreakpointLongClick(nearest, e.getRawX(), e.getRawY());
                        } else {
                            // 无最近断点时长按走"添加"路径
                            clickListener.onBreakpointClick(currentFile, row,
                                    e.getRawX(), e.getRawY());
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
        // PR-D6 / Phase 20: firstVisibleRow 偶发为 -1 (用户滚到编辑器顶部上方时) —
        // clamp 到 0 避免 NPE / index out of bounds。
        if (firstRow < 0) firstRow = 0;
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
        if (bps.isEmpty() && lastHitLine < 0) return;

        final float rowHeight = editor.getRowHeight();
        if (rowHeight <= 0f) return;
        final float firstVisibleRow = editor.getFirstVisibleRow();
        // PR-D6 / Phase 20: 同样 clamp 到 0,避免 firstVisibleRow < 0 时 NPE。
        final int firstRow = firstVisibleRow < 0 ? 0 : (int) firstVisibleRow;
        final int lastVisibleRow = firstRow + (int) (getHeight() / Math.max(1f, rowHeight)) + 1;

        final float cx = getWidth() / 2f;
        final float r = dp(GLYPH_RADIUS_DP);

        // Phase 22: 命中行水平贯穿高亮 (先画,再画 bp 圆点)
        drawHitLineIfVisible(canvas, firstRow, rowHeight);

        for (IdeBreakpoint bp : bps) {
            if (bp.line < firstRow || bp.line > lastVisibleRow) continue;
            if (bp.line <= 0) continue;
            float topY = (bp.line - firstRow) * rowHeight;
            float cy = topY + rowHeight / 2f;
            if (cy < r || cy > getHeight() - r) continue;

            drawGlyph(canvas, cx, cy, r, bp);
        }
    }

    /**
     * Phase 22 / Phase 23 续: 当前 DebuggerController 暂停的行 (lastSuspendInfo) 整行水平贯穿高亮。
     * 跟 BreakpointColumnView.drawHitLine 行为一致,只是实现略不同(简化版,没 columnBg 渐变)。
     */
    private void drawHitLineIfVisible(Canvas canvas, int firstRow, float rowHeight) {
        if (lastHitLine < 0) return;
        if (lastHitLine < firstRow) return;
        float topY = (lastHitLine - firstRow) * rowHeight;
        if (topY > getHeight()) return;
        float bottomY = topY + rowHeight;
        // 中间一条 2dp 粗的橙线
        float cy = (topY + bottomY) / 2f;
        hitLinePaint.setColor(HIT_LINE_COLOR_BASE);
        canvas.drawRect(0, cy - dp(HIT_LINE_HEIGHT_DP) / 2f,
                getWidth(), cy + dp(HIT_LINE_HEIGHT_DP) / 2f, hitLinePaint);
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float r, IdeBreakpoint bp) {
        int fill = BreakpointStateColors.colorForState(getContext(), bp.state);
        fillPaint.setColor(fill);
        canvas.drawCircle(cx, cy, r, fillPaint);
        outlinePaint.setColor(adjustAlpha(Color.WHITE, 0.85f));
        outlinePaint.setStrokeWidth(dp(1.5f));
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
        if (bp.state == IdeBreakpoint.State.DEPENDENT
                || bp.state == IdeBreakpoint.State.DEPENDENT_PENDING) {
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
            // 内部"文"字形简化:两条横线 + 一条竖线 - 表示日志点
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.2f));
            float armW = r * 0.6f;
            float armH = r * 0.35f;
            canvas.drawLine(cx - armW, cy - armH, cx + armW, cy - armH, outlinePaint);
            canvas.drawLine(cx - armW, cy,        cx + armW, cy,        outlinePaint);
            canvas.drawLine(cx,         cy - r * 0.6f, cx,       cy + r * 0.6f, outlinePaint);
        }
        // Phase 22g: 内联断点 (LINE + elementName 非空) 在 dot 内画右指三角
        if (bp.kind == IdeBreakpoint.Kind.LINE
                && bp.elementName != null
                && !bp.elementName.isEmpty()) {
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(1.0f));
            Path path = new Path();
            float baseX = cx + dp(0.5f);
            float topY = cy - r * 0.55f;
            float midY = cy + r * 0.55f;
            float tipX = cx + r * 0.85f;
            path.moveTo(baseX, topY);
            path.lineTo(tipX, cy);
            path.lineTo(baseX, midY);
            path.close();
            outlinePaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, outlinePaint);
            outlinePaint.setStyle(Paint.Style.STROKE);
        }
        // Phase 22a + 22i: 命中次数徽章 (右侧药丸 + 状态感知底色)
        if (bp.hitCountReceived > 0) {
            drawHitCountBadge(canvas, cx, cy, r, bp);
        }
        // Phase 23 E3: 出口 paint 状态重置。
        // 内联断点分支 (上面) 临时把 outlinePaint.style 切到 FILL,
        // 必须切回 STROKE。另外,各状态分支 (DISABLED / CONDITION / 等)
        // 都会改 outlinePaint.strokeWidth / color,这些都应重置,否则下
        // 一个 bp 拿到的 outlinePaint 是脏的。
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1.5f));
        outlinePaint.setColor(Color.WHITE);
        outlinePaint.setPathEffect(null);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Phase 22a + 22i 续: 命中次数徽章 — 圆角药丸,数字 9999+ 截断。
     * 状态感知底色 (HIT 红 / DISABLED 暗灰 / VERIFIED 绿 / 默认深灰),
     * 跟 BreakpointColumnView + BreakpointListAdapter 一致。
     */
    private void drawHitCountBadge(Canvas canvas, float cx, float cy, float r, IdeBreakpoint bp) {
        int n = bp.hitCountReceived;
        String text = n > 9999 ? "9999+" : String.valueOf(n);
        float textW = hitBadgeTextPaint.measureText(text);
        float padH = dp(5f);
        float padV = dp(2f);
        float badgeW = textW + padH * 2f;
        float badgeH = hitBadgeTextPaint.getTextSize() + padV * 2f;
        float badgeLeft = cx + r + dp(3f);
        float badgeTop = cy - badgeH / 2f;
        float badgeRight = badgeLeft + badgeW;
        float badgeBottom = cy + badgeH / 2f;
        // 状态感知底色
        int badgeBg = BreakpointStateColors.hitCountBadgeBackgroundForState(getContext(), bp.state);
        hitBadgePaint.setColor(badgeBg);
        tmpRect.set(badgeLeft, badgeTop, badgeRight, badgeBottom);
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), hitBadgePaint);
        // 数字居中
        Paint.FontMetrics fm = hitBadgeTextPaint.getFontMetrics();
        float textY = (badgeTop + badgeBottom) / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, (badgeLeft + badgeRight) / 2f, textY, hitBadgeTextPaint);
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

    /**
     * Phase 23 续: 在指定 (file, row) 精确查找已有断点 (delta=0)。
     * 用法: 单击时如果该行已有 bp,走 onBreakpointExistingClick 而不是 onBreakpointClick。
     */
    @Nullable
    private IdeBreakpoint findAt(@NonNull String file, int row) {
        for (IdeBreakpoint bp : BreakpointManager.getInstance().forFile(file)) {
            if (bp.line == row && bp.isActive()) return bp;
        }
        return null;
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    // ---- Phase 23 续: 监听 DebuggerController,更新 lastHitLine 触发 onDraw ----

    private void subscribeSessionState() {
        if (sessionListener != null) return;
        sessionListener = new DebugSessionState.Listener() {
            @Override
            public void onStateChanged(@NonNull DebugSessionState state) {
                if (state.isSuspended() && state.currentFrame() != null) {
                    com.zerostudio.debugger.api.StackFrameInfo frame = state.currentFrame();
                    String src = frame.sourceFile;
                    if (currentFile != null && src != null && currentFile.endsWith(src)) {
                        lastHitLine = frame.lineNumber;
                    } else if (currentFile == null || currentFile.isEmpty()) {
                        // 没有绑文件时,直接显示行
                        lastHitLine = frame.lineNumber;
                    } else {
                        lastHitLine = -1;
                    }
                } else {
                    lastHitLine = -1;
                }
                invalidate();
            }
        };
        DebuggerController.getInstance().sessionState().addListener(sessionListener);
    }

    private void unsubscribeSessionState() {
        if (sessionListener == null) return;
        DebuggerController.getInstance().sessionState().removeListener(sessionListener);
        sessionListener = null;
        lastHitLine = -1;
    }
}
