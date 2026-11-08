package sql.expr

/**
 * SQL's three-valued logic: a condition involving NULL is neither true nor false but [UNKNOWN].
 *
 * | a       | b       | a AND b | a OR b  |
 * |---------|---------|---------|---------|
 * | TRUE    | TRUE    | TRUE    | TRUE    |
 * | TRUE    | FALSE   | FALSE   | TRUE    |
 * | TRUE    | UNKNOWN | UNKNOWN | TRUE    |
 * | FALSE   | FALSE   | FALSE   | FALSE   |
 * | FALSE   | UNKNOWN | FALSE   | UNKNOWN |
 * | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
 *
 * (AND and OR are symmetric.) `NOT UNKNOWN` is [UNKNOWN]. A WHERE keeps a row only when its condition is [TRUE]
 * ([isTrue]), so [FALSE] and [UNKNOWN] rows are both dropped.
 */
enum class TriBool {
    TRUE, FALSE, UNKNOWN;

    /** Whether a row with this result passes a WHERE: only [TRUE] does. */
    val isTrue get() = this == TRUE

    /** [FALSE] if either side is [FALSE]; [TRUE] only if both are; otherwise [UNKNOWN]. */
    infix fun and(o: TriBool) = when {
        this == FALSE || o == FALSE -> FALSE
        this == TRUE && o == TRUE -> TRUE
        else -> UNKNOWN
    }

    /** [TRUE] if either side is [TRUE]; [FALSE] only if both are; otherwise [UNKNOWN]. */
    infix fun or(o: TriBool) = when {
        this == TRUE || o == TRUE -> TRUE
        this == FALSE && o == FALSE -> FALSE
        else -> UNKNOWN
    }

    /** Swaps [TRUE] and [FALSE]; [UNKNOWN] stays [UNKNOWN]. */
    operator fun not() = when (this) {
        TRUE -> FALSE
        FALSE -> TRUE
        UNKNOWN -> UNKNOWN
    }
}
