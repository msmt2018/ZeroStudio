package android.zero.studio.termux.ui.tabs.callback

import android.graphics.Canvas
import android.zero.studio.termux.ui.tabs.utils.TabDragAnimationHelper
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/** ItemTouchHelper callback for tab drag and reorder functionality */
class TabItemTouchHelperCallback(private val adapter: ItemMoveCallback) :
    ItemTouchHelper.Callback() {

  interface ItemMoveCallback {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean

    fun onItemDragStarted(viewHolder: RecyclerView.ViewHolder)

    fun onItemDragEnded(viewHolder: RecyclerView.ViewHolder)
  }

  override fun getMovementFlags(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
  ): Int {
    val dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    val swipeFlags = 0 // No swipe
    return makeMovementFlags(dragFlags, swipeFlags)
  }

  override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder,
  ): Boolean {
    return adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
  }

  override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    // Not used - swipe disabled
  }

  override fun isLongPressDragEnabled(): Boolean = true

  override fun isItemViewSwipeEnabled(): Boolean = false

  override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
    super.onSelectedChanged(viewHolder, actionState)

    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
      adapter.onItemDragStarted(viewHolder)
      TabDragAnimationHelper.animatePickUp(viewHolder.itemView)
    }
  }

  override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
    super.clearView(recyclerView, viewHolder)

    adapter.onItemDragEnded(viewHolder)
    TabDragAnimationHelper.animateDrop(viewHolder.itemView)
  }

  override fun onChildDraw(
      c: Canvas,
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      dX: Float,
      dY: Float,
      actionState: Int,
      isCurrentlyActive: Boolean,
  ) {
    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
      // Apply translation during drag
      viewHolder.itemView.translationX = dX
      // Optional: visual effect
      viewHolder.itemView.alpha = 0.5f
    } else {
      super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
  }

  override fun getAnimationDuration(
      recyclerView: RecyclerView,
      animationType: Int,
      animateDx: Float,
      animateDy: Float,
  ): Long {
    return 250L // Match animation duration
  }
}
