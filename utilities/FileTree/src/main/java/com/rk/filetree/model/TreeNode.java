package com.rk.filetree.model;

import java.io.File;

/** Compatibility wrapper for legacy treeview TreeNode API. */
public class TreeNode extends com.unnamed.b.atv.model.TreeNode {

  public TreeNode(File value) {
    super(value);
  }

  public static TreeNode root() {
    return root(null);
  }

  public static TreeNode root(File value) {
    TreeNode root = new TreeNode(value);
    root.setSelectable(false);
    return root;
  }
}
