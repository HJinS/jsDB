package util

/**
 * [ErrorDetail] for failures in the SQL text itself (lexer and parser) - [exception.SqlSyntaxException]'s
 * payload. [offset] is the 0-based character offset into the original SQL string where the problem
 * starts; converting it to a line/column is left to whoever still has the SQL text.
 */
class SyntaxErrorDetail(
    reason: String? = null,
    val offset: Int,
) : ErrorDetail(reason) {
    override fun StringBuilder.appendExtra() {
        append(" at offset $offset")
    }
}
