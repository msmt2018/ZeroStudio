package android.zero.studio.termux.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Environment;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.termux.R;
import android.zero.studio.termux.TermuxFragment;
import android.zero.studio.termux.models.UserAction;
import android.zero.studio.termux.io.KeyboardShortcut;

import com.termux.view.TerminalView;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.data.TermuxUrlUtils;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link com.termux.view.TerminalViewClient} that bridges the {@link com.termux.view.TerminalView}
 * with the {@link TermuxFragment}.
 * @author android_zero
 */
public class TermuxTerminalViewClient extends TermuxTerminalViewClientBase {

    final TermuxFragment mFragment;
    final TermuxTerminalSessionFragmentClient mTermuxTerminalSessionFragmentClient;

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys. */
    boolean mVirtualControlKeyDown, mVirtualFnKeyDown;

    private boolean mTerminalCursorBlinkerStateAlreadySet;
    private List<KeyboardShortcut> mSessionShortcuts;

    private static final String LOG_TAG = "TermuxTerminalViewClient";

    public TermuxTerminalViewClient(@NonNull TermuxFragment fragment, @NonNull TermuxTerminalSessionFragmentClient termuxTerminalSessionFragmentClient) {
        this.mFragment = fragment;
        this.mTermuxTerminalSessionFragmentClient = termuxTerminalSessionFragmentClient;
    }

    public TermuxFragment getFragment() {
        return mFragment;
    }

    /**
     * Should be called when the fragment's view is created.
     */
    public void onCreate() {
        onReloadProperties();

        if (mFragment.getTerminalView() != null) {
            mFragment.getTerminalView().setTextSize(mFragment.getPreferences().getFontSize());
            mFragment.getTerminalView().setKeepScreenOn(mFragment.getPreferences().shouldKeepScreenOn());
        }
    }

