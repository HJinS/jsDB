package exception

import util.ErrorCode
import util.ErrorDetail

/**
 * What every layer's exception ([TableException], [DatabaseException], [CatalogException],
 * [IndexException], [StorageEngineException]) exposes: [code] says which failure it is, [detail]
 * carries the structured context the message is built from. Branch and assert on these rather than
 * on the message text.
 */
interface CodedException {
    val code: ErrorCode
    val detail: ErrorDetail
}

/**
 * Runs [block]; if it throws a [CodedException] whose [CodedException.code] is [code], returns
 * `onCaught`'s result instead. Any other exception - including a [CodedException] with a different
 * code - propagates untouched.
 */
inline fun <T> catchCode(code: ErrorCode, onCaught: (CodedException) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        if (e is CodedException && e.code == code) onCaught(e) else throw e
    }
