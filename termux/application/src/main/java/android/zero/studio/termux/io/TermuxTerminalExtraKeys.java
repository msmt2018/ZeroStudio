package android.zero.studio.termux.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;

import android.zero.studio.termux.TermuxFragment;
import android.zero.studio.termux.terminal.TermuxTerminalSessionFragmentClient;
import android.zero.studio.termux.terminal.TermuxTerminalViewClient;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;
import org.json.JSONException;

import com.google.android.material.button.MaterialButton;

/**
 * An implementation of {@link TerminalExtraKeys} that handles Termux-specific extra key actions.
 * Refactored to work with TermuxFragment.
 * @author android_zero
 */
public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxFragment mFragment;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionFragmentClient mTermuxTerminalSessionFragmentClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    /**
     * Constructor for TermuxTerminalExtraKeys.
     *
     * @param fragment The hosting {@link TermuxFragment}.
     * @param terminalView The {@link TerminalView} this client is associated with.
     * @param termuxTerminalViewClient The client for terminal view events.
     * @param TermuxTerminalSessionFragmentClient The client for terminal session events.
     */
    public TermuxTerminalExtraKeys(@NonNull TermuxFragment fragment, @NonNull TerminalView terminalView,
                                   @NonNull TermuxTerminalViewClient termuxTerminalViewClient,
                                   @NonNull TermuxTerminalSessionFragmentClient termuxTerminalSessionFragmentClient) {
        super(terminalView);

        mFragment = fragment;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionFragmentClient = termuxTerminalSessionFragmentClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys and style by loading them from properties.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link TermuxSharedProperties#getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link TermuxSharedProperties#getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = (String) mFragment.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mFragment.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mFragment.getActivity(), "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mFragment.getActivity(), "Can't create default extra keys", true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();

        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionFragmentClient != null)
                mTermuxTerminalSessionFragmentClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mFragment.getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }
    
    @Override
    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        // Override to handle macros properly if needed, otherwise default behavior from TerminalExtraKeys/ExtraKeysView
        if (buttonInfo.isMacro()) {
            String[] keys = buttonInfo.getKey().split(" ");
            boolean ctrlDown = false;
            boolean altDown = false;
            boolean shiftDown = false;
            boolean fnDown = false;
            for (String key : keys) {
                if (SpecialButton.CTRL.getKey().equals(key)) {
                    ctrlDown = true;
                } else if (SpecialButton.ALT.getKey().equals(key)) {
                    altDown = true;
                } else if (SpecialButton.SHIFT.getKey().equals(key)) {
                    shiftDown = true;
                } else if (SpecialButton.FN.getKey().equals(key)) {
                    fnDown = true;
                } else {
                    onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
                    // Reset modifiers after a key press
                    ctrlDown = false; altDown = false; shiftDown = false; fnDown = false;
                }
            }
        } else {
            onTerminalExtraKeyButtonClick(view, buttonInfo.getKey(), false, false, false, false);
        }
    }

}