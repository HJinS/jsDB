package index.btree

/** Which operation a [BTree] descent is performing — passed to [index.btree.node.Node.isSafeNode] to decide latch-crabbing ancestor release. */
enum class BTreeOptMode {
    SELECT,
    INSERT,
    DELETE,
    UPDATE
}
