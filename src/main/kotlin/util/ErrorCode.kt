package util

/**
 * Every distinct failure this engine reports. The constant itself is what tells two failures apart
 * (what code and tests branch on); [sqlState] is the standard SQLSTATE it maps to, kept so the
 * eventual client protocol can hand a standard code back - several constants may share one
 * SQLSTATE (e.g. the three ORDER BY errors are all `42P10`).
 *
 * SQLSTATE classes in use:
 * - 02 (No Data): no row to select/update/delete.
 * - 0A (Feature Not Supported): valid request this engine doesn't implement yet.
 * - 22 (Data Exception): a value outside what the clause accepts (e.g. a negative LIMIT/OFFSET).
 * - 23 (Integrity Constraint Violation): NOT NULL, UNIQUE, etc. violated.
 * - 2B (Dependent Privilege Descriptors Still Exist): can't drop an object directly because
 *   something else still depends on it.
 * - 42 (Syntax Error or Access Rule Violation): duplicate definition, reference to a nonexistent
 *   object, invalid definition.
 * - XX (Internal Error): a storage-engine- or index-internal failure, or corrupted stored data -
 *   not a SQL-semantic condition a caller's query can trigger through normal use.
 */
enum class ErrorCode(val sqlState: String) {
    // --- database.Table ---
    ROW_NOT_FOUND("02000"),
    PRIMARY_KEY_UPDATE_NOT_SUPPORTED("0A000"),
    NEGATIVE_LIMIT("2201W"),
    NEGATIVE_OFFSET("2201X"),
    UNIQUE_VIOLATION("23505"),
    UNDEFINED_INDEX("42704"),
    TOO_MANY_ORDER_COLUMNS("42P10"),
    ORDER_COLUMN_MISMATCH("42P10"),
    UNSUPPORTED_SORT_DIRECTION("42P10"),
    CORRUPTED_INDEX("XX000"),

    // --- database.DataBase (DDL) ---
    NOT_NULL_VIOLATION("23502"),

    /** Dropping this object directly isn't allowed because something else still depends on it - e.g. a table's primary index can only be removed via DROP TABLE (issue #49). */
    DEPENDENT_OBJECTS_STILL_EXIST("2BP01"),
    DUPLICATE_TABLE("42P07"),
    DUPLICATE_COLUMN("42701"),
    DUPLICATE_OBJECT("42710"),
    UNDEFINED_COLUMN("42703"),
    UNDEFINED_OBJECT("42704"),

    // --- catalog.CatalogManager ---
    /** A catalog row doesn't match its schema - under normal operation this can't happen; it means the stored data itself is corrupted. */
    CORRUPTED_ROW("XX000"),

    /** The table/index definition is itself structurally invalid (e.g. no key columns at all, no columns at all). */
    INVALID_DEFINITION("42P16"),
    UNDEFINED_TABLE("42P01"),

    // --- index.btree / index.serializer: structural or logic invariant violations ---
    INVALID_TRACE_STACK("XX000"),
    EMPTY_TREE("XX000"),
    INVALID_NODE_TYPE("XX000"),
    INVALID_SAFE_CHECK("XX000"),
    INVALID_BYTES("XX000"),
    POSITION_OUT_OF_BOUNDS("XX000"),
    VAR_INT_TOO_LONG("XX000"),
    INVALID_UUID_LENGTH("XX000"),
    INVALID_TRACE_OBJECT("XX000"),
    LEAF_NODE_NOT_FOUND("XX000"),

    // --- storageEngine: disk/memory-level invariant violations ---
    INVALID_READ_OFFSET("XX000"),
    FILE_CORRUPTED("XX000"),
    INVALID_PAGE_ID("XX000"),
    INVALID_PAGE_TYPE("XX000"),
    LRU_EVICT("XX000"),
    PAGE_NOT_FOUND_IN_CACHE("XX000"),
    PAGE_IN_USE("XX000"),
    UNEXPECTED("XX000"),
    SLOT_OUT_OF_BOUND("XX000"),
    SLOT_SHIFT("XX000"),
    PAGE_FULL("XX000"),
    INVALID_META_ARGUMENT("XX000"),
}
