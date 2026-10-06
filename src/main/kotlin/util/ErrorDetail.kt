package util

/**
 * Base for the per-layer error-detail payloads ([EngineErrorDetail], [SQLErrorDetail]) every
 * `exception.*Exception` carries. Formats a consistent `[SQLSTATE/CODE_NAME] <subclass-specific
 * detail>: <reason>` message via [toMessage] - subclasses contribute their own fields by
 * overriding [appendExtra] rather than each building their own message string.
 * */
open class ErrorDetail(val reason: String? = null) {
    protected open fun StringBuilder.appendExtra() {}

    fun toMessage(code: ErrorCode): String = buildString {
        append("[${code.sqlState}/${code.name}]")
        appendExtra()
        reason?.let { append(": $it") }
    }
}
