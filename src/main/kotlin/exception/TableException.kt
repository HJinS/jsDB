package exception

import util.ErrorCode
import util.SQLErrorDetail

/** Failures from [database.Table]'s row CRUD and range-scan API - the one caller-triggerable layer among these five exception hierarchies (bad `ORDER BY`, uniqueness, missing row, negative LIMIT/OFFSET), plus [ErrorCode.CORRUPTED_INDEX] for a genuine storage-level inconsistency. Which failure it is, is told by [code]. */
class TableException(
    override val code: ErrorCode,
    override val detail: SQLErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
