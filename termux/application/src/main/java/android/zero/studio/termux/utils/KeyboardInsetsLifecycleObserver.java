package android.zero.studio.termux.util;

import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import android.zero.studio.termux.TermuxFragment;
import com.termux.shared.logger.Logger;

import java.util.List;

/**
 * A lifecycle-aware component that manages the soft keyboard and window insets for a {@link TermuxFragment}.
 * It aims to provide a stable keyboard experience, preventing the keyboard from being dismissed unexpectedly
 * due to layout changes or focus shifts.
 */
public class KeyboardInsetsLifecycleObserver implements DefaultLifecycleObserver {

    private final TermuxFragment mFragment;
    private final Window mWindow;
    private final View mRootView;

    private boolean isKeyboardVisible = false;

    private static final String LOG_TAG = "KeyboardInsetsLifecycleObserver";

    public KeyboardInsetsLifecycleObserver(@NonNull TermuxFragment fragment) {
        this.mFragment = fragment;
        this.mWindow = fragment.requireActivity().getWindow();
        this.mRootView = fragment.getView(); // Get the fragment's root view
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        // Set the appropriate window soft input mode when the fragment is resumed.
        // ADJUST_RESIZE allows the layout to resize when the keyboard appears.
        mWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setupInsetsListener();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        // Clean up listeners when the fragment is paused.
        ViewCompat.setOnApplyWindowInsetsListener(mRootView, null);
        ViewCompat.setWindowInsetsAnimationCallback(mRootView, null);
    }

    private void setupInsetsListener() {
        if (mRootView == null) return;

        // Listener for changes in window insets (like keyboard visibility)
        ViewCompat.setOnApplyWindowInsetsListener(mRootView, (v, insets) -> {
            boolean wasVisible = isKeyboardVisible;
            isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            if (isKeyboardVisible != wasVisible) {
                Logger.logDebug(LOG_TAG, "Keyboard visibility changed to: " + isKeyboardVisible);
                // Optionally, you can add logic here to run when keyboard visibility changes.
            }

            // Let the system handle the rest of the insets application.
            return ViewCompat.onApplyWindowInsets(v, insets);
        });

        // Animation callback for smoother keyboard transitions (optional but recommended)
        ViewCompat.setWindowInsetsAnimationCallback(mRootView, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                return insets;
            }
        });
    }

    /**
     * Public method to request showing the soft keyboard.
     */
    public void showSoftKeyboard() {
        if (mFragment.getTerminalView() != null) {
            WindowCompat.getInsetsController(mWindow, mFragment.getTerminalView()).show(WindowInsetsCompat.Type.ime());
        }
    }

    /**
     * Public method to request hiding the soft keyboard.
     */
    public void hideSoftKeyboard() {
         if (mFragment.getTerminalView() != null) {
            WindowCompat.getInsetsController(mWindow, mFragment.getTerminalView()).hide(WindowInsetsCompat.Type.ime());
        }
    }

    /**
     * Toggles the soft keyboard's visibility.
     */
    public void toggleSoftKeyboard() {
        if (isKeyboardVisible) {
            hideSoftKeyboard();
        } else {
            showSoftKeyboard();
        }
    }

    /**
     * Re-asserts focus on the terminal view. This can be called if focus is unexpectedly lost.
     */
    public void ensureFocus() {
        if (mFragment.getTerminalView() != null && !mFragment.getTerminalView().hasFocus()) {
            Logger.logDebug(LOG_TAG, "Forcing focus back to TerminalView.");
            mFragment.getTerminalView().requestFocus();
        }
    }
}