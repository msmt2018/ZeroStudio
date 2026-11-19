package android.studio.zero.regular.expression.preview;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.rosemoe.oniguruma.OnigNative;
import android.studio.zero.regular.expression.preview.railroad.RailroadConverter;
import android.studio.zero.regular.expression.preview.railroad.RailroadDiagramView;
import android.studio.zero.regular.expression.preview.railroad.RailroadNode;
import android.studio.zero.regular.expression.preview.model.RegexAstNode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RegexPreviewFragment extends Fragment {
    private static final String TAG = "RegexPreview";

    private EditText etRegexInput;
    private EditText etTextInput;
    private TextView tvStatus;
    private TextView tvMatchResult;
    private RailroadDiagramView railroadView; // 自定义视图
    private Spinner spEncoding;
    private Button btnModifiers;

    private long onigRegexPtr = 0;
    private int currentEncodingIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isSelfUpdating = false;

    // Modifiers
    private boolean flagI = false, flagG = true, flagM = false, flagS = false;
    private boolean flagX = false; 

    private static final int ONIG_OPTION_NONE = 0;
    private static final int ONIG_OPTION_IGNORECASE = 1;
    private static final int ONIG_OPTION_EXTEND = (ONIG_OPTION_IGNORECASE << 1);
    private static final int ONIG_OPTION_MULTILINE = (ONIG_OPTION_EXTEND << 1);
    private static final int ONIG_OPTION_SINGLELINE = (ONIG_OPTION_MULTILINE << 1);

    private final Runnable updateRunnable = this::updateAll;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_regex_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupListeners();
        
        etRegexInput.setText("[a-z]([a-zA-Z0-9_.1-])+");
        etTextInput.setText("androidx-annotation = { module = \"androidx.annotation:annotation\", version.ref = \"androidxCore\" }");
        
        handler.postDelayed(updateRunnable, 300);
    }

    private void initViews(View view) {
        etRegexInput = view.findViewById(R.id.et_regex);
        etTextInput = view.findViewById(R.id.et_text);
        tvStatus = view.findViewById(R.id.tv_status);
        tvMatchResult = view.findViewById(R.id.tv_match_result);
        railroadView = view.findViewById(R.id.railroad_view); // 绑定新视图
        spEncoding = view.findViewById(R.id.sp_encoding);
        btnModifiers = view.findViewById(R.id.btn_modifiers);

        String[] encodings = new String[]{"UTF-8", "ASCII", "UTF-16LE", "UTF-16BE"};
        spEncoding.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, encodings));
    }

    private void setupListeners() {
        spEncoding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentEncodingIndex = position;
                triggerUpdate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnModifiers.setOnClickListener(v -> showModifiersPopup());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSelfUpdating) return;
                triggerUpdate();
            }
        };
        etRegexInput.addTextChangedListener(watcher);
        etTextInput.addTextChangedListener(watcher);
    }

    private void triggerUpdate() {
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, 300);
    }

    private void showModifiersPopup() {
        if (getContext() == null) return;
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_modifiers, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(16);
        popupWindow.setOutsideTouchable(true);

        CheckBox cbG = popupView.findViewById(R.id.cb_g); cbG.setChecked(flagG);
        CheckBox cbI = popupView.findViewById(R.id.cb_i); cbI.setChecked(flagI);
        CheckBox cbM = popupView.findViewById(R.id.cb_m); cbM.setChecked(flagM);
        CheckBox cbS = popupView.findViewById(R.id.cb_s); cbS.setChecked(flagS);
        CheckBox cbX = popupView.findViewById(R.id.cb_x); cbX.setChecked(flagX);

        View.OnClickListener listener = v -> {
            flagG = cbG.isChecked();
            flagI = cbI.isChecked();
            flagM = cbM.isChecked();
            flagS = cbS.isChecked();
            flagX = cbX.isChecked();
            btnModifiers.setText("Flags" + (flagI ? "(i)" : ""));
            triggerUpdate();
        };

        cbG.setOnClickListener(listener);
        cbI.setOnClickListener(listener);
        cbM.setOnClickListener(listener);
        cbS.setOnClickListener(listener);
        cbX.setOnClickListener(listener);

        popupWindow.showAsDropDown(btnModifiers, 0, 10);
    }

    private int getOnigOptions() {
        int options = ONIG_OPTION_NONE;
        if (flagI) options |= ONIG_OPTION_IGNORECASE;
        if (flagM) options |= ONIG_OPTION_MULTILINE;
        if (flagS) options |= ONIG_OPTION_SINGLELINE;
        if (flagX) options |= ONIG_OPTION_EXTEND;
        return options;
    }

    private void releaseCurrentRegex() {
        final long handle = onigRegexPtr;
        onigRegexPtr = 0L;
        if (handle != 0L) {
            OnigNative.releaseRegex(handle);
        }
    }

    private void updateAll() {
        if (!isAdded() || getContext() == null ||
                etRegexInput == null || etTextInput == null ||
                tvStatus == null || tvMatchResult == null) {
            return;
        }

        final String pattern = etRegexInput.getText().toString();
        releaseCurrentRegex();

        if (pattern.isEmpty()) {
            resetUI("Enter a regular expression");
            return;
        }

        final int options = getOnigOptions();

        // Compile a new independent native handle before using it.
        try {
            final long newHandle = OnigNative.newRegex(pattern, flagI);
            if (newHandle == 0L) {
                resetUI("Invalid Regex");
                tvStatus.setTextColor(Color.RED);
                return;
            }

            onigRegexPtr = newHandle;
            tvStatus.setText("Valid Regex");
            tvStatus.setTextColor(0xFF008800);
            highlightMatches(etTextInput.getText().toString(), options);
        } catch (RuntimeException e) {
            resetUI("Invalid Regex: " + e.getMessage());
            tvStatus.setTextColor(Color.RED);
            Log.e(TAG, "Unable to compile regular expression", e);
            return;
        }

        // 2. 更新铁路图
        try {
            RegexAstNode rootAst = RegexParser.parse(pattern, currentEncodingIndex, options);
            if (rootAst != null && rootAst.error == null) {
                // 转换 + 渲染
                RailroadNode root = RailroadConverter.convert(rootAst);
                railroadView.setRootNode(root);
            } else {
                if(railroadView != null) railroadView.setRootNode(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parser error", e);
        }
    }

    private void resetUI(String msg) {
        tvStatus.setText(msg);
        tvStatus.setTextColor(Color.GRAY);
        tvMatchResult.setText("");
        clearInputSpans();
        if (railroadView != null) railroadView.setRootNode(null);
    }

    private void clearInputSpans() {
        isSelfUpdating = true;
        Editable editable = etTextInput.getText();
        for (BackgroundColorSpan span : editable.getSpans(0, editable.length(), BackgroundColorSpan.class)) editable.removeSpan(span);
        for (ForegroundColorSpan span : editable.getSpans(0, editable.length(), ForegroundColorSpan.class)) editable.removeSpan(span);
        isSelfUpdating = false;
    }

    private void highlightMatches(String text, int options) {
        if (onigRegexPtr == 0 || text.isEmpty()) {
            tvMatchResult.setText("");
            return;
        }

        isSelfUpdating = true;
        try {
            Editable editable = etTextInput.getText();
            clearInputSpans();

            Charset charset = getCharset(currentEncodingIndex);
            byte[] textBytes = text.getBytes(charset);
            SpannableStringBuilder resultBuilder = new SpannableStringBuilder();
            
            int searchPos = 0;
            int limit = textBytes.length;
            int matchCount = 0;

            while (searchPos < limit && matchCount < 200) {
                int[] result = OnigNative.regexSearch(onigRegexPtr, 0, textBytes, searchPos, limit);
                
                if (result != null && result.length >= 2) {
                    int startB = result[0];
                    int endB = result[1];
                    if (endB <= startB) {
                        if (endB < limit) searchPos = endB + 1; else break;
                        continue;
                    }

                    int startC = getCharIndex(text, startB, charset);
                    int endC = getCharIndex(text, endB, charset);

                    if (startC >= 0 && endC <= text.length()) {
                        editable.setSpan(new BackgroundColorSpan(0x402196F3), startC, endC, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        editable.setSpan(new ForegroundColorSpan(0xFF1565C0), startC, endC, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        
                        if (resultBuilder.length() > 0) resultBuilder.append("  ");
                        String matchStr = text.substring(startC, endC).replace("\n", "↵");
                        resultBuilder.append(matchStr);
                        resultBuilder.setSpan(new BackgroundColorSpan(0xFFE8F5E9), resultBuilder.length() - matchStr.length(), resultBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        matchCount++;
                    }
                    searchPos = endB;
                    if (!flagG) break;
                } else {
                    break;
                }
            }
            if (matchCount == 0) tvMatchResult.setText("No matches found.");
            else tvMatchResult.setText(resultBuilder);
        } finally {
            isSelfUpdating = false;
        }
    }

    private Charset getCharset(int index) {
        switch (index) {
            case 1: return StandardCharsets.US_ASCII;
            case 2: return StandardCharsets.UTF_16LE;
            case 3: return StandardCharsets.UTF_16BE;
            default: return StandardCharsets.UTF_8;
        }
    }

    private int getCharIndex(String text, int byteIndex, Charset charset) {
        if (byteIndex == 0) return 0;
        try {
            return new String(text.getBytes(charset), 0, byteIndex, charset).length();
        } catch (Exception e) { return 0; }
    }

    @Override
    public void onDestroyView() {
        // A delayed update must never outlive the view whose widgets it uses.
        handler.removeCallbacks(updateRunnable);
        releaseCurrentRegex();

        etRegexInput = null;
        etTextInput = null;
        tvStatus = null;
        tvMatchResult = null;
        railroadView = null;
        spEncoding = null;
        btnModifiers = null;

        super.onDestroyView();
    }
}