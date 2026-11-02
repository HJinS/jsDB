package sql.expr

enum class TriBool {
    TRUE, FALSE, UNKNOWN;

    val isTrue get() = this == TRUE
    infix fun and(o: TriBool) = when {
        this == FALSE || o == FALSE -> FALSE
        this == TRUE && o == TRUE -> TRUE
        else -> UNKNOWN
    }

    infix fun or(o: TriBool) = when {
        this == TRUE || o == TRUE -> TRUE
        this == FALSE && o == FALSE -> FALSE
        else -> UNKNOWN
    }

    operator fun not() = when (this) {
        TRUE -> FALSE
        FALSE -> TRUE
        UNKNOWN -> UNKNOWN
    }
}
