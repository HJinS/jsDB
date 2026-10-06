package helper

import exception.CodedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import util.ErrorCode

/**
 * Like [shouldThrow], but also asserts which failure it is: the exception must be a [T] whose
 * [CodedException.code] is [code]. A [T] carrying a different code fails the test, so it can't
 * pass for the wrong reason. Returns the exception for further assertions on [CodedException.detail].
 */
inline fun <reified T> shouldThrowCode(
    code: ErrorCode,
    noinline block: () -> Any?
): T where T : Throwable, T : CodedException {
    val error = shouldThrow<T>(block)
    error.code shouldBe code
    return error
}
