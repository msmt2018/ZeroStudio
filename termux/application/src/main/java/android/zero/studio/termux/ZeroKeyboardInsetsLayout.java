package android.zero.studio.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * A root layout that handles System Bars (Status Bar / Nav Bar) insets by applying padding.
 *
 * @author android_zero
 */
public class ZeroKeyboardInsetsLayout extends FrameLayout {

    public ZeroKeyboardInsetsLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public ZeroKeyboardInsetsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ZeroKeyboardInsetsLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // We handle specific padding manually.
        setFitsSystemWindows(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewCompat.requestApplyInsets(this);
    }

    public WindowInsetsCompat onApplyWindowInsets(WindowInsetsCompat insets) {
       // Get System Bars (Status Bar + Navigation Bar)
        int typeSystemBars = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
        Insets systemBars = insets.getInsets(typeSystemBars);

        // Check if Keyboard (IME) is visible
        boolean isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

        // Top Padding: 
        // Force to 0. The hosting Activity already handles the status bar area.
        // Adding systemBars.top here would create the double gap seen in the screenshot.
        int paddingTop = 0;

        // Bottom Padding:
        // - If Keyboard IS visible: Set 0. System 'adjustResize' pushes the view up.
        //   Adding nav bar padding here creates a white strip above the keyboard.
        // - If Keyboard IS NOT visible: Apply nav bar height so content isn't hidden behind the gesture bar.
        int paddingBottom = isImeVisible ? 0 : systemBars.bottom;

        // Apply padding if changed
        if (getPaddingLeft() != systemBars.left || 
            getPaddingTop() != paddingTop || 
            getPaddingRight() != systemBars.right || 
            getPaddingBottom() != paddingBottom) {
            
            setPadding(
                systemBars.left, 
                paddingTop, 
                systemBars.right, 
                paddingBottom
            );
        }

        return WindowInsetsCompat.CONSUMED;
    }
}