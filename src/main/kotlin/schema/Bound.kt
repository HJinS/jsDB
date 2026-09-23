package schema

/**
 * One side (lower or upper) of a `Table.selectByRange` scan boundary.
 *
 * @property value The boundary value, or null for **unbounded** on this side (scan to the tree's
 *   own edge — not "compare against null"). Any leading column pinned by equality must be
 *   mirrored into both the lower and upper [Bound] with the same value; see `Table.selectByRange`.
 * @property isInclusive Whether [value] itself is included (`<=`/`>=`) or excluded (`<`/`>`).
 *   Meaningless when [value] is null.
 * */
data class Bound<K>(
    val value: K?,
    val isInclusive: Boolean
)
