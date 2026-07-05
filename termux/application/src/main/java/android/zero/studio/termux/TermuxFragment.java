package android.zero.studio.termux;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import android.zero.studio.termux.app.BaseIDEFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.itsaky.androidide.ui.EdgeSnapBubbleView;
import com.itsaky.androidide.projects.IProjectManager;
import com.termux.R;
import com.termux.app.activities.HelpActivity;
import android.zero.studio.termux.ui.theme.TerminalThemeManager;
import android.zero.studio.termux.api.file.FileReceiverActivity;
import android.zero.studio.termux.terminal.TermuxTerminalSessionFragmentClient;
import android.zero.studio.termux.terminal.TermuxTerminalViewClient;
import android.zero.studio.termux.terminal.TermuxSessionsListViewController;
import android.zero.studio.termux.io.TerminalToolbarViewPager;
import android.zero.studio.termux.io.TermuxTerminalExtraKeys;
import android.zero.studio.termux.ui.tabs.SessionTabManager;
import android.zero.studio.termux.ui.tabs.TerminalSessionTabsView;

import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A terminal emulator fragment. This class encapsulates the UI and logic for a terminal interface,
 * designed to be hosted by any Activity.
 *
 * @author android_zero
 */
public class TermuxFragment extends BaseIDEFragment implements ServiceConnection {

    /** The root view of the fragment, inflated in onCreateView. */
    private View mRootView;

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a
     * call to {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    protected TermuxService mTermuxService;

    /** The {@link TerminalView} shown in {@link TermuxFragment} that displays the terminal. */
    protected TerminalView mTerminalView;

    /**
     * The {@link TerminalViewClient} interface implementation to allow for communication between
     * {@link TerminalView} and {@link TermuxFragment}.
     */
    protected TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     * The {@link TerminalSessionClient} interface implementation to allow for communication between
     * {@link TerminalSession} and {@link TermuxFragment}.
     */
    protected TermuxTerminalSessionFragmentClient mTermuxTerminalSessionActivityClient;

    /** Termux app shared preferences manager. */
    protected TermuxAppSharedPreferences mPreferences;

    /** Termux app SharedProperties loaded from termux.properties */
    protected TermuxAppSharedProperties mProperties;

    /** The root view of the {@link TermuxFragment}. */
    protected TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxFragment}.
     * Used for legacy layout calculations or padding adjustments.
     */
    protected View mTermuxActivityBottomSpaceView;

    /** The terminal extra keys view. */
    protected ExtraKeysView mExtraKeysView;