    /**
     * Should be called when the fragment starts.
     */
    public void onStart() {
        boolean isTerminalViewKeyLoggingEnabled = mFragment.getPreferences().isTerminalViewKeyLoggingEnabled();
        if(mFragment.getTerminalView() != null)
            mFragment.getTerminalView().setIsTerminalViewKeyLoggingEnabled(isTerminalViewKeyLoggingEnabled);
        
        // Note: ZeroKeyboardInsetsLayout does not need logging enabled via this method currently
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled);
    }

    /**
     * Should be called when the fragment resumes.
     */
    public void onResume() {
        mTerminalCursorBlinkerStateAlreadySet = false;

        // Ensure the terminal view requests focus when the fragment resumes.
        // This is important for hardware keyboard input to work immediately.
        if (mFragment.getTerminalView() != null) {
             mFragment.getTerminalView().requestFocus();
            if (mFragment.getTerminalView().mEmulator != null) {
                setTerminalCursorBlinkerState(true);
                mTerminalCursorBlinkerStateAlreadySet = true;
            }
        }
    }

    /**
     * Should be called when the fragment stops.
     */
    public void onStop() {
        setTerminalCursorBlinkerState(false);
    }

    /**
     * Should be called when properties are reloaded.
     */
    public void onReloadProperties() {
        setSessionShortcuts();
    }

    /**
     * Should be called when the fragment's hosting activity styling is reloaded.
     */
    public void onReloadActivityStyling() {
        setTerminalCursorBlinkerState(true);
    }

    @Override
    public void onEmulatorSet() {
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }
    
    @Override
    public void onSingleTapUp(MotionEvent e) {
        if (mFragment.getTerminalView() == null || mFragment.getCurrentSession() == null) return;
        
        // Explicitly request focus on tap.
        mFragment.getTerminalView().requestFocus();
        
        TerminalEmulator term = mFragment.getCurrentSession().getEmulator();

        if (mFragment.getProperties().shouldOpenTerminalTranscriptURLOnClick()) {
            int[] columnAndRow = mFragment.getTerminalView().getColumnAndRow(e, true);
            String wordAtTap = term.getScreen().getWordAtLocation(columnAndRow[0], columnAndRow[1]);
            LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(wordAtTap);

            if (!urlSet.isEmpty()) {
                String url = (String) urlSet.iterator().next();
                ShareUtils.openUrl(mFragment.getActivity(), url);
                return;
            }
        }
        
        // If not in mouse tracking mode and the event is from touch, show the keyboard.
        // This is the primary manual trigger for showing the keyboard.
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
             KeyboardUtils.showSoftKeyboard(mFragment.getActivity(), mFragment.getTerminalView());
        }
    }


    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mFragment.getProperties().isBackKeyTheEscapeKey();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mFragment.getProperties().isEnforcingCharBasedInput();
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mFragment.getProperties().isUsingCtrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return mFragment.getTerminalToolbarViewPager() == null || mFragment.isTerminalViewSelected() || (mFragment.getTerminalView() != null && mFragment.getTerminalView().hasFocus());
    }

   //复制模式：复制内容时执行
    @Override
    public void copyModeChanged(boolean copyMode) {
        // Fragment layout doesn't use drawer locking
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (handleVirtualKeys(keyCode, e, true)) return true;

        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionFragmentClient.removeFinishedSession(currentSession);
            return true;
        } else if (!mFragment.getProperties().areHardwareKeyboardShortcutsDisabled() &&
            e.isCtrlPressed() && e.isAltPressed()) {
            int unicodeChar = e.getUnicodeChar(0);

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || unicodeChar == 'n') {
                mTermuxTerminalSessionFragmentClient.switchToSession(true);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || unicodeChar == 'p') {
                mTermuxTerminalSessionFragmentClient.switchToSession(false);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
              // 切换到下一个会话标签页
                mTermuxTerminalSessionFragmentClient.switchToSession(true);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mTermuxTerminalSessionFragmentClient.switchToSession(false);
            } else if (unicodeChar == 'k') {
                onToggleSoftKeyboardRequest();
            } else if (unicodeChar == 'm') {
                if (mFragment.getTerminalView() != null) mFragment.getTerminalView().showContextMenu();
            } else if (unicodeChar == 'r') {
                mTermuxTerminalSessionFragmentClient.renameSession(currentSession);
            } else if (unicodeChar == 'c') {
                mTermuxTerminalSessionFragmentClient.addNewSession(false, null);
            } else if (unicodeChar == 'u') {
                showUrlSelection();
            } else if (unicodeChar == 'v') {
                doPaste();
            } else if (unicodeChar == '+' || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+') {
                changeFontSize(true);
            } else if (unicodeChar == '-') {
                changeFontSize(false);
            } else if (unicodeChar >= '1' && unicodeChar <= '9') {
                int index = unicodeChar - '1';
                mTermuxTerminalSessionFragmentClient.switchToSession(index);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mFragment.getTerminalView() != null && mFragment.getTerminalView().mEmulator == null) {
            mFragment.finishActivityIfNotFinishing();
            return true;
        }
        return handleVirtualKeys(keyCode, e, false);
    }

    private boolean handleVirtualKeys(int keyCode, KeyEvent event, boolean down) {
        InputDevice inputDevice = event.getDevice();
        if (mFragment.getProperties().areVirtualVolumeKeysDisabled()) {
            return false;
        } else if (inputDevice != null && inputDevice.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVirtualControlKeyDown = down;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mVirtualFnKeyDown = down;
            return true;
        }
        return false;
    }

    @Override
    public boolean readControlKey() {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown;
    }

    @Override
    public boolean readAltKey() {
        return readExtraKeysSpecialButton(SpecialButton.ALT);
    }

    @Override
    public boolean readShiftKey() {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT);
    }

    @Override
    public boolean readFnKey() {
        return readExtraKeysSpecialButton(SpecialButton.FN);
    }

    public boolean readExtraKeysSpecialButton(SpecialButton specialButton) {
        if (mFragment.getExtraKeysView() == null) return false;
        Boolean state = mFragment.getExtraKeysView().readSpecialButton(specialButton, true);
        if (state == null) {
            Logger.logError(LOG_TAG,"Failed to read an unregistered " + specialButton + " special button value from extra keys.");
            return false;
        }
        return state;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (mVirtualFnKeyDown) {
            int resultingKeyCode = -1;
            int resultingCodePoint = -1;
            boolean altDown = false;
            int lowerCase = Character.toLowerCase(codePoint);
            switch (lowerCase) {
                case 'w': resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP; break;
                case 'a': resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT; break;
                case 's': resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN; break;
                case 'd': resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                case 'p': resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP; break;
                case 'n': resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN; break;
                case 't': resultingKeyCode = KeyEvent.KEYCODE_TAB; break;
                case 'i': resultingKeyCode = KeyEvent.KEYCODE_INSERT; break;
                case 'h': resultingCodePoint = '~'; break;
                case 'u': resultingCodePoint = '_'; break;
                case 'l': resultingCodePoint = '|'; break;
                case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                    resultingKeyCode = (codePoint - '1') + KeyEvent.KEYCODE_F1; break;
                case '0': resultingKeyCode = KeyEvent.KEYCODE_F10; break;
                case 'e': resultingCodePoint = 27; break; // Escape
                case '.': resultingCodePoint = 28; break; // ^.\
                case 'b': case 'f': case 'x':
                    resultingCodePoint = lowerCase; altDown = true; break;
                case 'v':
                    resultingCodePoint = -1;
                    AudioManager audio = (AudioManager) mFragment.getActivity().getSystemService(Context.AUDIO_SERVICE);
                    audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;
                case 'q': case 'k':
                    mFragment.toggleTerminalToolbar();
                    mVirtualFnKeyDown=false;
                    break;
            }

            if (resultingKeyCode != -1) {
                TerminalEmulator term = session.getEmulator();
                session.write(KeyHandler.getCode(resultingKeyCode, 0, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()));
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint);
            }
            return true;
        } else if (ctrlDown) {
            if (codePoint == 106 /* Ctrl+j or \n */ && !session.isRunning()) {
                mTermuxTerminalSessionFragmentClient.removeFinishedSession(session);
                return true;
            }

            List<KeyboardShortcut> shortcuts = mSessionShortcuts;
            if (shortcuts != null && !shortcuts.isEmpty()) {
                int codePointLowerCase = Character.toLowerCase(codePoint);
                for (int i = shortcuts.size() - 1; i >= 0; i--) {
                    KeyboardShortcut shortcut = shortcuts.get(i);
                    if (codePointLowerCase == shortcut.codePoint) {
                        switch (shortcut.shortcutAction) {
                            case TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION:
                                mTermuxTerminalSessionFragmentClient.addNewSession(false, null);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION:
                                mTermuxTerminalSessionFragmentClient.switchToSession(true);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION:
                                mTermuxTerminalSessionFragmentClient.switchToSession(false);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION:
                                mTermuxTerminalSessionFragmentClient.renameSession(mFragment.getCurrentSession());
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void setSessionShortcuts() {
        mSessionShortcuts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.entrySet()) {
            Integer codePoint = (Integer) mFragment.getProperties().getInternalPropertyValue(entry.getKey(), true);
            if (codePoint != null)
                mSessionShortcuts.add(new KeyboardShortcut(codePoint, entry.getValue()));
        }
    }

    public void changeFontSize(boolean increase) {
        mFragment.getPreferences().changeFontSize(increase);
        if(mFragment.getTerminalView() != null)
            mFragment.getTerminalView().setTextSize(mFragment.getPreferences().getFontSize());
    }

    /**
     * Manually toggles the soft keyboard visibility.
     */
    public void onToggleSoftKeyboardRequest() {
        if (mFragment.getTerminalView() == null) return;
        KeyboardUtils.toggleSoftKeyboard(mFragment.getActivity());
    }
    
    // REMOVED setSoftKeyboardState() and getShowSoftKeyboardRunnable()
    // The new logic is much simpler and directly controlled.

    public void setTerminalCursorBlinkerState(boolean start) {
        if (mFragment.getTerminalView() == null) return;
        if (start) {
            if (mFragment.getTerminalView().setTerminalCursorBlinkerRate(mFragment.getProperties().getTerminalCursorBlinkRate()))
                mFragment.getTerminalView().setTerminalCursorBlinkerState(true, true);
            else
                Logger.logError(LOG_TAG,"Failed to start cursor blinker");
        } else {
            mFragment.getTerminalView().setTerminalCursorBlinkerState(false, true);
        }
    }

    public void shareSessionTranscript() {
        TerminalSession session = mFragment.getCurrentSession();
        if (session == null) return;
        String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;
        transcriptText = DataUtils.getTruncatedCommandOutput(transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false).trim();
        ShareUtils.shareText(mFragment.getActivity(), mFragment.getString(R.string.title_share_transcript), transcriptText, mFragment.getString(R.string.title_share_transcript_with));
    }

    public void shareSelectedText() {
        if(mFragment.getTerminalView() == null) return;
        String selectedText = mFragment.getTerminalView().getStoredSelectedText();
        if (DataUtils.isNullOrEmpty(selectedText)) return;
        ShareUtils.shareText(mFragment.getActivity(), mFragment.getString(R.string.title_share_selected_text), selectedText, mFragment.getString(R.string.title_share_selected_text_with));
    }

    public void showUrlSelection() {
        if (mFragment.getCurrentSession() == null) return;
        String text = ShellUtils.getTerminalSessionTranscriptText(mFragment.getCurrentSession(), true, true);
        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        if (urlSet.isEmpty()) {
            new AlertDialog.Builder(mFragment.getActivity()).setMessage(R.string.title_select_url_none_found).show();
            return;
        }
        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        Collections.reverse(Arrays.asList(urls));
        final AlertDialog dialog = new AlertDialog.Builder(mFragment.getActivity()).setItems(urls, (di, which) -> {
            String url = (String) urls[which];
            ShareUtils.copyTextToClipboard(mFragment.getActivity(), url, mFragment.getString(R.string.msg_select_url_copied_to_clipboard));
        }).setTitle(R.string.title_select_url_dialog).create();
        dialog.setOnShowListener(di -> {
            ListView lv = dialog.getListView();
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];
                ShareUtils.openUrl(mFragment.getActivity(), url);
                return true;
            });
        });
        dialog.show();
    }

    public void reportIssueFromTranscript() {
        if (mFragment.getCurrentSession() == null) return;
        final String transcriptText = ShellUtils.getTerminalSessionTranscriptText(mFragment.getCurrentSession(), false, true);
        if (transcriptText == null) return;
        MessageDialogUtils.showMessage(mFragment.requireActivity(), TermuxConstants.TERMUX_APP_NAME + " Report Issue",
            mFragment.getString(R.string.msg_add_termux_debug_info),
            mFragment.getString(R.string.action_yes), (dialog, which) -> reportIssueFromTranscript(transcriptText, true),
            mFragment.getString(R.string.action_no), (dialog, which) -> reportIssueFromTranscript(transcriptText, false),
            null);
    }

    private void reportIssueFromTranscript(String transcriptText, boolean addTermuxDebugInfo) {
        Logger.showToast(mFragment.requireActivity(), mFragment.getString(R.string.msg_generating_report), true);
        new Thread() {
            @Override
            public void run() {
                StringBuilder reportString = new StringBuilder();
                String title = TermuxConstants.TERMUX_APP_NAME + " Report Issue";
                reportString.append("## Transcript\n");
                reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true));
                reportString.append("\n##\n");
                if (addTermuxDebugInfo) {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mFragment.requireActivity(), TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                } else {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mFragment.requireActivity(), TermuxUtils.AppInfoMode.TERMUX_PACKAGE));
                }
                reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(mFragment.requireActivity(), true));
                if (TermuxBootstrap.isAppPackageManagerAPT()) {
                    String termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(mFragment.requireActivity());
                    if (termuxAptInfo != null)
                        reportString.append("\n\n").append(termuxAptInfo);
                }
                if (addTermuxDebugInfo) {
                    String termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(mFragment.requireActivity());
                    if (termuxDebugInfo != null)
                        reportString.append("\n\n").append(termuxDebugInfo);
                }
                String userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.getName();
                ReportInfo reportInfo = new ReportInfo(userActionName, TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, title);
                reportInfo.setReportString(reportString.toString());
                reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString(mFragment.requireActivity()));
                reportInfo.setReportSaveFileLabelAndPath(userActionName,
                    Environment.getExternalStorageDirectory() + "/" +
                        FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));
                ReportActivity.startReportActivity(mFragment.requireActivity(), reportInfo);
            }
        }.start();
    }

    public void doPaste() {
        TerminalSession session = mFragment.getCurrentSession();
        if (session == null || !session.isRunning()) return;
        String text = ShareUtils.getTextStringFromClipboardIfSet(mFragment.getActivity(), true);
        if (text != null)
            session.getEmulator().paste(text);
    }
}