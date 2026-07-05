package android.zero.studio.termux.terminal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.text.TextUtils;
import android.widget.ListView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import android.zero.studio.termux.TermuxFragment;
import android.zero.studio.termux.TermuxService;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.io.BellHandler;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * The {@link TerminalSessionClient} implementation for {@link TermuxFragment}.
 * @author android_zero
 */
public class TermuxTerminalSessionFragmentClient extends TermuxTerminalSessionClientBase {

    protected final TermuxFragment mFragment;

    private static final int MAX_SESSIONS = 8;

    private SoundPool mBellSoundPool;

    private int mBellSoundId;

    private static final String LOG_TAG = "TermuxTerminalSessionFragmentClient";

    public TermuxTerminalSessionFragmentClient(@NonNull TermuxFragment fragment) {
        this.mFragment = fragment;
    }

    /**
     * Should be called when the fragment's view is created.
     */
    public void onCreate() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }

    /**
     * Should be called when the fragment starts.
     */
    public void onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by {@link #onStop} if its valid,
        // otherwise get the last session currently running.
        if (mFragment.getTermuxService() != null) {
            setCurrentSession(getCurrentStoredSessionOrLast());
            termuxSessionListNotifyUpdated();
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        if (mFragment.getTerminalView() != null)
            mFragment.getTerminalView().onScreenUpdated();
    }

    /**
     * Should be called when the fragment resumes.
     */
    public void onResume() {
        // Just initialize the mBellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool();
    }

    /**
     * Should be called when the fragment stops.
     */
    public void onStop() {
        // Store current session in shared preferences so that it can be restored later in
        // {@link #onStart} if needed.
        setCurrentStoredSession();

        // Release mBellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool();
    }

    /**
     * Should be called when the fragment's hosting activity styling is reloaded.
     */
    public void onReloadActivityStyling() {
        // Set terminal fonts and colors
        checkForFontAndColors();
    }



    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (!mFragment.isVisibles()) return;

        if (mFragment.getCurrentSession() == changedSession && mFragment.getTerminalView() != null)
            mFragment.getTerminalView().onScreenUpdated();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (!mFragment.isVisibles()) return;

        if (updatedSession != mFragment.getCurrentSession()) {
            // Only show toast for other sessions than the current one, since the user
            // probably consciously caused the title change to change in the current session
            // and don't want an annoying toast for that.
            mFragment.showToast(toToastTitle(updatedSession), true);
        }

        termuxSessionListNotifyUpdated();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        TermuxService service = mFragment.getTermuxService();

        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            mFragment.finishActivityIfNotFinishing();
            return;
        }

        int index = service.getIndexOfSession(finishedSession);

        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting for the user to press enter.
        // The plugin can handle/show errors itself.
        boolean isPluginExecutionCommandWithPendingResult = false;
        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult = termuxSession.getExecutionCommand().isPluginExecutionCommandWithPendingResult();
            if (isPluginExecutionCommandWithPendingResult)
                Logger.logVerbose(LOG_TAG, "The \"" + finishedSession.mSessionName + "\" session will be force finished automatically since result in pending.");
        }

        if (mFragment.isVisibles() && finishedSession != mFragment.getCurrentSession()) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                mFragment.showToast(toToastTitle(finishedSession) + " - exited", true);
        }

        // Auto close logic
        // Modified: Always try to remove session if it's not the last one or handle properly
        removeFinishedSession(finishedSession);
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mFragment.isVisibles()) return;
        ShareUtils.copyTextToClipboard(mFragment.getActivity(), text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mFragment.isVisibles()) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mFragment.getActivity(), true);
        if (text != null && mFragment.getTerminalView() != null && mFragment.getTerminalView().mEmulator != null)
            mFragment.getTerminalView().mEmulator.paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (!mFragment.isVisibles()) return;

        switch (mFragment.getProperties().getBellBehaviour()) {
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE:
                BellHandler.getInstance(mFragment.getActivity()).doBell();
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP:
                loadBellSoundPool();
                if (mBellSoundPool != null)
                    mBellSoundPool.play(mBellSoundId, 1.f, 1.f, 1, 0, 1.f);
                break;
            case TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE:
                // Ignore the bell character.
                break;
        }
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (mFragment.getCurrentSession() == changedSession)
            updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        if (enabled && !mFragment.isVisibles()) return;

        // If cursor is to enabled now, then start cursor blinking if blinking is enabled
        // otherwise stop cursor blinking
        if(mFragment.getTerminalView() != null)
            mFragment.getTerminalView().setTerminalCursorBlinkerState(enabled, false);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession terminalSession, int pid) {
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;
        
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(terminalSession);
        if (termuxSession != null)
            termuxSession.getExecutionCommand().mPid = pid;
    }


    /**
     * Should be called when the terminal session is reset.
     */
    public void onResetTerminalSession() {
        // Ensure blinker starts again after reset if cursor blinking was disabled before reset like
        // with "tput civis" which would have called onTerminalCursorStateChange()
        if(mFragment.getTerminalView() != null)
            mFragment.getTerminalView().setTerminalCursorBlinkerState(true, true);
    }



    @Override
    public Integer getTerminalCursorStyle() {
        return mFragment.getProperties().getTerminalCursorStyle();
    }



    /** Load mBellSoundPool */
    private synchronized void loadBellSoundPool() {
        if (mBellSoundPool == null) {
            mBellSoundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()).build();

            try {
                mBellSoundId = mBellSoundPool.load(mFragment.getActivity(), R.raw.bell, 1);
            } catch (Exception e){
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e);
            }
        }
    }

    /** Release mBellSoundPool resources */
    private synchronized void releaseBellSoundPool() {
        if (mBellSoundPool != null) {
            mBellSoundPool.release();
            mBellSoundPool = null;
        }
    }



    /** Try switching to session. */
    public void setCurrentSession(TerminalSession session) {
        if (session == null) {
             // Handle empty state
             if (mFragment.getTerminalView() != null) {
                 // Optional: Detach session or show empty state
                 // mFragment.getTerminalView().attachSession(null); 
             }
             return;
        }
        
        if (mFragment.getTerminalView() == null) return;

        if (mFragment.getTerminalView().attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange();
        }

        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.
        // checkAndScrollToSession(session);
        updateBackgroundColor();
    }

    void notifyOfSessionChange() {
        if (!mFragment.isVisibles()) return;

        if (!mFragment.getProperties().areTerminalSessionChangeToastsDisabled()) {
            TerminalSession session = mFragment.getCurrentSession();
            mFragment.showToast(toToastTitle(session), false);
        }
    }

    public void switchToSession(boolean forward) {
        // Logic moved to SessionTabManager for cleaner separation, 
        // but kept here for compatibility if called from other places
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;

        TerminalSession currentTerminalSession = mFragment.getCurrentSession();
        int index = service.getIndexOfSession(currentTerminalSession);
        int size = service.getTermuxSessionsSize();
        if (forward) {
            if (++index >= size) index = 0;
        } else {
            if (--index < 0) index = size - 1;
        }

        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null)
            setCurrentSession(termuxSession.getTerminalSession());
            
        termuxSessionListNotifyUpdated();
    }

    public void switchToSession(int index) {
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;

        TermuxSession termuxSession = service.getTermuxSession(index);
        if (termuxSession != null)
            setCurrentSession(termuxSession.getTerminalSession());
            
        termuxSessionListNotifyUpdated();
    }

    @SuppressLint("InflateParams")
    public void renameSession(final TerminalSession sessionToRename) {
        if (sessionToRename == null) return;

        TextInputDialogUtils.textInput(mFragment.getActivity(), R.string.title_rename_session, sessionToRename.mSessionName, R.string.action_rename_session_confirm, text -> {
            renameSession(sessionToRename, text);
            termuxSessionListNotifyUpdated();
        }, -1, null, -1, null, null);
    }

    private void renameSession(TerminalSession sessionToRename, String text) {
        if (sessionToRename == null) return;
        sessionToRename.mSessionName = text;
        TermuxService service = mFragment.getTermuxService();
        if (service != null) {
            TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename);
            if (termuxSession != null)
                termuxSession.getExecutionCommand().shellName = text;
        }
    }
    
    public void addNewSession(boolean isFailSafe, String sessioName) {
        addNewSession(isFailSafe, sessioName, null);
    }

    public void addNewSession(boolean isFailSafe, String sessionName, String workingDirectory) {
        // Delegate to SessionTabManager if possible, or use service directly
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;

        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            new AlertDialog.Builder(mFragment.getActivity()).setTitle(R.string.title_max_terminals_reached).setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null).show();
        } else {
            TerminalSession currentSession = mFragment.getCurrentSession();

            if (workingDirectory == null) {
                if (currentSession == null) {
                    workingDirectory = mFragment.getProperties().getDefaultWorkingDirectory();
                } else {
                    workingDirectory = currentSession.getCwd();
                }
            }

            TermuxSession newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName);
            if (newTermuxSession == null) return;

            TerminalSession newTerminalSession = newTermuxSession.getTerminalSession();
            setCurrentSession(newTerminalSession);
            
            termuxSessionListNotifyUpdated();
        }
    }

    public void setCurrentStoredSession() {
        TerminalSession currentSession = mFragment.getCurrentSession();
        if (currentSession != null)
            mFragment.getPreferences().setCurrentSession(currentSession.mHandle);
        else
            mFragment.getPreferences().setCurrentSession(null);
    }

    /** The current session as stored or the last one if that does not exist. */
    public TerminalSession getCurrentStoredSessionOrLast() {
        TerminalSession stored = getCurrentStoredSession();

        if (stored != null) {
            // If a stored session is in the list of currently running sessions, then return it
            return stored;
        } else {
            // Else return the last session currently running
            TermuxService service = mFragment.getTermuxService();
            if (service == null) return null;

            TermuxSession termuxSession = service.getLastTermuxSession();
            if (termuxSession != null)
                return termuxSession.getTerminalSession();
            else
                return null;
        }
    }

    private TerminalSession getCurrentStoredSession() {
        String sessionHandle = mFragment.getPreferences().getCurrentSession();
        if (sessionHandle == null) return null;

        // If no session is stored in shared preferences
        if (sessionHandle == null)
            return null;

        // Check if the session handle found matches one of the currently running sessions
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return null;

        return service.getTerminalSessionForHandle(sessionHandle);
    }

    public void removeFinishedSession(TerminalSession finishedSession) {
        // Return pressed with finished session - remove it.
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;

        int index = service.removeTermuxSession(finishedSession);
        
        // Logic to switch to another session or close
        int size = service.getTermuxSessionsSize();
        if (size > 0) {
             if (index >= size) {
                index = size - 1;
            }
            TermuxSession termuxSession = service.getTermuxSession(index);
            if (termuxSession != null)
                setCurrentSession(termuxSession.getTerminalSession());
        } else {
            // No sessions left
            setCurrentSession(null);
             // DO NOT finish activity, keep fragment alive
        }
        
        termuxSessionListNotifyUpdated();
    }

    public void termuxSessionListNotifyUpdated() {
        mFragment.termuxSessionListNotifyUpdated();
    }

    public void checkAndScrollToSession(TerminalSession session) {
        if (!mFragment.isVisibles()) return;
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return;
        if(mFragment.getView() == null) return;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return;
        final ListView termuxSessionsListView = mFragment.getView().findViewById(R.id.terminal_sessions_list);
        if (termuxSessionsListView == null) return;

        termuxSessionsListView.setItemChecked(indexOfSession, true);
        // Delay is necessary otherwise sometimes scroll to newly added session does not happen
        termuxSessionsListView.postDelayed(() -> termuxSessionsListView.smoothScrollToPosition(indexOfSession), 1000);
    }


    String toToastTitle(TerminalSession session) {
        TermuxService service = mFragment.getTermuxService();
        if (service == null) return null;

        final int indexOfSession = service.getIndexOfSession(session);
        if (indexOfSession < 0) return null;
        StringBuilder toastTitle = new StringBuilder("[" + (indexOfSession + 1) + "]");
        if (!TextUtils.isEmpty(session.mSessionName)) {
            toastTitle.append(" ").append(session.mSessionName);
        }
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(session.mSessionName == null ? " " : "\n");
            toastTitle.append(title);
        }
        return toastTitle.toString();
    }


    public void checkForFontAndColors() {
        try {
            File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
            File fontFile = TermuxConstants.TERMUX_FONT_FILE;

            final Properties props = new Properties();
            if (colorsFile.isFile()) {
                try (InputStream in = new FileInputStream(colorsFile)) {
                    props.load(in);
                }
            }

            TerminalColors.COLOR_SCHEME.updateWith(props);
            TerminalSession session = mFragment.getCurrentSession();
            if (session != null && session.getEmulator() != null) {
                session.getEmulator().mColors.reset();
            }
            updateBackgroundColor();

            final Typeface newTypeface = (fontFile.exists() && fontFile.length() > 0) ? Typeface.createFromFile(fontFile) : Typeface.MONOSPACE;
            if(mFragment.getTerminalView() != null)
                mFragment.getTerminalView().setTypeface(newTypeface);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in checkForFontAndColors()", e);
        }
    }

    public void updateBackgroundColor() {
        if (!mFragment.isVisibles()) return;
        TerminalSession session = mFragment.getCurrentSession();
        View terminalView = mFragment.getTerminalView();
        if (session != null && session.getEmulator() != null && terminalView != null) {
            terminalView.setBackgroundColor(session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }
}