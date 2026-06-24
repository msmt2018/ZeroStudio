/*
 *  ZeroStudio IDE - 断点条件/日志点配置对话框 (Phase E2)
 *
 *  取代原 BreakpointListFragment 内的简易 AlertDialog:
 *    - 顶部显示 file:line
 *    - 类型切换 RadioGroup: 普通 / 条件 / 日志点
 *    - 条件断点: 多行等宽输入框 + 实时校验
 *    - 日志点: 单行输入框 + 常用模板 chip
 *    - 命中次数: 模式 spinner + 数值输入 + 模式相关的 helper text
 *    - 实时错误提示
 *
 *  入口:
 *    BreakpointConditionDialog.show(fragmentManager, breakpointId)
 *  通过 BreakpointManager 应用变更,通知监听器刷新 UI。
 */

package com.itsaky.androidide.debugger;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import com.zerostudio.debugger.api.Breakpoint;
import java.io.File;

public class BreakpointConditionDialog extends DialogFragment {

    private static final String TAG = "BpConditionDialog";
    private static final String ARG_BREAKPOINT_ID = "bp_id";

    /**
     * Show the dialog for editing the given breakpoint. We can't use the
     * name `show` because [androidx.fragment.app.DialogFragment] exposes
     * its own non-static `show(FragmentManager, String)` and Java won't
     * let a static method override a non-static one with the same
     * signature.
     */
    public static void showDialog(@NonNull FragmentManager fm, @NonNull String breakpointId) {
        BreakpointConditionDialog d = new BreakpointConditionDialog();
        Bundle b = new Bundle();
        b.putString(ARG_BREAKPOINT_ID, breakpointId);
        d.setArguments(b);
        d.show(fm, TAG);
    }

    @Nullable private IdeBreakpoint bp;
    @Nullable private AlertDialog dialog;

