package exception

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import util.EngineErrorDetail
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail

class CodedExceptionTest :
    BehaviorSpec({
        given("a TableException built from a code and a structured detail") {
            val detail = SQLErrorDetail(
                reason = "LIMIT must not be negative (got -1)",
                entityType = EntityType.TABLE,
                entityName = "users",
                columnNames = listOf("id"),
            )
            val error = TableException(ErrorCode.NEGATIVE_LIMIT, detail)

            `when`("reading it back") {
                then("the code and the detail are exposed as-is, not just baked into the message") {
                    error.code shouldBe ErrorCode.NEGATIVE_LIMIT
                    error.detail shouldBe detail
                    error.detail.columnNames shouldBe listOf("id")
                }
                then("the message carries both the SQLSTATE and the code name, then the entity and reason") {
                    error.message shouldBe "[2201W/NEGATIVE_LIMIT] Table 'users' (columns: 'id'): LIMIT must not be negative (got -1)"
                }
            }
        }

        given("two codes that share one SQLSTATE") {
            `when`("comparing them") {
                then("they are still different codes, which is what tells the failures apart") {
                    ErrorCode.TOO_MANY_ORDER_COLUMNS.sqlState shouldBe ErrorCode.ORDER_COLUMN_MISMATCH.sqlState
                    (ErrorCode.TOO_MANY_ORDER_COLUMNS == ErrorCode.ORDER_COLUMN_MISMATCH) shouldBe false
                }
            }
        }

        given("an engine-layer exception") {
            val error = StorageEngineException(ErrorCode.PAGE_IN_USE, EngineErrorDetail(reason = "pinned", pageId = 7L))

            `when`("reading it back") {
                then("its detail keeps the engine-specific fields, and the message names the code") {
                    error.detail.pageId shouldBe 7L
                    error.message shouldBe "[XX000/PAGE_IN_USE] pageId=7: pinned"
                }
            }
        }

        given("catchCode") {
            fun tableError(code: ErrorCode) = TableException(code, SQLErrorDetail(entityType = EntityType.ROW))

            `when`("the block returns normally") {
                then("its value is returned and onCaught is never called") {
                    var called = false
                    val result = catchCode(ErrorCode.ROW_NOT_FOUND, onCaught = { called = true; -1 }) { 42 }
                    result shouldBe 42
                    called shouldBe false
                }
            }

            `when`("the block throws a CodedException with the expected code") {
                then("onCaught's value is returned instead, and it receives the exception") {
                    var seen: CodedException? = null
                    val result = catchCode(
                        ErrorCode.ROW_NOT_FOUND,
                        onCaught = { seen = it; -1 }) { throw tableError(ErrorCode.ROW_NOT_FOUND) }
                    result shouldBe -1
                    seen?.code shouldBe ErrorCode.ROW_NOT_FOUND
                }
            }

            `when`("the block throws a CodedException with a different code") {
                then("it propagates untouched") {
                    val thrown = shouldThrow<TableException> {
                        catchCode(
                            ErrorCode.ROW_NOT_FOUND,
                            onCaught = { -1 }) { throw tableError(ErrorCode.UNIQUE_VIOLATION) }
                    }
                    thrown.code shouldBe ErrorCode.UNIQUE_VIOLATION
                }
            }

            `when`("the block throws something that is not a CodedException at all") {
                then("it propagates untouched") {
                    shouldThrow<IllegalStateException> {
                        catchCode(
                            ErrorCode.ROW_NOT_FOUND,
                            onCaught = { -1 }) { throw IllegalStateException("boom") }
                    }
                }
            }
        }
    })
