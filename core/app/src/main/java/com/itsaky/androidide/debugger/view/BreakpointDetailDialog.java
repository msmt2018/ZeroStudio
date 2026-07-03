/*
 *  ZeroStudio IDE - 断点详细设置弹窗 (Phase 20 重构 — 高斯模糊磨砂)
 *
 *  替代原 BreakpointConditionDialog (只支持 LINE/CONDITION/LOG 三种 + 1 个 advanced Kind spinner)。
 *  修复合并的 elementName 同步 bug + applyAdvancedOptions 顺序错位。
 *
 *  行为:
 *    - 接收一个 BreakpointTypeCatalog.Entry (13 种之一)
 *    - 接收已存在的 IdeBreakpoint (可能为 null —— 新建)
 *    - 动态渲染 4 大类不同 UI:
 *        GUTTER_LINE   → 位置 + 启用
 *        GUTTER_METHOD → 位置 + entry/exit
 *        GUTTER_COND   → 位置 + 条件 + hit count + suspend
 *        GUTTER_LOG    → 位置 + 日志 + 模板
 *        GUTTER_INLINE → 位置 + 子表达式 offset
 *        VAR_WATCH_*   → 变量路径 + access/modification
 *        VAR_INSTANCE  → instance id + 绑定行
 *        WINDOW_EXC    → 异常类名 + caught/uncaught
 *        WINDOW_SYM    → 函数名 + 模块
 *        WINDOW_DEP    → 选主断点
 *        BROWSER_*     → 选择器 / URL / 事件名
 *
 *    - 应用 → 通过 BreakpointManager 统一入口写回,UI 自动刷新
 *    - 全部 4 类断点共用同一个"详细设置" UI (UI 根据 Entry 动态切换)
 */

