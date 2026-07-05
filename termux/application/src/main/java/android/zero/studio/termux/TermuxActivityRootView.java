package android.zero.studio.termux;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * A compatibility wrapper for the root view that inherits from {@link ZeroKeyboardInsetsLayout}.
 *
 * @author android_zero
 */
public class TermuxActivityRootView extends ZeroKeyboardInsetsLayout {

    /**
     * Legacy reference.
     */
    @Deprecated
    public TermuxFragment mFragment;

    public TermuxActivityRootView(@NonNull Context context) {
        super(context);
    }

    public TermuxActivityRootView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TermuxActivityRootView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setFragment(TermuxFragment fragment) {
        mFragment = fragment;
    }

    public void setIsRootViewLoggingEnabled(boolean value) {
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Delegate insets handling to parent class logic
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, windowInsets) -> {
            return onApplyWindowInsets(windowInsets);
        });
    }

    public int getNavBarHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = getRootWindowInsets();
            if (insets != null) {
                return WindowInsetsCompat.toWindowInsetsCompat(insets)
                        .getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
        }
        return 0;
    }
}