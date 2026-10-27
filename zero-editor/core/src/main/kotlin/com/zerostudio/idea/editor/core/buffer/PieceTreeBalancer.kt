package com.zerostudio.idea.editor.core.buffer

/**
 * Piece Tree 红黑树平衡维护器
 * 保证树深严格小于 2 * log2(N + 1)，在亿级节点下最大寻址深度不超过 64 次跳转
 */
internal object PieceTreeBalancer {

    fun fixAfterInsert(root: PieceNode?, inserted: PieceNode): PieceNode {
        var x: PieceNode? = inserted
        var currentRoot = root
        x?.color = PieceNode.RED

        while (x != null && x != currentRoot && x.parent?.color == PieceNode.RED) {
            val parent = x.parent!!
            val grandParent = parent.parent ?: break

            if (parent == grandParent.left) {
                val uncle = grandParent.right
                if (uncle != null && uncle.color == PieceNode.RED) {
                    parent.color = PieceNode.BLACK
                    uncle.color = PieceNode.BLACK
                    grandParent.color = PieceNode.RED
                    x = grandParent
                } else {
                    if (x == parent.right) {
                        x = parent
                        currentRoot = rotateLeft(currentRoot, x)
                    }
                    x.parent?.color = PieceNode.BLACK
                    x.parent?.parent?.color = PieceNode.RED
                    if (x.parent?.parent != null) {
                        currentRoot = rotateRight(currentRoot, x.parent!!.parent!!)
                    }
                }
            } else {
                val uncle = grandParent.left
                if (uncle != null && uncle.color == PieceNode.RED) {
                    parent.color = PieceNode.BLACK
                    uncle.color = PieceNode.BLACK
                    grandParent.color = PieceNode.RED
                    x = grandParent
                } else {
                    if (x == parent.left) {
                        x = parent
                        currentRoot = rotateRight(currentRoot, x)
                    }
                    x.parent?.color = PieceNode.BLACK
                    x.parent?.parent?.color = PieceNode.RED
                    if (x.parent?.parent != null) {
                        currentRoot = rotateLeft(currentRoot, x.parent!!.parent!!)
                    }
                }
            }
        }
        currentRoot?.color = PieceNode.BLACK
        return currentRoot ?: inserted
    }

    private fun rotateLeft(root: PieceNode?, p: PieceNode): PieceNode {
        val r = p.right ?: return root ?: p
        p.right = r.left
        if (r.left != null) {
            r.left!!.parent = p
        }
        r.parent = p.parent
        when {
            p.parent == null -> { /* r 成为新根 */ }
            p.parent!!.left == p -> p.parent!!.left = r
            else -> p.parent!!.right = r
        }
        r.left = p
        p.parent = r

        // 旋转后重新计算受影响节点的字符与行号权重
        p.updateSubtreeWeights()
        r.updateSubtreeWeights()

        return if (p == root) r else root!!
    }

    private fun rotateRight(root: PieceNode?, p: PieceNode): PieceNode {
        val l = p.left ?: return root ?: p
        p.left = l.right
        if (l.right != null) {
            l.right!!.parent = p
        }
        l.parent = p.parent
        when {
            p.parent == null -> { /* l 成为新根 */ }
            p.parent!!.right == p -> p.parent!!.right = l
            else -> p.parent!!.left = l
        }
        l.right = p
        p.parent = l

        // 旋转后重新计算受影响节点的字符与行号权重
        p.updateSubtreeWeights()
        l.updateSubtreeWeights()

        return if (p == root) l else root!!
    }
}