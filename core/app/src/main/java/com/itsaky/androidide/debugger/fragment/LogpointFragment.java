/*
 *  ZeroStudio IDE - 日志点输出 Fragment (PR-6)
 *
 *  显示 LogStore 中累积的日志条目。来自 ide-debugger 的
 *  DebugEventBus 推送通过 DebuggerController 转写到 LogStore;
 *  本 Fragment 直接订阅 LogStore 来更新 RecyclerView。
 *
 *  - 顶部 "清空" 按钮清空 LogStore。
 *  - "自动滚动" CheckBox 决定新增条目时是否滚动到底。
 */

package com.itsaky.androidide.debugger.fragment;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.itsaky.androidide.R;
import com.itsaky.androidide.debugger.adapter.LogpointAdapter;
import com.itsaky.androidide.debugger.model.LogStore;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogpointFragment extends Fragment implements LogStore.Listener {

    private RecyclerView list;
    private View emptyView;
    private LogpointAdapter adapter;
    private CheckBox autoScroll;
    private CheckBox pauseBox;
    private Button exportBtn;

    // PR-D9.1: 导出用单线程 executor, 避免阻塞 UI 也不与 listener 派发线程争用。
    private static final ExecutorService EXPORT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LogStore-Export");
                t.setDaemon(true);
                return t;
            });

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logpoint, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.log_list);
        emptyView = view.findViewById(R.id.log_empty);
        autoScroll = view.findViewById(R.id.log_autoscroll);
        pauseBox = view.findViewById(R.id.log_pause);
        Button clear = view.findViewById(R.id.log_clear);
        exportBtn = view.findViewById(R.id.log_export);

        adapter = new LogpointAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        clear.setOnClickListener(v -> LogStore.getInstance().clear());
        exportBtn.setOnClickListener(v -> exportLogs());
        // PR-D9.2 (#45): 暂停/恢复 toggle 写入 LogStore, 立即生效。
        // CheckBox checked=true 表示暂停, 与 LogStore.enabled 取反对应。
        pauseBox.setOnCheckedChangeListener((btn, checked) ->
                LogStore.getInstance().setEnabled(!checked));

        LogStore.getInstance().addListener(this);
        // Restore previous entries
        adapter.submit(LogStore.getInstance().snapshot());
        updateEmpty();
    }

    @Override
    public void onDestroyView() {
        LogStore.getInstance().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onLogAppended(@NonNull LogStore.Entry entry) {
        if (!isAdded() || adapter == null) return;
        adapter.append(entry);
        updateEmpty();
        if (autoScroll != null && autoScroll.isChecked() && list != null) {
            list.post(() -> {
                if (list == null) return;
                int last = adapter.getItemCount() - 1;
                if (last >= 0) list.smoothScrollToPosition(last);
            });
        }
    }

    @Override
    public void onLogCleared() {
        if (!isAdded() || adapter == null) return;
        adapter.clear();
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = adapter == null || adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * PR-D9.1 (#41): 把当前 LogStore 快照导出到下载目录
     * {@code Download/zerostudio-logpoint-YYYYMMDD-HHmmss.txt}。
     * <p>
     * 导出走 {@link #EXPORT_EXECUTOR} 后台线程, 完成后用
     * {@link Toast} 在主线程报告结果。
     */
    private void exportLogs() {
        final LogStore store = LogStore.getInstance();
        if (store.size() == 0) {
            Toast.makeText(requireContext(),
                    R.string.debugger_log_export_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (exportBtn != null) exportBtn.setEnabled(false);
        EXPORT_EXECUTOR.submit(() -> {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
                    .format(new Date());
            File outDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File outFile = new File(outDir, "zerostudio-logpoint-" + stamp + ".txt");
            final int count;
            final String err;
            try {
                count = store.exportToFile(outFile);
                err = null;
            } catch (Throwable t) {
                count = -1;
                err = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            final int finalCount = count;
            final String finalErr = err;
            final File finalFile = outFile;
            if (getView() != null) {
                getView().post(() -> {
                    if (!isAdded()) return;
                    if (exportBtn != null) exportBtn.setEnabled(true);
                    if (finalErr != null) {
                        Toast.makeText(requireContext(),
                                getString(R.string.debugger_log_export_failed, finalErr),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(),
                                getString(R.string.debugger_log_export_ok,
                                        finalCount, finalFile.getAbsolutePath()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
