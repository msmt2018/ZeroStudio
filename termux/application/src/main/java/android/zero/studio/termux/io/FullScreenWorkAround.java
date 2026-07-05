package android.zero.studio.termux.io;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import android.zero.studio.termux.TermuxFragment;

/**
 * Work around for fullscreen mode in Termux to fix ExtraKeysView not being visible.
 * This class is derived from:
 * https://stackoverflow.com/questions/7417123/android-how-to-adjust-layout-in-full-screen-mode-when-softkeyboard-is-visible
 * and has some additional tweaks.
 * ---
 * For more information, see https://issuetracker.google.com/issues/36911528
 *
 * This class is refactored to depend on a TermuxFragment instead of a specific TermuxActivity,
 * making it more modular and reusable within any hosting Activity.
 */
public class FullScreenWorkAround {
    private final View mChildOfContent;
    private int mUsableHeightPrevious;
    private final ViewGroup.LayoutParams mViewGroupLayoutParams;
    private final int mNavBarHeight;

    /**
     * Applies the workaround to the given fragment's activity.
     * @param fragment The TermuxFragment that needs the workaround.
     */
    public static void apply(@NonNull TermuxFragment fragment) {
        new FullScreenWorkAround(fragment);
    }

    /**
     * Private constructor to set up the global layout listener.
     * @param fragment The TermuxFragment instance.
     */
    private FullScreenWorkAround(@NonNull TermuxFragment fragment) {
        // We get the root content view from the fragment's hosting activity.
        ViewGroup content = fragment.requireActivity().findViewById(android.R.id.content);
        if (content.getChildCount() > 0) {
            mChildOfContent = content.getChildAt(0);
            mViewGroupLayoutParams = mChildOfContent.getLayoutParams();
            // Get the navigation bar height from the fragment.
            mNavBarHeight = fragment.getNavBarHeight();
            mChildOfContent.getViewTreeObserver().addOnGlobalLayoutListener(this::possiblyResizeChildOfContent);
        } else {
            // Handle case where content view has no children, to prevent crashes.
            mChildOfContent = null;
            mViewGroupLayoutParams = null;
            mNavBarHeight = 0;
        }
    }

    private void possiblyResizeChildOfContent() {
        if (mChildOfContent == null) return;

        int usableHeightNow = computeUsableHeight();
        if (usableHeightNow != mUsableHeightPrevious) {
            int usableHeightSansKeyboard = mChildOfContent.getRootView().getHeight();
            int heightDifference = usableHeightSansKeyboard - usableHeightNow;
            if (heightDifference > (usableHeightSansKeyboard / 4)) {
                // keyboard probably just became visible

                // ensures that usable layout space does not extend behind the
                // soft keyboard, causing the extra keys to not be visible
                mViewGroupLayoutParams.height = (usableHeightSansKeyboard - heightDifference) + getNavBarHeight();
            } else {
                // keyboard probably just became hidden
                mViewGroupLayoutParams.height = usableHeightSansKeyboard;
            }
            mChildOfContent.requestLayout();
            mUsableHeightPrevious = usableHeightNow;
        }
    }

    private int getNavBarHeight() {
        return mNavBarHeight;
    }

    private int computeUsableHeight() {
        if (mChildOfContent == null) return 0;
        Rect r = new Rect();
        mChildOfContent.getWindowVisibleDisplayFrame(r);
        return (r.bottom - r.top);
    }
}