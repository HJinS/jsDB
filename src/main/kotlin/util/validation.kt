package util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * `require`, but with a caller-chosen (typically a domain [exception.CatalogException]/
 * [exception.TableException]/etc.) exception instead of `IllegalArgumentException` — used
 * throughout for precondition checks that should surface as a specific SQLSTATE-carrying error.
 *
 * The `contract` tells the compiler that if this returns normally, [value] was true — same
 * smart-cast benefit as the standard library's `require`.
 * */
@OptIn(ExperimentalContracts::class)
inline fun requireOrThrow(value: Boolean, lazyException: () -> Exception) {
    contract {
        returns() implies value
    }
    if (!value) throw lazyException()
}
