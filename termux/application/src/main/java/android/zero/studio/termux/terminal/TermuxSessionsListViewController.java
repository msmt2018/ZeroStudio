package android.zero.studio.termux.terminal;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import android.zero.studio.termux.TermuxFragment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.terminal.TerminalSession;
import java.util.List;

/**
 * An {@link ArrayAdapter} for displaying a list of {@link TermuxSession} instances in a ListView.
 * It also handles item clicks and long clicks to switch or rename sessions.
 *
 * This class has been refactored to depend on {@link TermuxFragment} instead of a concrete Activity,
 * allowing it to function within a more modular UI structure.
 */
public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxFragment mFragment;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    /**
     * Constructor for the TermuxSessionsListViewController.
     * @param fragment The hosting {@link TermuxFragment}.
     * @param sessionList The list of {@link TermuxSession} to display.
     */
    public TermuxSessionsListViewController(@NonNull TermuxFragment fragment, @NonNull List<TermuxSession> sessionList) {
        super(fragment.requireContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mFragment = fragment;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = LayoutInflater.from(mFragment.requireContext());
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);

        TermuxSession termuxSession = getItem(position);
        if (termuxSession == null) {
            sessionTitleView.setText("null termux session");
            return sessionRowView;
        }

        TerminalSession sessionAtRow = termuxSession.getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        boolean shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(mFragment.requireActivity(), NightMode.getAppNightMode().getName());

        if (shouldEnableDarkTheme) {
            sessionTitleView.setBackground(
                ContextCompat.getDrawable(mFragment.requireActivity(), R.drawable.session_background_black_selected)
            );
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = shouldEnableDarkTheme ? Color.WHITE : Color.BLACK;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);
        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        if (clickedSession == null) return;
        mFragment.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());

    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        if (selectedSession == null) return false;
        mFragment.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }
}