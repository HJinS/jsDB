package index.btree

/**
 * Which way a [BTree.search]/[Cursor] walk moves through leaves
 * - always byte-ascending ([FORWARD]) or byte-descending ([BACKWARD]);
 * DESC key columns are already bit-flipped at encoding time, so this is the only direction concept the tree itself needs.
 * */
enum class ScanDirection {
    FORWARD,
    BACKWARD,
}
