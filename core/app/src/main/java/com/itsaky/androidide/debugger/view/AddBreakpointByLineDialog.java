/*
 *  ZeroStudio IDE - 按行号添加断点弹窗
 *
 *  支持三种行号表达式:
 *    1. 单个行号:  5
 *    2. 范围:      5~100  (在 5 到 100 的每一行都加断点, 含端点)
 *    3. 列表:      1,2,3  或  1，3，8  (英文逗号 , 和中文逗号 ， 都支持)
 *
 *  规则:
 *    - 范围表达式中, 起始值必须 < 终止值 (不允许 100~5)
 *    - 列表表达式中, 数字必须严格升序 (不允许 1,3,2)
 *    - 列表和范围不能混用 (不允许 1,2~5,8) —— 保持解析简单
 *
 *  流程: 输入表达式 -> 校验 -> 弹出已有的断点类型选择弹窗
 *  -> 用户选择类型后, 给所有解析出的行号批量应用断点.
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
import com.itsaky.androidide.utils.FlashbarUtilsKt;
import java.util.ArrayList;
import java.util.List;

/**
 * 输入行号表达式 -> 解析为行号列表 -> 选择断点类型 -> 批量应用.
 *
 * <p>支持的语法见类头注释.
 */
public class AddBreakpointByLineDialog extends DialogFragment {

    private static final String TAG = "AddBpByLine";
    private static final String ARG_FILE = "file";

    /** 中文逗号 (U+FF0C). */
    private static final String CN_COMMA = "，";
    /** 英文逗号. */
    private static final String EN_COMMA = ",";
    /** 范围分隔符 (~). */
    private static final String RANGE_SEP = "~";

    @Nullable private String filePath;

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

        // 语法说明
        TextView desc = new TextView(ctx);
        desc.setText(R.string.debugger_add_bp_by_line_desc);
        desc.setTextSize(11f);
        desc.setTextColor(0xFF666666);
        android.widget.LinearLayout.LayoutParams descLp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(ctx, 8);
        root.addView(desc, descLp);

