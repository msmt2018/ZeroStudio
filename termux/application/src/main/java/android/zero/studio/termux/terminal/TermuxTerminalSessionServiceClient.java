package android.zero.studio.termux.terminal;

import android.app.Service;
import androidx.annotation.NonNull;
import android.zero.studio.termux.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import java.io.Closeable;

/** The {@link TerminalSessionClient} implementation that may require a {@link Service} for its interface methods. */
public class TermuxTerminalSessionServiceClient extends TermuxTerminalSessionClientBase implements
    Closeable {

    private static final String LOG_TAG = "TermuxTerminalSessionServiceClient";

    private TermuxService mService;

    public TermuxTerminalSessionServiceClient(TermuxService service) {
        this.mService = service;
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxSession termuxSession = mService.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }

    @Override
    public void close() {
        mService = null;
    }
}
