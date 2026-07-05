package android.zero.studio.termux;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.Logger;

/**
 * The main activity for the Termux application.
 *
 * This activity's primary role is to host the {@link TermuxFragment}, which contains the
 * terminal user interface and logic. It handles activity lifecycle events, passes intents,
 * and delegates user interactions like back presses and menu item selections to the fragment.
 */
public class TermuxActivity extends AppCompatActivity {

    private TermuxFragment mTermuxFragment;

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.logDebug(LOG_TAG, "onCreate");
        setContentView(R.layout.activity_termux_host);

        if (savedInstanceState == null) {
            mTermuxFragment = TermuxFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mTermuxFragment)
                .commit();
        } else {
            // Fragment will be automatically restored by the FragmentManager
            mTermuxFragment = (TermuxFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Logger.logDebug(LOG_TAG, "onNewIntent");

        // The fragment may not have been created yet, so check for null.
        if (mTermuxFragment != null) {
            // Let the fragment handle the new intent.
             if(mTermuxFragment.isAdded()) {
                 // Forward new intent to the fragment
                 // This will trigger onNewIntent in the fragment if implemented,
                 // but since we don't have that, we'll manually handle it or pass data.
                 // For now, we assume the fragment will get the latest intent via getActivity().getIntent()
                 // in its onResume or other lifecycle methods if needed.
             }
        }
    }


    @Override
    public void onBackPressed() {
        // Delegate the back press event to the fragment.
        // If the fragment handles it (e.g., closes the drawer), it returns true.
        // Otherwise, perform the default back action.
        if (mTermuxFragment == null || !mTermuxFragment.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mTermuxFragment != null && mTermuxFragment.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mTermuxFragment != null && mTermuxFragment.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Delegate options menu creation to the fragment.
        if (mTermuxFragment != null) {
            mTermuxFragment.onCreateOptionsMenu(menu, getMenuInflater());
        }
        return false; // Return false to prevent the activity's options menu from being shown
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Delegate options menu item selection to the fragment.
        if (mTermuxFragment != null && mTermuxFragment.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        // Delegate context menu item selection to the fragment.
        if (mTermuxFragment != null && mTermuxFragment.onContextItemSelected(item)) {
            return true;
        }
        return super.onContextItemSelected(item);
    }


    /**
     * Start the TermuxActivity.
     * @param context The context to start the activity from.
     */
    public static void startTermuxActivity(@NonNull final Context context) {
        context.startActivity(newInstance(context));
    }

    /**
     * Create a new Intent to start the TermuxActivity.
     * @param context The context to create the intent with.
     * @return A new Intent for TermuxActivity.
     */
    @NonNull
    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