        // 输入框 —— 不用 TYPE_CLASS_NUMBER, 因为要支持 ~ 和 , 字符.
        // 用 TYPE_CLASS_TEXT + TYPE_NUMBER_VARIATION_NORMAL 让数字键盘优先
        // 但仍允许输入符号.
        TextInputLayout inputLayout = new TextInputLayout(ctx);
        inputLayout.setHint(getString(R.string.debugger_add_bp_by_line_hint));
        TextInputEditText input = new TextInputEditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_NUMBER_VARIATION_NORMAL);
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
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(ctx, R.string.debugger_add_bp_by_line_no_file,
                    Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        CharSequence raw = input.getText();
        String expr = raw == null ? "" : raw.toString().trim();
        if (expr.isEmpty()) {
            inputLayout.setError(getString(R.string.debugger_add_bp_by_line_invalid));
            return;
        }

        // 解析表达式 -> 行号列表
        ParseResult result = parseExpression(expr);
        if (result.lines == null || result.lines.isEmpty()) {
            inputLayout.setError(result.error != null
                    ? result.error
                    : getString(R.string.debugger_add_bp_by_line_invalid));
            return;
        }

        final String file = filePath;
        final List<Integer> lines = result.lines;
        dismiss();
        showBreakpointTypePicker(file, lines);
    }

    /**
     * 解析行号表达式.
     *
     * <p>支持三种形式 (不能混用):
     * <ul>
     *   <li>单个: {@code 5}</li>
     *   <li>范围: {@code 5~100} (含端点)</li>
     *   <li>列表: {@code 1,2,3} 或 {@code 1，3，8} (英文 / 中文逗号均可,
     *       必须升序)</li>
     * </ul>
     *
     * @return 解析结果. {@link ParseResult#lines} 为 null 表示解析失败,
     *         {@link ParseResult#error} 含失败原因.
     */
    @NonNull
    private ParseResult parseExpression(@NonNull String expr) {
        // 统一 trim, 去除空白
        String s = expr.trim();

        // 中文逗号统一替换为英文逗号, 简化后续处理
        boolean hasCnComma = s.contains(CN_COMMA);
        boolean hasEnComma = s.contains(EN_COMMA);
        boolean hasRange = s.contains(RANGE_SEP);

        // 列表模式 (含逗号)
        if (hasCnComma || hasEnComma) {
            // 不允许列表和范围混用
            if (hasRange) {
                return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
            }
            // 统一用英文逗号分割
            String normalized = s.replace(CN_COMMA, EN_COMMA);
            String[] parts = normalized.split(EN_COMMA);
            List<Integer> lines = new ArrayList<>();
            int prev = 0; // 列表必须严格升序, 起始 prev=0 保证第一个数 >= 1 即可
            for (String part : parts) {
                String t = part.trim();
                if (t.isEmpty()) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
                }
                int n;
                try {
                    n = Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
                }
                if (n < 1) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
                }
                // 列表必须严格升序: 不允许 1,3,2
                if (n <= prev) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_desc_order));
                }
                lines.add(n);
                prev = n;
            }
            return ParseResult.ok(lines);
        }

        // 范围模式 (含 ~)
        if (hasRange) {
            String[] parts = s.split(RANGE_SEP);
            if (parts.length != 2) {
                return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
            }
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start < 1 || end < 1) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
                }
                // 范围终止值必须大于起始值: 不允许 100~5
                if (end <= start) {
                    return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_desc_range));
                }
                List<Integer> lines = new ArrayList<>();
                for (int i = start; i <= end; i++) {
                    lines.add(i);
                }
                return ParseResult.ok(lines);
            } catch (NumberFormatException e) {
                return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
            }
        }

        // 单个行号
        try {
            int n = Integer.parseInt(s);
            if (n < 1) {
                return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
            }
            List<Integer> lines = new ArrayList<>();
            lines.add(n);
            return ParseResult.ok(lines);
        } catch (NumberFormatException e) {
            return ParseResult.fail(getString(R.string.debugger_add_bp_by_line_invalid));
        }
    }

    /**
     * 弹出断点类型选择弹窗, 用户选择后批量应用.
     *
     * <p>选择「普通行断点」时, 对所有行号逐一 {@link BreakpointManager#toggle}
     * (已有断点的行不会重复添加). 选择其他类型时, 逐行打开
     * {@link BreakpointDetailDialog} (用户需逐行配置).
     */
    private void showBreakpointTypePicker(@NonNull String file, @NonNull List<Integer> lines) {
        FragmentActivity activity = (FragmentActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        // 取第一个行号作为 BreakpointTypePickerDialog 的定位行 (它只用于显示位置信息)
        int firstLine = lines.get(0);
        BreakpointTypePickerDialog.show(activity, file, firstLine, 0f, 0f,
                (entry, f, l, x, y) -> {
                    // 普通行断点: 批量 toggle
                    if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.GUTTER_LINE_CLICK) {
                        BreakpointManager bm = BreakpointManager.getInstance();
                        int applied = 0;
                        for (int line : lines) {
                            // toggle 会对已存在的断点移除, 这里只想要"添加"语义:
                            // 如果该行已有断点, 不重复添加 (跳过).
                            if (bm.findAt(f, line) == null) {
                                bm.toggle(f, line);
                                applied++;
                            }
                        }
                        // flash 提示应用了多少个
                        try {
                            FlashbarUtilsKt.flashSuccess(activity.getString(
                                    R.string.debugger_add_bp_by_line_applied, applied));
                        } catch (Throwable ignored) {
                        }
                        return;
                    }
                    // 其他类型: 逐行打开详情弹窗.
                    // 一次只处理第一行, 用户可以再次打开弹窗处理后续行.
                    BreakpointDetailDialog.showForNew(activity, f, firstLine, entry, null, null);
                });
    }

    private static int dp(@NonNull Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 表达式解析结果. */
    private static final class ParseResult {
        @Nullable final List<Integer> lines;
        @Nullable final String error;

        private ParseResult(@Nullable List<Integer> lines, @Nullable String error) {
            this.lines = lines;
            this.error = error;
        }

        static ParseResult ok(@NonNull List<Integer> lines) {
            return new ParseResult(lines, null);
        }

        static ParseResult fail(@NonNull String error) {
            return new ParseResult(null, error);
        }
    }
}
