package util

/**
 * Only the codes defined by the SQL standard (SQLSTATE) that correspond to situations this
 * project actually throws.
 * Class 02 (No Data): no row to select/update/delete.
 * Class 23 (Integrity Constraint Violation): NOT NULL, UNIQUE, etc. violated.
 * Class 42 (Syntax Error or Access Rule Violation): duplicate definition, reference to a
 *   nonexistent object, invalid definition.
 * Class XX (Internal Error): a storage-engine-internal failure, not a SQL-semantic condition.
 * */
enum class SqlState(val code: String) {
    NO_DATA("02000"),
    NOT_NULL_VIOLATION("23502"),
    UNIQUE_VIOLATION("23505"),
    DUPLICATE_TABLE("42P07"),
    DUPLICATE_COLUMN("42701"),
    DUPLICATE_OBJECT("42710"),
    UNDEFINED_TABLE("42P01"),
    UNDEFINED_COLUMN("42703"),
    UNDEFINED_OBJECT("42704"),
    INVALID_TABLE_DEFINITION("42P16"),
    INVALID_COLUMN_REFERENCE("42P10"),
    INTERNAL_ERROR("XX000")
}
