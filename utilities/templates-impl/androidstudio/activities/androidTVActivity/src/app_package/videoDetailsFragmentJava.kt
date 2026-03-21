/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itsaky.androidide.templates.impl.androidstudio.activities.androidTVActivity.src.app_package

/**
 * Generates Java source code for VideoDetailsFragment in Android TV project.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun videoDetailsFragmentJava(
  activityClass: String,
  detailsActivity: String,
  detailsFragment: String,
  minApiLevel: Int,
  packageName: String,
): String {
  val contextArgBlock = if (minApiLevel >= 23) "getContext()" else "getActivity()"

  return """
package ${packageName};

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.Collections;
import java.util.List;

/*
 * LeanbackDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its meta plus related videos.
 */
public class ${detailsFragment} extends DetailsSupportFragment {
    private static final String TAG = "${detailsFragment.take(23)}";

    private static final int ACTION_WATCH_TRAILER = 1;
    private static final int ACTION_RENT = 2;
    private static final int ACTION_BUY = 3;

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int NUM_COLS = 10;

    private Movie mSelectedMovie;

    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private DetailsSupportFragmentBackgroundController mDetailsBackground;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate DetailsFragment");
        super.onCreate(savedInstanceState);

        mDetailsBackground = new DetailsSupportFragmentBackgroundController(this);

        mSelectedMovie =
                (Movie) getActivity().getIntent() .getSerializableExtra(${detailsActivity}.MOVIE);
        if (mSelectedMovie != null) {
            mPresenterSelector = new ClassPresenterSelector();
            mAdapter = new ArrayObjectAdapter(mPresenterSelector);
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            setupRelatedMovieListRow();
            setAdapter(mAdapter);
            initializeBackground(mSelectedMovie);
            setOnItemViewClickedListener(new ItemViewClickedListener());
        } else {
            Intent intent = new Intent(getActivity(), ${activityClass}.class);
            startActivity(intent);
        }
    }

    private void initializeBackground(Movie data) {
        mDetailsBackground.enableParallax();
        Glide.with(getActivity())
                .asBitmap()
                .centerCrop()
                .error(R.drawable.default_background)
                .load(data.getBackgroundImageUrl())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap,
                                                @Nullable Transition<? super Bitmap> transition) {
                        mDetailsBackground.setCoverBitmap(bitmap);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) { }
                });
    }

    private void setupDetailsOverviewRow() {
        Log.d(TAG, "doInBackground: " + mSelectedMovie.toString());
        final DetailsOverviewRow row = new DetailsOverviewRow(mSelectedMovie);
        row.setImageDrawable(
                ContextCompat.getDrawable($contextArgBlock, R.drawable.default_background));
        int width = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_WIDTH);
        int height = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_HEIGHT);
        Glide.with(getActivity())
                .load(mSelectedMovie.getCardImageUrl())
                .centerCrop()
                .error(R.drawable.default_background)
                .into(new CustomTarget<Drawable>(width, height) {
                    @Override
                    public void onResourceReady(@NonNull Drawable drawable,
                                                @Nullable Transition<? super Drawable> transition) {
                        Log.d(TAG, "details overview card image url ready: " + drawable);
                        row.setImageDrawable(drawable);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) { }
                });

        ArrayObjectAdapter actionAdapter = new ArrayObjectAdapter();

        actionAdapter.add(
                new Action(
                        ACTION_WATCH_TRAILER,
                        getResources().getString(R.string.watch_trailer_1),
                        getResources().getString(R.string.watch_trailer_2)));
        actionAdapter.add(
                new Action(
                        ACTION_RENT,
                        getResources().getString(R.string.rent_1),
                        getResources().getString(R.string.rent_2)));
        actionAdapter.add(
                new Action(
                        ACTION_BUY,
                        getResources().getString(R.string.buy_1),
                        getResources().getString(R.string.buy_2)));
        row.setActionsAdapter(actionAdapter);

        mAdapter.add(row);
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background.
        FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor($contextArgBlock, R.color.selected_background));

        // Hook up transition element.
        FullWidthDetailsOverviewSharedElementHelper sharedElementHelper =
                new FullWidthDetailsOverviewSharedElementHelper();
        sharedElementHelper.setSharedElementEnterTransition(
                getActivity(), ${detailsActivity}.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(sharedElementHelper);
        detailsPresenter.setParticipatingEntranceTransition(true);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_WATCH_TRAILER) {
                    Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                    intent.putExtra(${detailsActivity}.MOVIE, mSelectedMovie);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), action.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupRelatedMovieListRow() {
        String subcategories[] = {getString(R.string.related_movies)};
        List<Movie> list = MovieList.getList();

        Collections.shuffle(list);
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        for (int j = 0; j < NUM_COLS; j++) {
            listRowAdapter.add(list.get(j % 5));
        }

        HeaderItem header = new HeaderItem(0, subcategories[0]);
        mAdapter.add(new ListRow(header, listRowAdapter));
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    private int convertDpToPixel(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Movie) {
                Log.d(TAG, "Item: " + item.toString());
                Intent intent = new Intent(getActivity(), ${detailsActivity}.class);
                intent.putExtra(getResources().getString(R.string.movie), mSelectedMovie);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            ${detailsActivity}.SHARED_ELEMENT_NAME)
                        .toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }
}
"""
}