/*
 *  ZeroStudio IDE - 断点类型选择器弹窗 (Phase 20 重构 — 高斯模糊磨砂)
 *
 *  替代原 BreakpointTypePicker (ListPopupWindow 实现,无磨砂效果)。
 *
 *  显示 4 大类 + 13 个子类型:
 *    GUTTER       (5 个)  - 行号边栏单击/右键触发
 *    VARIABLES    (2 个)  - Variables / Watches 面板右键触发
 *    WINDOW       (3 个)  - Breakpoints Window + 触发
 *    BROWSER      (3 个)  - 浏览器开发者工具特有 (Phase 20 接口预留)
 *
 *  UI 效果:
 *    - 全屏 Dialog (Theme_Material3_Dialog 或自定义 transparent)
 *    - 内容布局 bg_dialog_frosted_glass
 *    - 单击条目 -> 回调 (Entry + file + line + 屏幕坐标)
 *
 *  入口:
 *    BreakpointTypePickerDialog.show(activity, file, line, screenX, screenY, cb)
 *
 *  与原 ListPopupWindow 实现差异:
 *    - 全屏 Dialog → 用户可看到背景上下文 (毛玻璃)
 *    - 显示所有 13 个,不限 Gutter 3 个
 *    - 类别化显示,符合用户描述
 *    - 不需要 ghost anchor (Activity 已有 root content)
 */

package com.itsaky.androidide.debugger.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
import com.itsaky.androidide.utils.ILogger;

public class BreakpointTypePickerDialog extends DialogFragment {

    private static final String TAG = "BpTypePicker";

    public interface Callback {
        /**
         * 用户选了一个断点类型。
         *
         * @param entry    选中的断点类型元数据
         * @param file     目标文件
         * @param line     目标行
         * @param screenX  点击屏幕 X 坐标 (px)
         * @param screenY  点击屏幕 Y 坐标 (px)
         */
        void onTypePicked(@NonNull BreakpointTypeCatalog.Entry entry,
                          @NonNull String file, int line,
                          float screenX, float screenY);
    }

    private static final String ARG_FILE = "file";
    private static final String ARG_LINE = "line";
    private static final String ARG_X = "x";
    private static final String ARG_Y = "y";

    @Nullable private String file;
    private int line = -1;
    private float screenX, screenY;
    @Nullable private Callback pendingCallback;

    public static void show(
            @NonNull FragmentActivity activity,
            @NonNull String file,
            int line,
            float screenX,
            float screenY,
            @NonNull Callback cb) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            BreakpointTypePickerDialog d = new BreakpointTypePickerDialog();
            Bundle b = new Bundle();
            b.putString(ARG_FILE, file);
            b.putInt(ARG_LINE, line);
            b.putFloat(ARG_X, screenX);
            b.putFloat(ARG_Y, screenY);
            d.setArguments(b);
            d.pendingCallback = cb;
            d.show(activity.getSupportFragmentManager(), TAG);
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "show failed: " + t.getMessage());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle b = getArguments();
        if (b != null) {
            file = b.getString(ARG_FILE);
            line = b.getInt(ARG_LINE, -1);
            screenX = b.getFloat(ARG_X, 0f);
            screenY = b.getFloat(ARG_Y, 0f);
        }
        if (file == null) file = "";

        // 透明 Dialog, 内部用 frosted_glass 背景。
        // 关键修复 (Phase 24 bug): 窗口高度改为 WRAP_CONTENT, 避免覆盖整个编辑器;
        // 移除 FLAG_BLUR_BEHIND (旧系统会触发全屏模糊), 改用 setDimAmount 即可。
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 宽度 MATCH_PARENT 让内部 LinearLayout 的 maxWidth 生效; 高度 WRAP_CONTENT
            // 避免占满整屏
            w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            // 用 dim 替代 blur, 避免 FLAG_BLUR_BEHIND 在某些系统上把整个窗口涂白
            w.setDimAmount(0.5f);
            w.setGravity(Gravity.CENTER);
        }
        dialog.setCanceledOnTouchOutside(true);

        // 加载布局 — 用 contentView LayoutParams 强制宽度 MATCH_PARENT,
        // 否则 inflate(null) 会让根 FrameLayout 退化为 WRAP_CONTENT,
        // 进而导致内部 LinearLayout 中 weight=1 的文字列塌缩成 0 (只剩图标可见)。
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View root = inflater.inflate(R.layout.dialog_breakpoint_type_picker, null, false);
        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        populate(root);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // 在 onStart 中强制设置窗口尺寸，覆盖 DialogFragment 默认主题可能
        // 设置的 windowMinWidth/windowMinHeight（这些值可能很小，导致对话框
        // 被压缩成 "高 300 宽 50dp" 的异常竖条）。
        Dialog d = getDialog();
        if (d != null) {
            Window w = d.getWindow();
            if (w != null) {
                w.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 不主动清 pendingCallback — 避免在 dismiss 后丢失回调引用
        // (Callback 由调用方持有)
    }

    private void populate(@NonNull View root) {
        LinearLayout list = root.findViewById(R.id.bptp_list);
        if (list == null) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        Context ctx = requireContext();
        for (BreakpointTypeCatalog.Category cat : BreakpointTypeCatalog.categories()) {
            // 1. 类别 header
            TextView header = (TextView) inflater.inflate(
                    R.layout.item_bp_type_header, list, false);
            header.setText(categoryTitle(cat));
            list.addView(header);
            // 2. 该类所有条目
            for (BreakpointTypeCatalog.Entry e : BreakpointTypeCatalog.forCategory(cat)) {
                View item = inflater.inflate(R.layout.item_bp_type_entry, list, false);
                ((ImageView) item.findViewById(R.id.bptpe_icon))
                        .setImageResource(e.iconRes);
                ((TextView) item.findViewById(R.id.bptpe_title))
                        .setText(e.titleRes);
                ((TextView) item.findViewById(R.id.bptpe_desc))
                        .setText(e.descRes);
                item.setOnClickListener(v -> onEntryClicked(e));
                list.addView(item);
            }
        }
    }

    private void onEntryClicked(@NonNull BreakpointTypeCatalog.Entry e) {
        try {
            if (pendingCallback != null && file != null) {
                pendingCallback.onTypePicked(e, file, line, screenX, screenY);
            }
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "callback failed: " + t.getMessage());
        }
        dismissAllowingStateLoss();
    }

    private int categoryTitle(@NonNull BreakpointTypeCatalog.Category cat) {
        switch (cat) {
            case GUTTER:    return R.string.debugger_bp_cat_gutter;
            case VARIABLES: return R.string.debugger_bp_cat_variables;
            case WINDOW:    return R.string.debugger_bp_cat_window;
            case BROWSER:   return R.string.debugger_bp_cat_browser;
            default:        return R.string.debugger_bp_cat_gutter;
        }
    }
}