package com.itsaky.androidide.debugger.view;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.DebuggerHaptics;
import com.itsaky.androidide.debugger.model.BreakpointManager;
import com.itsaky.androidide.debugger.model.BreakpointTypeCatalog;
import com.itsaky.androidide.debugger.model.IdeBreakpoint;
import com.itsaky.androidide.utils.ILogger;
import com.zerostudio.debugger.api.Breakpoint;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BreakpointDetailDialog extends DialogFragment {

    private static final String TAG = "BpDetailDialog";

    public interface OnAppliedListener {
        void onApplied(@NonNull IdeBreakpoint bp);
    }

    private static final String ARG_FILE = "file";
    private static final String ARG_LINE = "line";
    private static final String ARG_BP_ID = "bp_id";
    private static final String ARG_ENTRY = "entry";
    private static final String ARG_ELEMENT = "element";

    @Nullable private String file;
    private int line = -1;
    @Nullable private String existingBpId;
    @Nullable private BreakpointTypeCatalog.Entry entry;
    @Nullable private String presetElement;
    @Nullable private OnAppliedListener pendingOnApplied;

    @Nullable private IdeBreakpoint bp;

    // 通用视图
    @Nullable private TextView locationView;
    @Nullable private MaterialSwitch enabledSwitch;
    @Nullable private LinearLayout contentContainer;

    // 类型相关视图
    @Nullable private TextInputLayout elementLayout;
    @Nullable private TextInputEditText elementInput;
    @Nullable private TextInputLayout conditionLayout;
    @Nullable private TextInputEditText conditionInput;
    @Nullable private TextInputLayout logLayout;
    @Nullable private TextInputEditText logInput;
    @Nullable private Spinner hitCountModeSpinner;
    @Nullable private TextInputLayout hitCountLayout;
    @Nullable private TextInputEditText hitCountInput;
    @Nullable private CheckBox methodEntryCheck;
    @Nullable private CheckBox methodExitCheck;
    @Nullable private CheckBox watchAccessCheck;
    @Nullable private CheckBox watchModificationCheck;
    @Nullable private CheckBox exceptionCaughtCheck;
    @Nullable private CheckBox exceptionUncaughtCheck;
    @Nullable private CheckBox temporaryCheck;
    @Nullable private Spinner dependentSpinner;
    @Nullable private TextView validation;
    @Nullable private LinearLayout inlineOffsetBox;
    @Nullable private TextInputEditText inlineOffsetInput;
    @Nullable private LinearLayout instanceBox;
    @Nullable private TextInputEditText instanceIdInput;

    private final List<IdeBreakpoint> dependentChoices = new ArrayList<>();

    public static void showForNew(
            @NonNull FragmentActivity activity,
            @NonNull String file,
            int line,
            @NonNull BreakpointTypeCatalog.Entry entry,
            @Nullable String presetElement,
            @Nullable OnAppliedListener onApplied) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            BreakpointDetailDialog d = new BreakpointDetailDialog();
            Bundle b = new Bundle();
            b.putString(ARG_FILE, file);
            b.putInt(ARG_LINE, line);
            b.putString(ARG_BP_ID, null);
            b.putString(ARG_ENTRY, entry.entryPoint.name());
            b.putString(ARG_ELEMENT, presetElement);
            d.setArguments(b);
            d.pendingOnApplied = onApplied;
            d.show(activity.getSupportFragmentManager(), TAG);
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "showForNew failed: " + t.getMessage());
        }
    }

    public static void showForExisting(
            @NonNull FragmentActivity activity,
            @NonNull String bpId,
            @NonNull OnAppliedListener onApplied) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            BreakpointDetailDialog d = new BreakpointDetailDialog();
            Bundle b = new Bundle();
            b.putString(ARG_BP_ID, bpId);
            d.setArguments(b);
            d.pendingOnApplied = onApplied;
            d.show(activity.getSupportFragmentManager(), TAG);
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "showForExisting failed: " + t.getMessage());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle b = getArguments();
        if (b != null) {
            file = b.getString(ARG_FILE);
            line = b.getInt(ARG_LINE, -1);
            existingBpId = b.getString(ARG_BP_ID);
            String ep = b.getString(ARG_ENTRY);
            presetElement = b.getString(ARG_ELEMENT);
            if (ep != null) {
                try { entry = BreakpointTypeCatalog.fromEntryPoint(
                        BreakpointTypeCatalog.EntryPoint.valueOf(ep)); }
                catch (Throwable ignored) { entry = null; }
            }
        }
        if (existingBpId != null) {
            bp = BreakpointManager.getInstance().findById(existingBpId);
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            try { w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND); } catch (Throwable ignored) {}
            w.setDimAmount(0.4f);
        }
        dialog.setCanceledOnTouchOutside(true);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View root = inflater.inflate(R.layout.dialog_breakpoint_detail, null, false);
        dialog.setContentView(root);
        bindCommon(root);
        renderForEntry(root);
        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void bindCommon(@NonNull View root) {
        locationView = root.findViewById(R.id.bpd_location);
        enabledSwitch = root.findViewById(R.id.bpd_enabled);
        contentContainer = root.findViewById(R.id.bpd_content);

        // 位置
        if (locationView != null) {
            String loc;
            if (bp != null) {
                loc = new File(bp.file).getName() + " : " + bp.line;
            } else if (file != null) {
                loc = new File(file).getName() + " : " + line;
            } else {
                loc = "";
            }
            locationView.setText(loc);
        }
        if (enabledSwitch != null) {
            enabledSwitch.setChecked(bp == null || bp.state != IdeBreakpoint.State.DISABLED);
        }

        // 取消 / 保存按钮
        root.findViewById(R.id.bpd_btn_cancel).setOnClickListener(v -> dismissAllowingStateLoss());
        root.findViewById(R.id.bpd_btn_save).setOnClickListener(v -> onSaveClicked());
    }

    private void renderForEntry(@NonNull View root) {
        if (entry == null || contentContainer == null) return;
        contentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        // 通用: element
        if (entry.needsElement) {
            View v = inflater.inflate(R.layout.part_bp_field_text, contentContainer, false);
            ((TextView) v.findViewById(R.id.bpft_label))
                    .setText(elementHintFor(entry));
            elementLayout = v.findViewById(R.id.bpft_layout);
            elementInput = v.findViewById(R.id.bpft_input);
            elementLayout.setHint(elementHintFor(entry));
            if (presetElement != null && (bp == null || bp.elementName == null)) {
                elementInput.setText(presetElement);
            } else if (bp != null && bp.elementName != null) {
                elementInput.setText(bp.elementName);
            }
            contentContainer.addView(v);
        }
        if (entry.needsCondition) {
            View v = inflater.inflate(R.layout.part_bp_field_text, contentContainer, false);
            ((TextView) v.findViewById(R.id.bpft_label))
                    .setText(R.string.debugger_bcd_condition_label);
            conditionLayout = v.findViewById(R.id.bpft_layout);
            conditionLayout.setHint(R.string.debugger_bcd_condition_hint);
            conditionInput = v.findViewById(R.id.bpft_input);
            conditionInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            conditionInput.setMinLines(2);
            conditionInput.setMaxLines(4);
            if (bp != null && bp.condition != null) {
                conditionInput.setText(bp.condition);
            }
            contentContainer.addView(v);
        }
        if (entry.needsLogMessage) {
            View v = inflater.inflate(R.layout.part_bp_field_text, contentContainer, false);
            ((TextView) v.findViewById(R.id.bpft_label))
                    .setText(R.string.debugger_bcd_log_label);
            logLayout = v.findViewById(R.id.bpft_layout);
            logLayout.setHint(R.string.debugger_bcd_log_hint);
            logInput = v.findViewById(R.id.bpft_input);
            logInput.setSingleLine(true);
            if (bp != null && bp.logMessage != null) {
                logInput.setText(bp.logMessage);
            }
            contentContainer.addView(v);
        }
        // 命中次数
        if (entry.needsHitCount) {
            View v = inflater.inflate(R.layout.part_bp_hit_count, contentContainer, false);
            hitCountModeSpinner = v.findViewById(R.id.bphc_mode);
            hitCountLayout = v.findViewById(R.id.bphc_input_layout);
            hitCountInput = v.findViewById(R.id.bphc_input);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    requireContext(),
                    R.array.debugger_bcd_hit_count_modes,
                    android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            hitCountModeSpinner.setAdapter(adapter);
            int pos = 0;
            if (bp != null) {
                switch (bp.hitCountMode) {
                    case EQUAL: pos = 1; break;
                    case GREATER_THAN: pos = 2; break;
                    case MULTIPLE: pos = 3; break;
                    default: pos = 0; break;
                }
                if (bp.hitCount > 0) hitCountInput.setText(String.valueOf(bp.hitCount));
            }
            hitCountModeSpinner.setSelection(pos);
            hitCountModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                    hitCountLayout.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
            hitCountLayout.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
            contentContainer.addView(v);
        }
        // Method entry/exit
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.GUTTER_METHOD_DECL_CLICK
                || entry.entryPoint == BreakpointTypeCatalog.EntryPoint.WINDOW_SYMBOLIC_ADD) {
            View v = inflater.inflate(R.layout.part_bp_checkboxes, contentContainer, false);
            methodEntryCheck = v.findViewById(R.id.bpcb_entry);
            methodExitCheck = v.findViewById(R.id.bpcb_exit);
            methodEntryCheck.setVisibility(View.VISIBLE);
            methodExitCheck.setVisibility(View.VISIBLE);
            methodEntryCheck.setChecked(bp == null || entry.defaultMethodEntry || (bp != null && bp.methodEntry));
            methodExitCheck.setChecked(bp != null && bp.methodExit);
            contentContainer.addView(v);
        }
        // Watch access / modification
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.VAR_FIELD_RIGHT_CLICK_ACC
                || entry.entryPoint == BreakpointTypeCatalog.EntryPoint.VAR_FIELD_RIGHT_CLICK_MOD) {
            View v = inflater.inflate(R.layout.part_bp_checkboxes, contentContainer, false);
            watchAccessCheck = v.findViewById(R.id.bpcb_access);
            watchModificationCheck = v.findViewById(R.id.bpcb_modification);
            watchAccessCheck.setVisibility(View.VISIBLE);
            watchModificationCheck.setVisibility(View.VISIBLE);
            if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.VAR_FIELD_RIGHT_CLICK_ACC) {
                watchAccessCheck.setChecked(true);
                watchModificationCheck.setChecked(false);
            } else {
                watchAccessCheck.setChecked(false);
                watchModificationCheck.setChecked(true);
            }
            if (bp != null) {
                watchAccessCheck.setChecked(bp.watchAccess);
                watchModificationCheck.setChecked(bp.watchModification);
            }
            contentContainer.addView(v);
        }
        // Exception caught / uncaught
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.WINDOW_EXCEPTION_ADD) {
            View v = inflater.inflate(R.layout.part_bp_checkboxes, contentContainer, false);
            exceptionCaughtCheck = v.findViewById(R.id.bpcb_caught);
            exceptionUncaughtCheck = v.findViewById(R.id.bpcb_uncaught);
            exceptionCaughtCheck.setVisibility(View.VISIBLE);
            exceptionUncaughtCheck.setVisibility(View.VISIBLE);
            exceptionCaughtCheck.setChecked(bp == null || bp.catchCaught);
            exceptionUncaughtCheck.setChecked(bp == null || bp.catchUncaught);
            contentContainer.addView(v);
        }
        // 依赖断点下拉
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.WINDOW_DEPENDENT_TOGGLE) {
            View v = inflater.inflate(R.layout.part_bp_dependent, contentContainer, false);
            dependentSpinner = v.findViewById(R.id.bpd_dep_spinner);
            rebuildDependentChoices();
            if (bp != null && bp.dependsOnBreakpointId != null) {
                for (int i = 0; i < dependentChoices.size(); i++) {
                    if (bp.dependsOnBreakpointId.equals(dependentChoices.get(i).id)) {
                        dependentSpinner.setSelection(i + 1);
                        break;
                    }
                }
            }
            contentContainer.addView(v);
        }
        // Inline offset
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.GUTTER_INLINE_CLICK) {
            View v = inflater.inflate(R.layout.part_bp_field_text, contentContainer, false);
            ((TextView) v.findViewById(R.id.bpft_label))
                    .setText(R.string.debugger_bpd_inline_offset);
            inlineOffsetBox = (LinearLayout) v;
            elementLayout = v.findViewById(R.id.bpft_layout);
            elementLayout.setHint(R.string.debugger_bpd_inline_offset_hint);
            elementInput = v.findViewById(R.id.bpft_input);
            elementInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            if (bp != null && bp.elementName != null) elementInput.setText(bp.elementName);
            contentContainer.addView(v);
        }
        // Instance id
        if (entry.entryPoint == BreakpointTypeCatalog.EntryPoint.VAR_INSTANCE_RIGHT_CLICK) {
            View v = inflater.inflate(R.layout.part_bp_field_text, contentContainer, false);
            ((TextView) v.findViewById(R.id.bpft_label))
                    .setText(R.string.debugger_bpd_instance_id);
            instanceBox = (LinearLayout) v;
            elementLayout = v.findViewById(R.id.bpft_layout);
            elementLayout.setHint(R.string.debugger_bpd_instance_id_hint);
            elementInput = v.findViewById(R.id.bpft_input);
            elementInput.setSingleLine(true);
            if (bp != null && bp.elementName != null) elementInput.setText(bp.elementName);
            contentContainer.addView(v);
        }
        // 临时
        if (entry.defaultTemporary) {
            View v = inflater.inflate(R.layout.part_bp_checkboxes, contentContainer, false);
            temporaryCheck = v.findViewById(R.id.bpcb_temporary);
            temporaryCheck.setVisibility(View.VISIBLE);
            temporaryCheck.setChecked(true);
            if (bp != null) temporaryCheck.setChecked(bp.temporary);
            contentContainer.addView(v);
        }
        validation = root.findViewById(R.id.bpd_validation);
    }

    private int elementHintFor(@NonNull BreakpointTypeCatalog.Entry e) {
        switch (e.entryPoint) {
            case GUTTER_METHOD_DECL_CLICK: return R.string.debugger_bpd_method_name_hint;
            case GUTTER_INLINE_CLICK:      return R.string.debugger_bpd_inline_offset_hint;
            case VAR_FIELD_RIGHT_CLICK_ACC:
            case VAR_FIELD_RIGHT_CLICK_MOD: return R.string.debugger_bpd_field_name_hint;
            case VAR_INSTANCE_RIGHT_CLICK:  return R.string.debugger_bpd_instance_id_hint;
            case WINDOW_EXCEPTION_ADD:      return R.string.debugger_bpd_exception_class_hint;
            case WINDOW_SYMBOLIC_ADD:       return R.string.debugger_bpd_symbol_name_hint;
            case WINDOW_DEPENDENT_TOGGLE:   return R.string.debugger_bpd_dependent_hint;
            case BROWSER_DOM_ADD:           return R.string.debugger_bpd_dom_selector_hint;
            case BROWSER_XHR_ADD:           return R.string.debugger_bpd_xhr_url_hint;
            case BROWSER_EVENT_ADD:         return R.string.debugger_bpd_event_name_hint;
            default: return R.string.debugger_bpd_element_hint_generic;
        }
    }

    private void rebuildDependentChoices() {
        if (dependentSpinner == null) return;
        dependentChoices.clear();
        dependentChoices.addAll(BreakpointManager.getInstance().snapshot());
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.debugger_bpd_dependent_none));
        for (IdeBreakpoint other : dependentChoices) {
            labels.add(new File(other.file).getName() + ":" + other.line);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dependentSpinner.setAdapter(adapter);
    }

    private void onSaveClicked() {
        if (entry == null) { dismissAllowingStateLoss(); return; }
        if (!validate()) return;
        BreakpointManager mgr = BreakpointManager.getInstance();
        // 1. 创建或获取 IdeBreakpoint
        IdeBreakpoint target;
        if (bp != null) {
            target = bp;
        } else {
            if (file == null) { dismissAllowingStateLoss(); return; }
            target = new IdeBreakpoint(file, line);
            mgr.add(target);
        }
        // 2. 应用 element / condition / log / hit count / method / watch / exception / temporary / dependent
        String element = (elementInput != null && elementInput.getText() != null)
                ? elementInput.getText().toString().trim() : null;
        if (element != null && element.isEmpty()) element = null;

        String condition = (conditionInput != null && conditionInput.getText() != null)
                ? conditionInput.getText().toString().trim() : null;
        if (condition != null && condition.isEmpty()) condition = null;

        String log = (logInput != null && logInput.getText() != null)
                ? logInput.getText().toString().trim() : null;
        if (log != null && log.isEmpty()) log = null;

        int hitCount = 0;
        Breakpoint.HitCountMode mode = Breakpoint.HitCountMode.ALWAYS;
        if (hitCountModeSpinner != null) {
            int p = hitCountModeSpinner.getSelectedItemPosition();
            switch (p) {
                case 1: mode = Breakpoint.HitCountMode.EQUAL; break;
                case 2: mode = Breakpoint.HitCountMode.GREATER_THAN; break;
                case 3: mode = Breakpoint.HitCountMode.MULTIPLE; break;
                default: mode = Breakpoint.HitCountMode.ALWAYS; break;
            }
            if (hitCountInput != null && hitCountInput.getText() != null) {
                try { hitCount = Integer.parseInt(hitCountInput.getText().toString().trim()); }
                catch (Throwable ignored) {}
            }
        }

        boolean methodEntry = methodEntryCheck == null || methodEntryCheck.isChecked();
        boolean methodExit = methodExitCheck != null && methodExitCheck.isChecked();
        boolean watchAccess = watchAccessCheck != null && watchAccessCheck.isChecked();
        boolean watchMod = watchModificationCheck != null && watchModificationCheck.isChecked();
        boolean catchCaught = exceptionCaughtCheck == null || exceptionCaughtCheck.isChecked();
        boolean catchUncaught = exceptionUncaughtCheck == null || exceptionUncaughtCheck.isChecked();
        boolean temporary = temporaryCheck != null && temporaryCheck.isChecked();

        String depId = null;
        if (dependentSpinner != null) {
            int p = dependentSpinner.getSelectedItemPosition();
            if (p > 0 && p - 1 < dependentChoices.size()) {
                depId = dependentChoices.get(p - 1).id;
            }
        }

        // 3. 写入 (applyAdvancedOptions 内部一次提交,避免多次 reinstallOnDebugger 抖动)
        mgr.applyAdvancedOptions(target.id, entry.kind, temporary,
                watchAccess, watchMod,
                methodEntry, methodExit,
                catchCaught, catchUncaught,
                depId, element);
        if (entry.needsCondition) mgr.setCondition(target.id, condition);
        else mgr.setCondition(target.id, null);
        if (entry.needsLogMessage) mgr.setLogMessage(target.id, log);
        else mgr.setLogMessage(target.id, null);
        mgr.setHitCount(target.id, mode, hitCount);
        mgr.setEnabled(target.id, enabledSwitch != null && enabledSwitch.isChecked());
        // 4. UI 反馈
        try { DebuggerHaptics.strong(requireActivity()); } catch (Throwable ignored) {}
        if (pendingOnApplied != null) {
            try { pendingOnApplied.onApplied(target); } catch (Throwable ignored) {}
        }
        dismissAllowingStateLoss();
    }

    private boolean validate() {
        if (entry == null) return true;
        if (entry.needsElement && elementInput != null) {
            String s = elementInput.getText() == null ? "" : elementInput.getText().toString().trim();
            if (s.isEmpty()) {
                showValidation(getString(R.string.debugger_bpd_validation_element_empty));
                return false;
            }
        }
        if (entry.needsCondition && conditionInput != null) {
            String s = conditionInput.getText() == null ? "" : conditionInput.getText().toString().trim();
            if (s.isEmpty()) {
                showValidation(getString(R.string.debugger_bcd_validation_condition_empty));
                return false;
            }
        }
        if (entry.needsLogMessage && logInput != null) {
            String s = logInput.getText() == null ? "" : logInput.getText().toString().trim();
            if (s.isEmpty()) {
                showValidation(getString(R.string.debugger_bcd_validation_log_empty));
                return false;
            }
        }
        if (entry.needsHitCount && hitCountModeSpinner != null
                && hitCountModeSpinner.getSelectedItemPosition() != 0
                && hitCountInput != null) {
            String s = hitCountInput.getText() == null ? "" : hitCountInput.getText().toString().trim();
            if (s.isEmpty()) {
                showValidation(getString(R.string.debugger_bcd_validation_count_invalid));
                return false;
            }
            try {
                int n = Integer.parseInt(s);
                if (n < 1 || n > 999_999_999) {
                    showValidation(getString(R.string.debugger_bcd_validation_count_invalid));
                    return false;
                }
            } catch (NumberFormatException nfe) {
                showValidation(getString(R.string.debugger_bcd_validation_count_invalid));
                return false;
            }
        }
        hideValidation();
        return true;
    }

    private void showValidation(@NonNull String msg) {
        if (validation == null) return;
        validation.setText(msg);
        validation.setVisibility(View.VISIBLE);
    }

    private void hideValidation() {
        if (validation == null) return;
        validation.setVisibility(View.GONE);
    }
}
