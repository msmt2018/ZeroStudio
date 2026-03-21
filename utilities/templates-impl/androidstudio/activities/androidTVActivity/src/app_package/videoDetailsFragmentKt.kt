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
 * Generates Kotlin source code for VideoDetailsFragment in Android TV project.
 *
 * @author Historical contributors (The Android Open Source Project)
 * @author android_zero
 */
fun videoDetailsFragmentKt(
  activityClass: String,
  detailsActivity: String,
  detailsFragment: String,
  minApiLevel: Int,
  packageName: String,
): String {
  val contextArgBlock = if (minApiLevel >= 23) "requireContext()" else "requireActivity()"
  return """
package ${packageName}

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.graphics.drawable.Drawable
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

import java.util.Collections

/**
 * A wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
class ${detailsFragment} : DetailsSupportFragment() {

    private lateinit var mDetailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var mPresenterSelector: ClassPresenterSelector
    private lateinit var mAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate DetailsFragment")
        super.onCreate(savedInstanceState)

        mDetailsBackground = DetailsSupportFragmentBackgroundController(this)

        var selectedMovie: Movie? = activity?.intent?.getSerializableExtra(${detailsActivity}.MOVIE) as Movie?
        if (selectedMovie != null) {
            mPresenterSelector = ClassPresenterSelector()
            mAdapter = ArrayObjectAdapter(mPresenterSelector)
            setupDetailsOverviewRow(selectedMovie)
            setupDetailsOverviewRowPresenter(selectedMovie)
            setupRelatedMovieListRow()
            adapter = mAdapter
            initializeBackground(selectedMovie)
            onItemViewClickedListener = ItemViewClickedListener(selectedMovie)
        } else {
            val intent = Intent($contextArgBlock, ${activityClass}::class.java)
            startActivity(intent)
        }
    }

    private fun initializeBackground(movie: Movie) {
        mDetailsBackground.enableParallax()
        Glide.with($contextArgBlock)
            .asBitmap()
            .centerCrop()
            .error(R.drawable.default_background)
            .load(movie.backgroundImageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    bitmap: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    mDetailsBackground.coverBitmap = bitmap
                    mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun setupDetailsOverviewRow(movie: Movie) {
        Log.d(TAG, "doInBackground: " + movie.toString())
        val row = DetailsOverviewRow(movie)
        row.imageDrawable = ContextCompat.getDrawable($contextArgBlock, R.drawable.default_background)
        val width = convertDpToPixel($contextArgBlock, DETAIL_THUMB_WIDTH)
        val height = convertDpToPixel($contextArgBlock, DETAIL_THUMB_HEIGHT)
        Glide.with($contextArgBlock)
            .load(movie.cardImageUrl)
            .centerCrop()
            .error(R.drawable.default_background)
            .into(object : CustomTarget<Drawable>(width, height) {
                override fun onResourceReady(
                    drawable: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    Log.d(TAG, "details overview card image url ready: " + drawable)
                    row.imageDrawable = drawable
                    mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })

        val actionAdapter = ArrayObjectAdapter()

        actionAdapter.add(
            Action(
                ACTION_WATCH_TRAILER,
                resources.getString(R.string.watch_trailer_1),
                resources.getString(R.string.watch_trailer_2)
            )
        )
        actionAdapter.add(
            Action(
                ACTION_RENT,
                resources.getString(R.string.rent_1),
                resources.getString(R.string.rent_2)
            )
        )
        actionAdapter.add(
            Action(
                ACTION_BUY,
                resources.getString(R.string.buy_1),
                resources.getString(R.string.buy_2)
            )
        )
        row.actionsAdapter = actionAdapter

        mAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter(movie: Movie) {
        // Set detail background.
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
            ContextCompat.getColor($contextArgBlock, R.color.selected_background)

        // Hook up transition element.
        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(
            activity, ${detailsActivity}.SHARED_ELEMENT_NAME
        )
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            if (action.id == ACTION_WATCH_TRAILER) {
                val intent = Intent($contextArgBlock, PlaybackActivity::class.java)
                intent.putExtra(${detailsActivity}.MOVIE, movie)
                startActivity(intent)
            } else {
                Toast.makeText($contextArgBlock, action.toString(), Toast.LENGTH_SHORT).show()
            }
        }
        mPresenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
    }

    private fun setupRelatedMovieListRow() {
        val subcategories = arrayOf(getString(R.string.related_movies))
        val list = MovieList.list

        Collections.shuffle(list)
        val listRowAdapter = ArrayObjectAdapter(CardPresenter())
        for (j in 0 until NUM_COLS) {
            listRowAdapter.add(list[j % 5])
        }

        val header = HeaderItem(0, subcategories[0])
        mAdapter.add(ListRow(header, listRowAdapter))
        mPresenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
    }

    private fun convertDpToPixel(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    private inner class ItemViewClickedListener(private val movie: Movie) : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (item is Movie) {
                Log.d(TAG, "Item: " + item.toString())
                val intent = Intent($contextArgBlock, ${detailsActivity}::class.java)
                intent.putExtra(resources.getString(R.string.movie), movie)

                val bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        (itemViewHolder.view as ImageCardView).mainImageView!!,
                        ${detailsActivity}.SHARED_ELEMENT_NAME
                    )
                        .toBundle()
                requireActivity().startActivity(intent, bundle)
            }
        }
    }

    companion object {
        private const val TAG = "${detailsFragment.take(23)}"

        private const val ACTION_WATCH_TRAILER = 1L
        private const val ACTION_RENT = 2L
        private const val ACTION_BUY = 3L

        private const val DETAIL_THUMB_WIDTH = 274
        private const val DETAIL_THUMB_HEIGHT = 274

        private const val NUM_COLS = 10
    }
}
"""
}