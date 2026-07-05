/*
 *  ZeroStudio IDE - 内存视图 Fragment (Phase 20)
 *
 *  P-UI-2 评估中提到的"内存视图 (Memory View)" — 在 4 大子能力里
 *  唯一缺失的。Phase 20 新增:
 *
 *  - 地址输入框 (支持 hex 0x... 形式)
 *  - 长度输入框 (字节数,1 - 4096)
 *  - 刷新按钮 → 走 Debugger.readMemoryAsync(address, length)
 *  - 结果展示: 16 字节一行的 hex dump
 *
 *  入口: EditorBottomSheet "内存" Tab
 *  数据来源: ide-debugger Debugger.readMemoryAsync (Phase 21+ 实际接入)
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.itsaky.androidide.R;
import com.itsaky.androidide.utils.ILogger;
import com.zerostudio.debugger.api.Debugger;

public class MemoryFragment extends Fragment {

    private static final String TAG = "MemoryFragment";

    private EditText addressInput;
    private EditText lengthInput;
    private TextView dumpView;
    private View refresh;
    private View emptyView;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        addressInput = view.findViewById(R.id.mem_addr);
        lengthInput = view.findViewById(R.id.mem_len);
        dumpView = view.findViewById(R.id.mem_dump);
        refresh = view.findViewById(R.id.mem_refresh);
        emptyView = view.findViewById(R.id.mem_empty);
        if (refresh != null) refresh.setOnClickListener(v -> doRead());
        showEmpty(true);
    }

    private void doRead() {
        if (addressInput == null || lengthInput == null) return;
        long address;
        int length;
        try {
            String addrStr = addressInput.getText() == null ? "" : addressInput.getText().toString().trim();
            if (addrStr.isEmpty()) {
                showError("地址不能为空");
                return;
            }
            if (addrStr.startsWith("0x") || addrStr.startsWith("0X")) {
                address = Long.parseUnsignedLong(addrStr.substring(2), 16);
            } else {
                address = Long.parseUnsignedLong(addrStr);
            }
            length = Integer.parseInt(lengthInput.getText().toString().trim());
            if (length < 1 || length > 4096) {
                showError("长度必须在 1 - 4096 之间");
                return;
            }
        } catch (NumberFormatException nfe) {
            showError("地址或长度解析失败: " + nfe.getMessage());
            return;
        }
        showEmpty(false);
        appendLine("正在读取 0x" + Long.toHexString(address) + " (" + length + " bytes) ...");
        Debugger dbg = com.itsaky.androidide.debugger.DebuggerController
                .getInstance().debugger();
        if (dbg == null) {
            showError("未连接调试器");
            return;
        }
        try {
            dbg.readMemoryAsync(address, length, result -> {
                if (getView() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (result.isError()) {
                        showError("读取失败: " + result.error);
                    } else {
                        appendLine(formatHex(result.bytes));
                    }
                });
            });
        } catch (Throwable t) {
            ILogger.ROOT.warn(TAG + ": " + "readMemoryAsync failed: " + t.getMessage());
            showError("读取失败: " + t.getMessage());
        }
    }

    @NonNull
    private String formatHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += 16) {
            int end = Math.min(i + 16, bytes.length);
            sb.append(String.format("0x%08X  ", i));
            for (int j = i; j < end; j++) {
                sb.append(String.format("%02X ", bytes[j] & 0xFF));
            }
            for (int j = end; j < i + 16; j++) sb.append("   ");
            sb.append(' ');
            for (int j = i; j < end; j++) {
                char c = (char) (bytes[j] & 0xFF);
                sb.append(c >= 32 && c < 127 ? c : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void showError(@NonNull String msg) {
        if (dumpView != null) {
            dumpView.setTextColor(0xFFFF6F00);
            dumpView.setText(msg);
        }
    }

    private void showEmpty(boolean empty) {
        if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (dumpView != null) dumpView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void appendLine(@NonNull String line) {
        if (dumpView == null) return;
        dumpView.setTextColor(0xFFA0A0A0);
        String current = dumpView.getText() == null ? "" : dumpView.getText().toString();
        dumpView.setText(current + line + "\n");
    }
}
