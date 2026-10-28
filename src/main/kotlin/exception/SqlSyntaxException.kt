package exception

import util.ErrorCode
import util.SyntaxErrorDetail

/** Failures from turning SQL text into tokens and a syntax tree ([sql.lexer.Lexer] and the parser) - the text itself is malformed. Which failure it is, is told by [code]; where it is, by [detail]'s offset. */
class SqlSyntaxException(
    override val code: ErrorCode,
    override val detail: SyntaxErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
