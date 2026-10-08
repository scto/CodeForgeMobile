package com.codeforge.app.terminal;

import android.app.Service;
import androidx.annotation.NonNull;
import com.codeforge.app.TermuxService;
import com.codeforge.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.codeforge.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.codeforge.terminal.TerminalSession;
import com.codeforge.terminal.TerminalSessionClient;
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
