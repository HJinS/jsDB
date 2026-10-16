package sql.lexer

enum class TokenType(val isKeyword: Boolean = false) {
    // 리터럴, 이름
    IDENTIFIER, QUOTED_IDENTIFIER, INTEGER, DECIMAL, STRING,

    // 예약어
    SELECT(true), FROM(true), WHERE(true), INSERT(true), INTO(true),
    VALUES(true), UPDATE(true), SET(true), DELETE(true), CREATE(true),
    ON(true), DROP(true), ALTER(true), COLUMN(true), PRIMARY(true), NOT(true),
    NULL(true), AND(true), OR(true), IS(true), IN(true), BETWEEN(true),
    LIKE(true), TRUE(true), FALSE(true), ORDER(true), BY(true),
    LIMIT(true), OFFSET(true), AS(true),

    // 기호
    LPAREN, RPAREN, COMMA, SEMICOLON, DOT, STAR, PLUS, MINUS, SLASH, PERCENT, CONCAT,
    EQ, NE, LT, LE, GT, GE,
    EOF;

    companion object {
        private val keywords: Map<String, TokenType> = entries.filter { it.isKeyword }.associateBy { it.name }

        fun keywordOf(word: String): TokenType? = keywords[word.uppercase()]
    }
}