    // views
    private TextView locationView;
    private RadioGroup typeGroup;
    private View conditionBox;
    private View logpointBox;
    private TextInputLayout conditionLayout;
    private TextInputEditText conditionInput;
    private TextInputEditText logInput;
    private ChipGroup logTemplates;
    private Spinner hitCountModeSpinner;
    private TextInputLayout hitCountLayout;
    private TextInputEditText hitCountInput;
    private TextView hitCountHelper;
    private TextView validation;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String id = getArguments() != null ? getArguments().getString(ARG_BREAKPOINT_ID) : null;
        bp = id == null ? null : BreakpointManager.getInstance().findById(id);
        if (bp == null) {
            // 无效 ID,直接关闭
            return new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.debugger_action_bp_delete)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(R.layout.dialog_breakpoint_condition, null, false);
        bindViews(view);
        populateFrom(bp);
        wireListeners();

        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.debugger_bcd_title)
                .setView(view)
                .setPositiveButton(R.string.debugger_bcd_btn_save, null /* overridden below */)
                .setNegativeButton(R.string.debugger_bcd_btn_cancel, null)
                .setNeutralButton(R.string.debugger_bcd_btn_remove, (d, w) -> {
                    if (bp != null) BreakpointManager.getInstance().remove(bp.id);
                })
                .create();
        // Override the positive button click after the dialog is built so
        // we can keep the dialog open when validation fails. The default
        // listener auto-dismisses the dialog.
        dialog.setOnShowListener(d -> {
            if (dialog == null) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        if (applyChanges()) {
                            dialog.dismiss();
                        }
                    });
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // 默认聚焦
        if (conditionInput != null && bp != null
                && bp.condition != null && !bp.condition.isEmpty()) {
            conditionInput.requestFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dialog = null;
    }

    private void bindViews(@NonNull View root) {
        locationView = root.findViewById(R.id.bcd_location);
        typeGroup = root.findViewById(R.id.bcd_type);
        conditionBox = root.findViewById(R.id.bcd_condition_box);
        logpointBox = root.findViewById(R.id.bcd_logpoint_box);
        conditionLayout = root.findViewById(R.id.bcd_condition_input_layout);
        conditionInput = root.findViewById(R.id.bcd_condition_input);
        logInput = root.findViewById(R.id.bcd_log_input);
        logTemplates = root.findViewById(R.id.bcd_log_templates);
        hitCountModeSpinner = root.findViewById(R.id.bcd_hit_count_mode);
        hitCountLayout = root.findViewById(R.id.bcd_hit_count_input_layout);
        hitCountInput = root.findViewById(R.id.bcd_hit_count_input);
        hitCountHelper = root.findViewById(R.id.bcd_hit_count_helper);
        validation = root.findViewById(R.id.bcd_validation);

        // spinner entries
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.debugger_bcd_hit_count_modes,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hitCountModeSpinner.setAdapter(adapter);

        // log templates
        String[] templates = getResources().getStringArray(
                R.array.debugger_bcd_log_templates);
        for (String tpl : templates) {
            Chip chip = new Chip(requireContext());
            chip.setText(tpl);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                if (logInput == null) return;
                logInput.setText(tpl);
                logInput.setSelection(tpl.length());
            });
            logTemplates.addView(chip);
        }
    }

    private void populateFrom(@NonNull IdeBreakpoint bp) {
        // 位置
        String name = new File(bp.file).getName();
        locationView.setText(name + " : " + bp.line);

        // 类型
        boolean isLog = bp.logMessage != null && !bp.logMessage.isEmpty();
        boolean isCond = bp.condition != null && !bp.condition.isEmpty();
        if (isLog) {
            typeGroup.check(R.id.bcd_type_logpoint);
        } else if (isCond) {
            typeGroup.check(R.id.bcd_type_condition);
        } else {
            typeGroup.check(R.id.bcd_type_normal);
        }

        // 条件 / 日志内容
        if (bp.condition != null) conditionInput.setText(bp.condition);
        if (bp.logMessage != null) logInput.setText(bp.logMessage);

        // 命中次数
        int spinnerPos = 0; // ALWAYS
        switch (bp.hitCountMode) {
            case EQUAL: spinnerPos = 1; break;
            case GREATER_THAN: spinnerPos = 2; break;
            case MULTIPLE: spinnerPos = 3; break;
            default: spinnerPos = 0; break;
        }
        hitCountModeSpinner.setSelection(spinnerPos);
        if (bp.hitCount > 0) hitCountInput.setText(String.valueOf(bp.hitCount));

        updateVisibility();
        updateHitCountHelper();
        validate();
    }

    private void wireListeners() {
        typeGroup.setOnCheckedChangeListener((g, id) -> {
            updateVisibility();
            validate();
        });
        hitCountModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                updateHitCountHelper();
                validate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        conditionInput.addTextChangedListener(new SimpleTextWatcher(() -> validate()));
        logInput.addTextChangedListener(new SimpleTextWatcher(() -> validate()));
        hitCountInput.addTextChangedListener(new SimpleTextWatcher(() -> validate()));
    }

    private void updateVisibility() {
        int checked = typeGroup.getCheckedRadioButtonId();
        conditionBox.setVisibility(checked == R.id.bcd_type_condition
                ? View.VISIBLE : View.GONE);
        logpointBox.setVisibility(checked == R.id.bcd_type_logpoint
                ? View.VISIBLE : View.GONE);
    }

    private void updateHitCountHelper() {
        int pos = hitCountModeSpinner.getSelectedItemPosition();
        int resId;
        switch (pos) {
            case 1: resId = R.string.debugger_bcd_hit_count_helper_equal; break;
            case 2: resId = R.string.debugger_bcd_hit_count_helper_greater; break;
            case 3: resId = R.string.debugger_bcd_hit_count_helper_multiple; break;
            default: resId = R.string.debugger_bcd_hit_count_helper_always; break;
        }
        hitCountHelper.setText(resId);
        boolean needInput = pos != 0;
        hitCountLayout.setVisibility(needInput ? View.VISIBLE : View.GONE);
    }

    private @Nullable String validate() {
        if (bp == null) return null;
        int checked = typeGroup.getCheckedRadioButtonId();
        if (checked == R.id.bcd_type_condition) {
            String s = textOf(conditionInput);
            if (s == null || s.trim().isEmpty()) {
                showError(getString(R.string.debugger_bcd_validation_condition_empty));
                return "condition_empty";
            }
        }
        if (checked == R.id.bcd_type_logpoint) {
            String s = textOf(logInput);
            if (s == null || s.trim().isEmpty()) {
                showError(getString(R.string.debugger_bcd_validation_log_empty));
                return "log_empty";
            }
        }
        int modePos = hitCountModeSpinner.getSelectedItemPosition();
        if (modePos != 0) {
            String s = textOf(hitCountInput);
            if (s == null || s.trim().isEmpty()) {
                showError(getString(R.string.debugger_bcd_validation_count_invalid));
                return "count_empty";
            }
            try {
                int n = Integer.parseInt(s.trim());
                if (n < 1 || n > 999_999_999) {
                    showError(getString(R.string.debugger_bcd_validation_count_invalid));
                    return "count_range";
                }
            } catch (NumberFormatException nfe) {
                showError(getString(R.string.debugger_bcd_validation_count_invalid));
                return "count_nan";
            }
        }
        hideError();
        return null;
    }

    private void showError(@NonNull String msg) {
        if (validation == null) return;
        validation.setText(msg);
        validation.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (validation == null) return;
        validation.setVisibility(View.GONE);
    }

    private boolean applyChanges() {
        if (bp == null) return false;
        // 二次校验:与 onCreateDialog 中的 listener 路径相同,防止键盘
        // 在最后一次输入后未来得及触发 TextWatcher 时的边界情况。
        String err = validate();
        if (err != null) return false;
        int checked = typeGroup.getCheckedRadioButtonId();
        BreakpointManager mgr = BreakpointManager.getInstance();
        // 1. 条件 / 日志消息
        if (checked == R.id.bcd_type_condition) {
            mgr.setCondition(bp.id, textOf(conditionInput));
            mgr.setLogMessage(bp.id, null);
        } else if (checked == R.id.bcd_type_logpoint) {
            mgr.setLogMessage(bp.id, textOf(logInput));
            mgr.setCondition(bp.id, null);
        } else {
            mgr.setCondition(bp.id, null);
            mgr.setLogMessage(bp.id, null);
        }
        // 2. 命中次数
        int pos = hitCountModeSpinner.getSelectedItemPosition();
        Breakpoint.HitCountMode mode;
        int count;
        switch (pos) {
            case 1: mode = Breakpoint.HitCountMode.EQUAL; count = parseInt(hitCountInput); break;
            case 2: mode = Breakpoint.HitCountMode.GREATER_THAN; count = parseInt(hitCountInput); break;
            case 3: mode = Breakpoint.HitCountMode.MULTIPLE; count = parseInt(hitCountInput); break;
            default: mode = Breakpoint.HitCountMode.ALWAYS; count = 0; break;
        }
        mgr.setHitCount(bp.id, mode, count);
        // PR-D5: 触觉反馈告知"配置已保存"
        DebuggerHaptics.strong(requireActivity());
        return true;
    }

    private static int parseInt(@Nullable TextInputEditText e) {
        String s = textOf(e);
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException nfe) { return 0; }
    }

    @Nullable
    private static String textOf(@Nullable TextInputEditText e) {
        if (e == null || e.getText() == null) return null;
        return e.getText().toString();
    }

    /** 轻量 TextWatcher,把 onTextChanged 转换为 Runnable。 */
    private static final class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleTextWatcher(@NonNull Runnable onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }
}
