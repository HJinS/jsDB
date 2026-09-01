package util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun requireOrThrow(value: Boolean, lazyException: () -> Exception) {
    contract {
        returns() implies value
    }
    if (!value) throw lazyException()
}
