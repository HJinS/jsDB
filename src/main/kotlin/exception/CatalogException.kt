package exception

import util.SQLErrorDetail
import util.SqlState

sealed class CatalogException(
    sqlState: SqlState,
    detail: SQLErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(sqlState), cause) {

    /** 카탈로그 row가 스키마와 안 맞음 — 정상 동작이면 있을 수 없는, 저장 데이터 자체의 손상. */
    class CorruptedRow(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.INTERNAL_ERROR, detail, cause)

    /** 테이블/인덱스 정의 자체가 구조적으로 잘못됨(예: key column이 하나도 없음, 컬럼이 하나도 없음). */
    class InvalidDefinition(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.INVALID_TABLE_DEFINITION, detail, cause)

    class UndefinedTable(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.UNDEFINED_TABLE, detail, cause)

    class UndefinedObject(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.UNDEFINED_OBJECT, detail, cause)
}
