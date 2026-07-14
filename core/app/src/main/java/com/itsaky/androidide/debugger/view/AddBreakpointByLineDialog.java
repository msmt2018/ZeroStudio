/*
 *  ZeroStudio IDE - 按行号添加断点弹窗
 *
 *  用户输入行号 -> 点击「确认」-> 弹出已有的断点类型选择弹窗
 *  (BreakpointTypePickerDialog) -> 选择类型后应用断点.
 *
 *  入口: DebuggerActionMenuProvider 的「按行号添加断点…」菜单项.
 */

package com.itsaky.androidide.debugger.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.utils.ILogger;

/**
 * 输入行号 -> 选择断点类型 -> 应用.
 *
 * <p>流程:
 * <ol>
 *   <li>用户输入行号, 点击「确认」</li>
 *   <li>校验行号合法 (正整数) 且当前有打开的文件</li>
 *   <li>弹出 {@link BreakpointTypePickerDialog} 让用户选择断点类型</li>
 *   <li>选择后通过 {@link BreakpointDetailDialog#showForNew} 或
 *       {@link BreakpointManager#toggle} 应用</li>
 * </ol>
 */
public class AddBreakpointByLineDialog extends DialogFragment {

    private static final String TAG = "AddBpByLine";
    private static final String ARG_FILE = "file";

    @Nullable private String filePath;

    /**
     * 显示弹窗.
     *
     * @param activity 宿主 Activity (必须是 FragmentActivity)
     * @param filePath 当前编辑器打开的文件绝对路径, 为 null 时弹窗会提示无文件
     */
    public static void show(@NonNull FragmentActivity activity, @Nullable String filePath) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            AddBreakpointByLineDialog d = new AddBreakpointByLineDialog();
            Bundle b = new Bundle();
            b.putString(ARG_FILE, filePath);
            d.setArguments(b);
            d.show(activity.getSupportFragmentManager(), TAG);
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": show failed: " + t.getMessage());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle b = getArguments();
        if (b != null) {
            filePath = b.getString(ARG_FILE);
        }

        Context ctx = requireContext();
        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.setCanceledOnTouchOutside(true);

        // 用代码构建布局, 不引入新的 XML 布局文件.
        int pad = dp(ctx, 20);
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(ctx);
        card.setRadius(dp(ctx, 16));
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setCardElevation(dp(ctx, 8));
        com.google.android.material.card.MaterialCardView.LayoutParams cardLp =
                new com.google.android.material.card.MaterialCardView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(pad, pad, pad, pad);
        card.setLayoutParams(cardLp);

        android.widget.LinearLayout root =
                new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // 标题
        TextView title = new TextView(ctx);
        title.setText(R.string.debugger_add_bp_by_line_title);
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1E1E1E);
        root.addView(title, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 文件提示 (如果有)
        if (filePath != null && !filePath.isEmpty()) {
            TextView fileHint = new TextView(ctx);
            fileHint.setText(filePath);
            fileHint.setTextSize(11f);
            fileHint.setTextColor(android.graphics.Color.GRAY);
            fileHint.setMaxLines(2);
            fileHint.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            android.widget.LinearLayout.LayoutParams fileLp =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            fileLp.topMargin = dp(ctx, 4);
            root.addView(fileHint, fileLp);
        }

        // 输入框
        TextInputLayout inputLayout = new TextInputLayout(ctx);
        inputLayout.setHint(getString(R.string.debugger_add_bp_by_line_hint));
        TextInputEditText input = new TextInputEditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputLayout.addView(input);
        android.widget.LinearLayout.LayoutParams inputLp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(ctx, 12);
        root.addView(inputLayout, inputLp);

        // 按钮区
        android.widget.LinearLayout buttonRow =
                new android.widget.LinearLayout(ctx);
        buttonRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonRow.setGravity(android.view.Gravity.END);
        android.widget.LinearLayout.LayoutParams btnRowLp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(ctx, 16);
        root.addView(buttonRow, btnRowLp);

        Button cancelBtn = new Button(ctx);
        cancelBtn.setText(android.R.string.cancel);
        cancelBtn.setOnClickListener(v -> dismiss());
        buttonRow.addView(cancelBtn);

        Button okBtn = new Button(ctx);
        okBtn.setText(android.R.string.ok);
        android.widget.LinearLayout.LayoutParams okLp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.leftMargin = dp(ctx, 8);
        okBtn.setOnClickListener(v -> onConfirm(input, inputLayout));
        buttonRow.addView(okBtn, okLp);

        card.addView(root);
        dialog.setContentView(card);
        return dialog;
    }

    private void onConfirm(@NonNull TextInputEditText input, @NonNull TextInputLayout inputLayout) {
        Context ctx = requireContext();
        // 校验文件
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(ctx, R.string.debugger_add_bp_by_line_no_file,
                    Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        // 校验行号
        CharSequence raw = input.getText();
        int line = -1;
        if (raw != null) {
            String s = raw.toString().trim();
            if (!s.isEmpty()) {
                try {
                    line = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (line < 1) {
            inputLayout.setError(getString(R.string.debugger_add_bp_by_line_invalid));
            return;
        }

        // 关闭当前弹窗, 弹出断点类型选择
        final String file = filePath;
        final int targetLine = line;
        dismiss();
        showBreakpointTypePicker(file, targetLine);
    }

    /**
     * 弹出已有的断点类型选择弹窗. 用户选择类型后:
     * <ul>
     *   <li>普通行断点 -> 直接 {@link BreakpointManager#toggle}</li>
     *   <li>其他类型 -> {@link BreakpointDetailDialog#showForNew} 打开详情弹窗</li>
     * </ul>
     */
    private void showBreakpointTypePicker(@NonNull String file, int line) {
        FragmentActivity activity = (FragmentActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        BreakpointTypePickerDialog.show(activity, file, line, 0f, 0f,
                (entry, f, l, x, y) -> {
                    // 普通行断点直接 toggle (无需额外配置)
                    if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.GUTTER_LINE_CLICK) {
                        BreakpointManager.getInstance().toggle(f, l);
                        return;
                    }
                    // 其他类型走详情弹窗
                    BreakpointDetailDialog.showForNew(activity, f, l, entry, null, null);
                });
    }

    private static int dp(@NonNull Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
