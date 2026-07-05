package android.zero.studio.termux.app;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.greenrobot.eventbus.EventBus;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.JobKt;
import java.util.concurrent.CancellationException;

/**
 * Base Fragment for the terminal application.
 * It handles event bus registration and CoroutineScope management.
 * This class has been refactored to be independent of any specific IDE or theme implementation.
 */
public abstract class BaseIDEFragment extends Fragment {

    /**
     * Flag to indicate if the fragment should subscribe to EventBus events.
     * Subclasses can override this by setting it to `true` in their `onCreate` or `onAttach`.
     */
    protected boolean subscribeToEvents = false;

    /**
     * CoroutineScope for executing tasks with the Default dispatcher.
     * It's tied to the fragment's lifecycle.
     */
    protected final CoroutineScope fragmentScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getDefault());

    @Override
    public void onAttach(@NonNull Context context) {
        // Removed AndroidIDE-specific theme logic to make the fragment generic.
        // The hosting Activity is now responsible for setting its own theme.
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        preInflateLayout();
        return bindLayout(inflater, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Any logic that needs to run after the view is created can go here.
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel the coroutine scope when the view is destroyed, using the standard Kotlin-Java interop way.
        JobKt.cancel(fragmentScope.getCoroutineContext(), new CancellationException("Fragment view is being destroyed"));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (subscribeToEvents && !EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    /**
     * Loads a fragment into the specified container view within the current fragment's child FragmentManager.
     *
     * @param fragment The fragment to load.
     * @param containerId The ID of the container view.
     */
    protected void loadFragment(Fragment fragment, int containerId) {
        // Using getChildFragmentManager() is appropriate if the container is inside this fragment's layout.
        // Use getParentFragmentManager() if you need to replace a fragment at the same level in the activity.
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(containerId, fragment);
        transaction.commit();
    }

    /**
     * A hook to run code before the layout is inflated in onCreateView.
     */
    protected void preInflateLayout() {}

    /**
     * Subclasses must implement this to provide the layout for the fragment.
     *
     * @param inflater The LayoutInflater to use for inflating the view.
     * @param container The parent view that the fragment's UI should be attached to.
     * @return The root view for the fragment's layout.
     */
    @NonNull
    protected abstract View bindLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);
}