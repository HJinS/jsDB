package exception

import util.ErrorCode
import util.SQLErrorDetail

/** Failures from [database.DataBase] - table/index/column DDL validation (duplicates, undefined references, PK nullability) and the NOT NULL check on written rows. Which failure it is, is told by [code]. */
class DatabaseException(
    override val code: ErrorCode,
    override val detail: SQLErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
