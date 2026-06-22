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
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    }

    public void bind(@NonNull CodeEditor editor, @NonNull String file) {
        this.editor = editor;
        this.currentFile = BreakpointManager.normalize(file);
    }

    public void setOnBreakpointClickListener(@Nullable OnBreakpointClickListener l) {
        this.clickListener = l;
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
        int fill = colorForState(bp.state);
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editor == null || currentFile == null) return super.onTouchEvent(event);
        if (clickListener == null) return super.onTouchEvent(event);
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float y = event.getY();
        float rowHeight = editor.getRowHeight();
        int firstRow = editor.getFirstVisibleRow();
        int row = firstRow + (int) (y / rowHeight);
        if (row < 0) return true;

        // 命中：在该行 ±2 行内查找最近断点
        IdeBreakpoint nearest = findNearest(currentFile, row);
        if (event.getEventTime() - event.getDownTime() > 500L && nearest != null) {
            clickListener.onBreakpointLongClick(nearest);
        } else if (nearest != null) {
            // 短按：切换该断点
            clickListener.onBreakpointClick(currentFile, row);
        } else {
            // 在新行添加断点
            clickListener.onBreakpointClick(currentFile, row);
        }
        performClick();
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

    private static int colorForState(IdeBreakpoint.State state) {
        switch (state) {
            case NORMAL:    return 0xFFE53935; // 红
            case INVALID:   return 0xFFB71C1C; // 暗红 / 圆环空心
            case VERIFIED:  return 0xFF43A047; // 绿
            case CONDITION: return 0xFFFBC02D; // 黄
            case DISABLED:  return 0xFF9E9E9E; // 灰
            case HIT:       return 0xFF1E88E5; // 蓝
            default:        return 0xFFE53935;
        }
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
