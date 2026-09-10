package util

open class ErrorDetail(val reason: String? = null) {
    protected open fun StringBuilder.appendExtra() {}

    fun toMessage(sqlState: SqlState): String = buildString {
        append("[${sqlState.code}]")
        appendExtra()
        reason?.let { append(": $it") }
    }
}