    /** The client for the {@link #mExtraKeysView}. */
    protected TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /** The termux sessions list controller (kept for compatibility, though UI uses Tabs now). */
    protected TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The last toast shown, used cancel current toast before showing new in {@link
     * #showToast(String, boolean)}.
     */
    protected WeakReference<Toast> mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the
     * terminal view at the time, so if the session causing a change is not in the foreground it
     * should probably be treated as background.
     */
    protected boolean mIsVisible;

    /** If onResume() was called after onCreate(). */
    protected boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving {@link
     * TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity was
     * killed by android.
     */
    protected boolean mIsActivityRecreated = false;

    /** The {@link TermuxFragment} is in an invalid state and must not be run. */
    protected boolean mIsInvalidState;

    protected int mNavBarHeight;

    protected float mTerminalToolbarDefaultHeight;

    protected TerminalSessionTabsView mTabsView;
    protected SessionTabManager mSessionTabManager;
    private View mTerminalEmptyStateContainer;

    // Theme Manager instance
    private TerminalThemeManager mThemeManager;

    // Bottom Sheet specific variables
    private View mBottomSheetContainer;
    private BottomSheetBehavior<View> mBottomSheetBehavior;
    private EdgeSnapBubbleView mToolbarGestureBubble;

    // Project specific
    private String mInitialWorkingDir;
    private String mInitialSessionName;
    private boolean mPendingCreateSession;
    private boolean mPendingCreateFailsafe;
    private String mPendingCreateSessionName;
    private String mPendingCreateWorkingDir;

    protected static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    protected static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    protected static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    protected static final int CONTEXT_MENU_AUTOFILL_ID = 2;
    protected static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    protected static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    protected static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    protected static final int CONTEXT_MENU_HELP_ID = 7;
    protected static final int CONTEXT_MENU_REPORT_ID = 9;
    protected static final int CONTEXT_MENU_THEME_PICKER_ID = 100;

    protected static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    protected static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    // Arguments key
    public static final String ARG_WORKING_DIR = "arg_working_dir";

    protected static final String LOG_TAG = "TermuxFragment";

    // This method is from the original BaseIDEActivity and now adapted for BaseIDEFragment
    @NonNull
    @Override
    protected View bindLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return inflater.inflate(R.layout.fragment_terminal, container, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        TermuxAppSharedProperties.init(context);
        mProperties = TermuxAppSharedProperties.getProperties();
        setActivityTheme();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Logger.logDebug(LOG_TAG, "onCreate");
        
        // Initialize Theme Manager
        mThemeManager = new TerminalThemeManager(requireContext());

        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Retrieve working directory from arguments or IProjectManager
        if (getArguments() != null) {
            mInitialWorkingDir = getArguments().getString(ARG_WORKING_DIR);
        }

        // Fallback to Project Manager if null
        // Also added checks to prevent crashes if IProjectManager is not available.
        if (mInitialWorkingDir == null) {
            try {

                IProjectManager projectManager = IProjectManager.getInstance();
                if (projectManager != null) {
                    // Assuming projectDirPath property exists or getProjectDir()
                    String path = projectManager.getProjectDirPath();
                    File projectDir = new File(path);
                    if (projectDir.exists() && projectDir.isDirectory()) {
                        mInitialWorkingDir = projectDir.getAbsolutePath();
                        // Also try to get project name
                        if (IProjectManager.getInstance().getWorkspace() != null &&
                                IProjectManager.getInstance().getWorkspace().getRootProject() != null) {
                            mInitialSessionName = IProjectManager.getInstance().getWorkspace().getRootProject().getName();
                        }
                    }
                }
            } catch (NoClassDefFoundError | Exception e) {
                // IProjectManager might not be available in all contexts or builds
                Logger.logWarn(LOG_TAG, "IProjectManager not available or failed to get project info: " + e.getMessage());
            }
        }

        ReportActivity.deleteReportInfoFilesOlderThanXDays(requireActivity(), 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(requireActivity(), true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running
            // fragment code
            mIsInvalidState = true;
            return;
        }

        // The service binding is initiated here and callbacks are handled by the fragment.
        // This makes the fragment self-contained but less reusable.
        // A better pattern is to have the hosting Activity manage the service connection and
        // provide the service instance to the fragment. This will be refactored later.
        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(requireActivity(), TermuxService.class);
            requireActivity().startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link
            // #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!requireActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxFragment failed to start TermuxService", e);
            Logger.showToast(requireActivity(),
                    getString(e.getMessage() != null && e.getMessage().contains("app is in background") ? R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                    true);
            mIsInvalidState = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
                    ViewGroup container, @Nullable Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onCreateView");
        mRootView = bindLayout(inflater, container);
        return mRootView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Logger.logVerbose(LOG_TAG, "onViewCreated");

        if (mIsInvalidState) {
            requireActivity().finish();
            return;
        }

        // Bind specific UI controls as requested
        mTermuxActivityRootView = view.findViewById(R.id.activity_termux_root_view);
        mTermuxActivityBottomSpaceView = view.findViewById(R.id.activity_termux_bottom_space_view);

        // Set reference to fragment in root view. 
        // This allows TermuxActivityRootView to access fragment state if needed.
        if (mTermuxActivityRootView != null) {
             mTermuxActivityRootView.setFragment(this);
        }

        mTabsView = view.findViewById(R.id.terminal_tabs_view);
        mTerminalEmptyStateContainer = view.findViewById(R.id.terminal_empty_state_container);
        MaterialButton newSessionButton = view.findViewById(R.id.terminal_empty_state_new_session_button);
        if (newSessionButton != null) {
            newSessionButton.setOnClickListener(v -> onCreateNewSession(false, null, null));
        }
        mSessionTabManager = new SessionTabManager(requireContext());

        // Set default working directory to manager
        if (mInitialWorkingDir != null) {
            mSessionTabManager.updateWorkingDirectory(mInitialWorkingDir);
        }

        // If service is already connected (e.g. re-attaching fragment), bind the tabs view now
        if (mTermuxService != null && mTabsView != null) {
            mTabsView.attachToFragment(this, mSessionTabManager);
            mTabsView.refreshSessions();
        }
        
        // Setup Bottom Sheet logic for the new Drawer-style toolbar
        setupBottomSheet(view);

        // setMargins();
        // ZeroKeyboardInsetsLayout automatically handles insets, so we don't strictly need manual margin setting here
        // but we keep the listener for legacy compatibility if needed.
        View content = requireActivity().findViewById(android.R.id.content);
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                mNavBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                return insets;
            });
        }

        if (mProperties.isUsingFullScreen()) {
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        // Initialize the toolbar logic (Extra Keys + Text Input) inside the Bottom Sheet
        setTerminalToolbarView(savedInstanceState);

         setToggleKeyboardView();
         
        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(requireActivity());

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that
        // Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(requireActivity());

        // Restore the theme (background color) immediately
        if (mThemeManager != null) {
            mThemeManager.restoreTheme(this);
        }

        view.post(() -> {
            // Check if fragment is still attached before proceeding
            if (!isAdded() || getActivity() == null) return;
            
            if (mTerminalView != null) {
                mTerminalView.requestFocus();
                // Ensure keyboard doesn't get stuck in a weird state
                if (!mProperties.shouldSoftKeyboardBeHiddenOnStartup()) {
                    KeyboardUtils.showSoftKeyboard(getActivity(), mTerminalView);
                }
            }
        });
    }
    
    /**
     * Initializes the BottomSheet logic for the toolbar and binds the top gesture bubble.
     */
    private void setupBottomSheet(View rootView) {
        mBottomSheetContainer = rootView.findViewById(R.id.bottom_sheet_container);
        mToolbarGestureBubble = rootView.findViewById(R.id.terminal_toolbar_gesture_bubble);

        if (mBottomSheetContainer != null) {
            mBottomSheetBehavior = BottomSheetBehavior.from(mBottomSheetContainer);
            
            // Initial state: based on user preference
            if (mPreferences.shouldShowTerminalToolbar()) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }

            if (mToolbarGestureBubble != null) {
                mToolbarGestureBubble.setOrientation(EdgeSnapBubbleView.Orientation.HORIZONTAL);
                mToolbarGestureBubble.setPosition(EdgeSnapBubbleView.Position.TOP);
                mToolbarGestureBubble.setOnBubbleClickListener(v -> toggleTerminalToolbar());
                mToolbarGestureBubble.setOnBubbleGestureListener(new EdgeSnapBubbleView.OnBubbleGestureListener() {
                    @Override
                    public void onDrag(float fraction) {}

                    @Override
                    public void onRelease(float fraction) {
                        if (fraction < -0.15f) {
                            if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                                toggleTerminalToolbar();
                            }
                        } else if (fraction > 0.15f) {
                            if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                                toggleTerminalToolbar();
                            }
                        }
                    }
                });
            }

            // Optional: Listen to callbacks to update preferences or UI state
            mBottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        // Toolbar is fully visible
                        mPreferences.setShowTerminalToolbar(true);
                        if (mToolbarGestureBubble != null) mToolbarGestureBubble.setArrowExpanded(true);

                        if (mTerminalView != null) mTerminalView.requestFocus();
                    } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        mPreferences.setShowTerminalToolbar(false);
                        if (mToolbarGestureBubble != null) mToolbarGestureBubble.setArrowExpanded(false);
                        if (mTerminalView != null) mTerminalView.requestFocus();
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
            });
        }
    }

    /**
     * Toggles the Bottom Sheet state to show or hide (collapse) the toolbar.
     */
    public void toggleTerminalToolbar() {
        if (mBottomSheetBehavior == null) return;

        if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            Logger.showToast(requireActivity(), getString(R.string.msg_disabling_terminal_toolbar), true);
        } else {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Logger.showToast(requireActivity(), getString(R.string.msg_enabling_terminal_toolbar), true);
            
            // If showing and text input is selected, request focus
            if (isTerminalToolbarTextInputViewSelected()) {
                 View textInput = mRootView.findViewById(R.id.terminal_toolbar_text_input);
                 if(textInput != null) textInput.requestFocus();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();
        

        if (mTerminalView != null) {
            mTerminalView.requestFocus();
        }

        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(requireActivity(), LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    public void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Logger.logDebug(LOG_TAG, "onDestroyView");

        if (mLastToast != null) {
            Toast lastToast = mLastToast.get();
            if (lastToast != null) lastToast.cancel();
            mLastToast.clear();
            mLastToast = null;
        }
        if (mSessionTabManager != null) mSessionTabManager.detach();

        if (mIsInvalidState) return;
        if (mTermuxService != null) {
            mTermuxService.unsetTermuxTerminalSessionClient();
        }
        try {
            if (mTermuxService != null) {
                requireActivity().unbindService(this);
                mTermuxService = null;
            }
        } catch (Exception e) {
            // ignore.
        }

        if (mTerminalView != null) {
            try {
                unregisterForContextMenu(mTerminalView);
            } catch (Exception ignored) {
                // ignore.
            }
            mTerminalView.setTerminalViewClient(null);
            mTerminalView = null;
        }

        // Clear view references to prevent leaks
        mRootView = null;
        mTermuxActivityRootView = null;
        mTermuxActivityBottomSpaceView = null;
        mExtraKeysView = null;
        mTermuxTerminalExtraKeys = null;
        mTermuxTerminalViewClient = null;
        mTermuxTerminalSessionActivityClient = null;
        mTabsView = null;
        
        mBottomSheetContainer = null;
        mToolbarGestureBubble = null;
        mBottomSheetBehavior = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with {@link
     * #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a
     * call to this callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");
        
        // Check if Fragment is still attached to Activity
        if (!isAdded() || getActivity() == null) {
             Logger.logDebug(LOG_TAG, "onServiceConnected: Fragment not attached to Activity, returning.");
             return;
        }

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        // Ensure mTabsView and mSessionTabManager are not null before using
        if (mTabsView != null && mSessionTabManager != null) {
             mSessionTabManager.attachToFragment(this);
             mTabsView.attachToFragment(this, mSessionTabManager);
        }

        final Intent intent = getActivity().getIntent();
        getActivity().setIntent(null);

        final String workingDir = intent != null && intent.getExtras() != null ? intent.getExtras().getString(TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR, null) : null;
        final String sessionName = intent != null && intent.getExtras() != null ? intent.getExtras().getString(TERMUX_ACTIVITY.EXTRA_SESSION_NAME, null) : null;
        boolean launchFailsafe = intent != null && intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);

        if (!mTermuxService.isTermuxSessionsEmpty()) {
            if (mThemeManager != null) {
                for(int i = 0; i < mTermuxService.getTermuxSessionsSize(); i++) {
                    TermuxSession ts = mTermuxService.getTermuxSession(i);
                    if(ts != null) {
                        mThemeManager.applyCurrentThemeToSession(ts.getTerminalSession());
                    }
                }
            }

            final Optional<TermuxSession> existingSession = workingDir == null ? Optional.empty() : mTermuxService.getTermuxSessions().stream().filter(session -> Objects.equals(
                            session.getTerminalSession().getCwd(), workingDir)).findFirst();
            setupTermuxSessionOnServiceConnected(intent, workingDir, sessionName, existingSession.orElse(null), launchFailsafe);
        } else {
            updateTerminalEmptyState();
        }
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        if (mPendingCreateSession) {
            boolean pendingFailsafe = mPendingCreateFailsafe;
            String pendingSessionName = mPendingCreateSessionName;
            String pendingWorkingDir = mPendingCreateWorkingDir;
            mPendingCreateSession = false;
            mPendingCreateSessionName = null;
            mPendingCreateWorkingDir = null;
            onCreateNewSession(pendingFailsafe, pendingSessionName, pendingWorkingDir);
        }
    }

    protected void setupTermuxSessionOnServiceConnected(Intent intent, String workingDir, String sessionName, TermuxSession existingSession, boolean launchFailsafe) {
        if (mTermuxService.isTermuxSessionsEmpty()) return;
        if (existingSession != null) {
            if (existingSession.getExecutionCommand().isFailsafe != launchFailsafe) {
                onCreateNewSession(launchFailsafe, sessionName, workingDir);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(existingSession.getTerminalSession());
            }
        } else if (workingDir != null) {
            onCreateNewSession(launchFailsafe, sessionName, workingDir);
        } else {
            mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
        }

        if (mTabsView != null) {
            mTabsView.refreshSessions();
        }
        updateTerminalEmptyState();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        mTermuxService = null;
        updateTerminalEmptyState();
    }

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityTheme() {
        if (mProperties == null) mProperties = TermuxAppSharedProperties.getProperties();
        if (mProperties == null) return;
        
        // Keep internal Termux styling variables up to date
        // TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        
        // Activity activity = requireActivity();
        // if (activity instanceof AppCompatActivity) {
             // REMOVED: Do not force Activity Night Mode from Termux properties
             // This allows the main app (AndroidIDE) to control the Day/Night theme globally.
             // AppCompatActivityUtils.setNightMode((AppCompatActivity) activity, NightMode.getAppNightMode().getName(), true);
        // }
    }

    private void setMargins() {
        RelativeLayout relativeLayout = mRootView.findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    private void setTermuxTerminalViewAndClients() {
        mTermuxTerminalSessionActivityClient = onCreateTerminalSessionClient();
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTerminalView = mRootView.findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    @NonNull
    protected TermuxTerminalSessionFragmentClient onCreateTerminalSessionClient() {
        return new TermuxTerminalSessionFragmentClient(this);
    }

    /**
     * Initialize and setup the bottom toolbar (ExtraKeys + Text Input).
     * The ViewPager is now hosted inside the BottomSheet layout in fragment_terminal.xml.
     */
    private void setTerminalToolbarView(Bundle savedInstanceState) {
        // Initialize the ExtraKeys logic
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        // Setup the ViewPager adapter which contains the extra keys and text input views
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        // Calculate height based on number of rows in extra keys matrix
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    /**
     * Toggles the Bottom Sheet state to show or hide (collapse) the toolbar.
     // */
    // public void toggleTerminalToolbar() {
        // final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        // if (terminalToolbarViewPager == null) return;

        // final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        // Logger.showToast(requireActivity(), (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        // terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        // if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // // Focus the text input view if just revealed.
            // mRootView.findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        // }
    // }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null || mRootView == null) return;
        final EditText textInputView = mRootView.findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    public void onCreateNewSession(boolean isFailsafe, String sessionName, String workingDirectory) {
        if (mTermuxService == null) {
            mPendingCreateSession = true;
            mPendingCreateFailsafe = isFailsafe;
            mPendingCreateSessionName = sessionName;
            mPendingCreateWorkingDir = workingDirectory;
            Intent serviceIntent = new Intent(requireActivity(), TermuxService.class);
            requireActivity().startService(serviceIntent);
            requireActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
            return;
        }

        final String requestedWorkingDirectory = workingDirectory;
        TermuxInstaller.setupBootstrapIfNeeded(requireActivity(), () -> {
            final Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded() || mTermuxService == null || mTermuxTerminalSessionActivityClient == null) return;
                String targetWorkingDirectory = requestedWorkingDirectory;
                if (targetWorkingDirectory == null) {
                    targetWorkingDirectory = mInitialWorkingDir;
                }
                mTermuxTerminalSessionActivityClient.addNewSession(isFailsafe, sessionName, targetWorkingDirectory);

                if (mThemeManager != null) {
                    TerminalSession session = getCurrentSession();
                    if (session != null) {
                        mThemeManager.applyCurrentThemeToSession(session);
                    }
                }

                if (mTabsView != null) mTabsView.refreshSessions();
                updateTerminalEmptyState();
            });
        });
    }

    private void setToggleKeyboardView() {
        // This button is likely inside the Drawer in the old layout, but we might not have it in the new Fragment layout.
        // If it exists in the new layout or toolbar, wire it up.
        View toggleButton = mRootView.findViewById(R.id.toggle_keyboard_button);
        if (toggleButton != null) {
             toggleButton.setOnClickListener(v -> {
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            });

            toggleButton.setOnLongClickListener(v -> {
                toggleTerminalToolbar();
                return true;
            });
        }
    }

    public boolean onBackPressed() {
        return false;
    }

    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (mTermuxTerminalViewClient == null) return false;
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return false;
        return mTermuxTerminalViewClient.onKeyDown(keyCode, event, currentSession);
    }

    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (mTermuxTerminalViewClient == null) return false;
        return mTermuxTerminalViewClient.onKeyUp(keyCode, event);
    }

    public void finishActivityIfNotFinishing() {
        if (requireActivity() != null && !requireActivity().isFinishing()) {
            requireActivity().finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) {
            Toast lastToast = mLastToast.get();
            if (lastToast != null) lastToast.cancel();
            mLastToast.clear();
        }
        Toast toast = Toast.makeText(requireActivity(), text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
        mLastToast = new WeakReference<>(toast);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean addAutoFillMenu = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireActivity().getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.isEnabled()) addAutoFillMenu = true;
        }
        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (addAutoFillMenu)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_ID, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        
        menu.add(Menu.NONE, CONTEXT_MENU_THEME_PICKER_ID, Menu.NONE, R.string.title_select_theme);
        
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        mTerminalView.showContextMenu();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();
        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_ID:
                requestAutoFill();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(requireActivity(), new Intent(requireActivity(), HelpActivity.class));
                return true;
            case CONTEXT_MENU_THEME_PICKER_ID:
                showThemeSelectionDialog();
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Shows a dialog to select the terminal theme.
     */
    private void showThemeSelectionDialog() {
        if (mThemeManager == null) return;

        final String[] themeNames = mThemeManager.getThemeNames();
        
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_select_theme)
            .setItems(themeNames, (dialog, which) -> {
                TerminalThemeManager.Theme selectedTheme = mThemeManager.getAvailableThemes().get(which);
                mThemeManager.applyTheme(this, selectedTheme);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public void onContextMenuClosed(Menu menu) { if (mTerminalView != null) mTerminalView.onContextMenuClosed(menu); }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;
        final AlertDialog.Builder b = new AlertDialog.Builder(requireActivity());
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

    private void requestAutoFill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = requireActivity().getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.isEnabled()) {
                autofillManager.requestAutofill(mTerminalView);
            }
        }
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public ViewPager getTerminalToolbarViewPager() {
        if (mRootView == null) return null;
        return (ViewPager) mRootView.findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }

    public void termuxSessionListNotifyUpdated() {
           if (mTabsView != null) {
                mTabsView.refreshSessions();
             }
           updateTerminalEmptyState();
     }

    private void updateTerminalEmptyState() {
        if (mTerminalEmptyStateContainer == null || mTerminalView == null) return;
        boolean hasSessions = mTermuxService != null && !mTermuxService.isTermuxSessionsEmpty();
        mTerminalEmptyStateContainer.setVisibility(hasSessions ? View.GONE : View.VISIBLE);
        mTerminalView.setVisibility(hasSessions ? View.VISIBLE : View.INVISIBLE);
        if (mBottomSheetContainer != null) {
            mBottomSheetContainer.setVisibility(hasSessions ? View.VISIBLE : View.GONE);
        }

        if (!hasSessions && mTermuxService != null && !mTermuxService.wantsToStop()) {
            Intent stopServiceIntent = new Intent(requireActivity(), TermuxService.class);
            stopServiceIntent.setAction(com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE);
            requireActivity().startService(stopServiceIntent);
        }
    }

    public boolean isVisibles() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionFragmentClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        return mTerminalView != null ? mTerminalView.getCurrentSession() : null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setTerminalToolbarHeight();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(requireActivity());
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();
        if (recreateActivity) {
            requireActivity().recreate();
        }
    }

    public static TermuxFragment newInstance(@Nullable Bundle args) {
        TermuxFragment fragment = new TermuxFragment();
        if (args != null) fragment.setArguments(args);
        return fragment;
    }

    public static TermuxFragment newInstance() {
        return newInstance(null);
    }
}
