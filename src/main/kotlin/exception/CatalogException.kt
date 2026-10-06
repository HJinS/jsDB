package exception

import util.ErrorCode
import util.SQLErrorDetail

/** Failures from [catalog.CatalogManager] - resolving, registering, or updating a table/index/column catalog row. Which failure it is, is told by [code]. */
class CatalogException(
    override val code: ErrorCode,
    override val detail: SQLErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
