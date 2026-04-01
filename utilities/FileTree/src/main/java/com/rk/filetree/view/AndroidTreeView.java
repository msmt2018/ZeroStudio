package com.rk.filetree.view;

import android.content.Context;
import androidx.annotation.DrawableRes;
import com.rk.filetree.model.TreeNode;

/** Compatibility wrapper for legacy treeview AndroidTreeView API. */
public class AndroidTreeView extends com.unnamed.b.atv.view.AndroidTreeView {

  public AndroidTreeView(Context context, TreeNode root, @DrawableRes int nodeBackground) {
    super(context, root, nodeBackground);
  }
}
