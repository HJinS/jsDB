package sql.lexer

class Token(
    val type: TokenType,
    val text: String,
    val offset: Int,
    val value: Any? = null
)
