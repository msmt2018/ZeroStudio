package android.zero.studio.termux.io;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import android.zero.studio.termux.TermuxFragment;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.terminal.TerminalSession;

/**
 * Manages the ViewPager for the terminal toolbar, which includes the extra keys view and a text input field.
 *
 * This class has been refactored to depend on {@link TermuxFragment} instead of {@link android.zero.studio.termux.TermuxFragment},
 * making it usable within a flexible Fragment-based architecture.
 */
public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxFragment mFragment;
        String mSavedTextInput;

        /**
         * Constructor for the PageAdapter.
         * @param fragment The hosting {@link TermuxFragment}.
         * @param savedTextInput The saved text to restore in the input field, can be null.
         */
        public PageAdapter(@NonNull TermuxFragment fragment, String savedTextInput) {
            this.mFragment = fragment;
            this.mSavedTextInput = savedTextInput;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mFragment.requireContext());
            View layout;
            if (position == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mFragment.getTermuxTerminalExtraKeys());
                extraKeysView.setButtonTextAllCaps(mFragment.getProperties().shouldExtraKeysTextBeAllCaps());
                mFragment.setExtraKeysView(extraKeysView);
                extraKeysView.reload(mFragment.getTermuxTerminalExtraKeys().getExtraKeysInfo(),
                    mFragment.getTerminalToolbarDefaultHeight());

                // apply extra keys fix if enabled in prefs
                // if (mFragment.getProperties().isUsingFullScreen() && mFragment.getProperties().isUsingFullScreenWorkAround()) {
                    // FullScreenWorkAround.apply(mFragment);
                // }

            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false);
                final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }

                editText.setOnEditorActionListener((v, actionId, event) -> {
                    TerminalSession session = mFragment.getCurrentSession();
                    if (session != null) {
                        if (session.isRunning()) {
                            String textToSend = editText.getText().toString();
                            if (textToSend.length() == 0) textToSend = "\r";
                            session.write(textToSend);
                        } else {
                            mFragment.getTermuxTerminalSessionClient().removeFinishedSession(session);
                        }
                        editText.setText("");
                    }
                    return true;
                });
            }
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            collection.removeView((View) view);
        }

    }



    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        final TermuxFragment mFragment;
        final ViewPager mTerminalToolbarViewPager;

        /**
         * Constructor for the OnPageChangeListener.
         * @param fragment The hosting {@link TermuxFragment}.
         * @param viewPager The {@link ViewPager} this listener is attached to.
         */
        public OnPageChangeListener(@NonNull TermuxFragment fragment, @NonNull ViewPager viewPager) {
            this.mFragment = fragment;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (position == 0) {
                if (mFragment.getTerminalView() != null)
                    mFragment.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}