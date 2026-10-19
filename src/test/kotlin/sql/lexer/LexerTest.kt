package sql.lexer

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import util.ErrorCode

class LexerTest :
    BehaviorSpec({
        fun tokens(sql: String) = Lexer(sql).tokenize()
        fun types(sql: String) = tokens(sql).map { it.type }

        given("keywords and identifiers") {
            `when`("lexing 'select ID from t'") {
                val result = tokens("select ID from t")
                then("keywords are matched case-insensitively and identifiers get their name lowercased") {
                    result.map { it.type } shouldBe listOf(
                        TokenType.SELECT,
                        TokenType.IDENTIFIER,
                        TokenType.FROM,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    result[1].value shouldBe "id"
                    result[1].text shouldBe "ID"
                }
            }

            `when`("lexing words that are only keywords in some grammar positions") {
                then("they are plain identifiers") {
                    types("table key add asc desc index unique") shouldBe List(7) { TokenType.IDENTIFIER } + TokenType.EOF
                }
            }

            `when`("lexing a quoted identifier") {
                val result = tokens("\"Users\"")
                then("its case is preserved and it never turns into a keyword") {
                    result[0].type shouldBe TokenType.QUOTED_IDENTIFIER
                    result[0].value shouldBe "Users"
                    types("\"select\"") shouldBe listOf(TokenType.QUOTED_IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("lexing an empty input") {
                then("only EOF comes back, at offset 0") {
                    val result = tokens("")
                    result.map { it.type } shouldBe listOf(TokenType.EOF)
                    result[0].offset shouldBe 0
                }
            }
        }

        given("string literals") {
            `when`("a quote is doubled inside the string") {
                val result = tokens("'it''s'")
                then("the value holds a single quote while the text keeps the source") {
                    result[0].type shouldBe TokenType.STRING
                    result[0].value shouldBe "it's"
                    result[0].text shouldBe "'it''s'"
                }
            }

            `when`("the string is empty") {
                then("it is a valid empty value") {
                    tokens("''")[0].value shouldBe ""
                }
            }
        }

        given("numbers") {
            `when`("lexing 1.5e-3 .5 7") {
                val result = tokens("1.5e-3 .5 7")
                then("decimals and integers are told apart, each as one token") {
                    result.map { it.type } shouldBe listOf(
                        TokenType.DECIMAL,
                        TokenType.DECIMAL,
                        TokenType.INTEGER,
                        TokenType.EOF
                    )
                    result[0].value shouldBe BigDecimal("1.5e-3")
                    result[1].value shouldBe BigDecimal(".5")
                    result[2].value shouldBe 7L
                }
            }

            `when`("lexing 1.5") {
                val result = tokens("1.5")
                then("the dot is part of the number, not a separate token") {
                    result.map { it.type } shouldBe listOf(TokenType.DECIMAL, TokenType.EOF)
                    result[0].text shouldBe "1.5"
                }
            }

            `when`("lexing t.col") {
                then("the dot stays a DOT token") {
                    types("t.col") shouldBe
                            listOf(TokenType.IDENTIFIER, TokenType.DOT, TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("lexing a-1") {
                then("the minus is never glued to the number") {
                    types("a-1") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.MINUS,
                        TokenType.INTEGER,
                        TokenType.EOF
                    )
                }
            }
        }

        given("operators") {
            `when`("lexing two-character operators next to their one-character prefixes") {
                then("the longest match wins") {
                    types("a<=b <> c !=d >= e < f > g || h") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.LE,
                        TokenType.IDENTIFIER,
                        TokenType.NE,
                        TokenType.IDENTIFIER,
                        TokenType.NE,
                        TokenType.IDENTIFIER,
                        TokenType.GE,
                        TokenType.IDENTIFIER,
                        TokenType.LT,
                        TokenType.IDENTIFIER,
                        TokenType.GT,
                        TokenType.IDENTIFIER,
                        TokenType.CONCAT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }

            `when`("lexing single-character punctuation") {
                then("each becomes its own token") {
                    types("( ) , ; . * + - / % =") shouldBe listOf(
                        TokenType.LPAREN,
                        TokenType.RPAREN,
                        TokenType.COMMA,
                        TokenType.SEMICOLON,
                        TokenType.DOT,
                        TokenType.STAR,
                        TokenType.PLUS,
                        TokenType.MINUS,
                        TokenType.SLASH,
                        TokenType.PERCENT,
                        TokenType.EQ,
                        TokenType.EOF,
                    )
                }
            }
        }

        given("comments and whitespace") {
            `when`("a line comment ends with a newline") {
                then("it is skipped") {
                    types("a -- c\n b") shouldBe listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("a line comment runs to the end of the input") {
                then("it is skipped without an error") {
                    types("a -- the rest") shouldBe listOf(TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("a block comment sits between tokens") {
                then("it is skipped, and '*' inside it does not end it early") {
                    types("a /* x * y */ b") shouldBe listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("a lone slash is not a comment") {
                then("it is a SLASH token") {
                    types("a / b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.SLASH,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }
        }

        given("token offsets") {
            `when`("lexing 'select  x'") {
                val result = tokens("select  x")
                then("each token records where it starts, and EOF sits at the input length") {
                    result.map { it.offset } shouldBe listOf(0, 8, 9)
                }
            }
        }

        given("malformed input") {
            fun error(sql: String, code: ErrorCode, offset: Int) {
                val e = shouldThrowCode<SqlSyntaxException>(code) { Lexer(sql).tokenize() }
                e.detail.offset shouldBe offset
            }

            `when`("a string is never closed") {
                then("UNTERMINATED_STRING points at its opening quote") {
                    error("select 'abc", ErrorCode.UNTERMINATED_STRING, 7)
                }
            }
            `when`("a quoted identifier is never closed") {
                then("UNTERMINATED_QUOTED_IDENTIFIER points at its opening quote") {
                    error("\"abc", ErrorCode.UNTERMINATED_QUOTED_IDENTIFIER, 0)
                }
            }
            `when`("a block comment is never closed") {
                then("UNTERMINATED_COMMENT points at its opening") {
                    error("a /* x", ErrorCode.UNTERMINATED_COMMENT, 2)
                }
            }
            `when`("letters follow a number directly") {
                then("INVALID_NUMBER is reported") {
                    error("123abc", ErrorCode.INVALID_NUMBER, 0)
                }
            }
            `when`("an integer does not fit in a Long") {
                then("NUMBER_OUT_OF_RANGE is reported") {
                    error("99999999999999999999", ErrorCode.NUMBER_OUT_OF_RANGE, 0)
                }
            }
            `when`("'!' or '|' stands alone, or a stray symbol appears") {
                then("UNEXPECTED_CHARACTER points at it") {
                    error("a ! b", ErrorCode.UNEXPECTED_CHARACTER, 2)
                    error("a | b", ErrorCode.UNEXPECTED_CHARACTER, 2)
                    error("a @ b", ErrorCode.UNEXPECTED_CHARACTER, 2)
                }
            }

            `when`("reading the message of an error") {
                then("it carries the SQLSTATE, the code name, the offset and the reason") {
                    val e =
                        shouldThrowCode<SqlSyntaxException>(ErrorCode.UNTERMINATED_STRING) { Lexer("'abc").tokenize() }
                    e.message shouldBe "[42601/UNTERMINATED_STRING] at offset 0: unterminated string literal"
                }
            }
        }

        given("every reserved keyword") {
            for (keyword in TokenType.entries.filter { it.isKeyword }) {
                `when`("lexing ${keyword.name} in lower, upper and mixed case") {
                    then("each spelling becomes the ${keyword.name} token, carrying no value") {
                        val mixed = keyword.name.mapIndexed { i, c -> if (i % 2 == 0) c.lowercaseChar() else c }
                            .joinToString("")
                        for (spelling in listOf(keyword.name.lowercase(), keyword.name, mixed)) {
                            val result = tokens(spelling)
                            result.map { it.type } shouldBe listOf(keyword, TokenType.EOF)
                            result[0].value shouldBe null
                        }
                    }
                }
            }
        }

        given("words that only look like keywords or internal token names") {
            `when`("a keyword is a prefix, a suffix, or part of a longer word") {
                then("the whole word is one identifier") {
                    types("selection select1 _select selects xfrom") shouldBe List(5) { TokenType.IDENTIFIER } + TokenType.EOF
                }
            }

            `when`("an identifier is spelled like a TokenType constant that is not a keyword") {
                then("it stays an identifier, never turning into EOF, STRING, ...") {
                    types("eof identifier integer decimal string lparen comma") shouldBe List(7) { TokenType.IDENTIFIER } + TokenType.EOF
                }
            }
        }

        given("identifier forms") {
            `when`("lexing underscores, digits, dollar signs, mixed case and non-ASCII letters") {
                val result = tokens("_tmp a_b2 a\$b UserName 이름")
                then("each is one identifier whose value is its lowercased name") {
                    result.dropLast(1).map { it.type }.toSet() shouldBe setOf(TokenType.IDENTIFIER)
                    result.dropLast(1).map { it.value } shouldBe listOf("_tmp", "a_b2", "a\$b", "username", "이름")
                    result.dropLast(1).map { it.text } shouldBe listOf("_tmp", "a_b2", "a\$b", "UserName", "이름")
                }
            }

            `when`("a quoted identifier contains a doubled quote, spaces and symbols") {
                then("the value is unescaped and its case is kept, while the text keeps the source as written") {
                    val escaped = tokens("\"My\"\"Col\"")[0]
                    escaped.value shouldBe "My\"Col"
                    escaped.text shouldBe "\"My\"\"Col\""
                    tokens("\"a b-c\"")[0].value shouldBe "a b-c"
                    tokens("\"MyCol\"")[0].text shouldBe "\"MyCol\""
                }
            }
        }

        given("string literal edge cases") {
            `when`("the string is just an escaped quote") {
                then("'''' is a single quote") {
                    tokens("''''")[0].value shouldBe "'"
                }
            }

            `when`("a string contains comment markers or keywords") {
                then("they are plain text, not comments or tokens") {
                    val result = tokens("'a -- b' '/* x */' 'select from'")
                    result.map { it.type } shouldBe List(3) { TokenType.STRING } + TokenType.EOF
                    result.dropLast(1).map { it.value } shouldBe listOf("a -- b", "/* x */", "select from")
                }
            }

            `when`("a string spans lines or holds non-ASCII text and emoji") {
                then("its value is kept as written") {
                    tokens("'a\nb'")[0].value shouldBe "a\nb"
                    tokens("'한글😀'")[0].value shouldBe "한글😀"
                }
            }

            `when`("two strings sit next to each other, or one has a doubled quote inside") {
                then("a space-separated pair is two tokens, a doubled quote keeps it one") {
                    types("'a' 'b'") shouldBe listOf(TokenType.STRING, TokenType.STRING, TokenType.EOF)
                    tokens("'a''b'")[0].value shouldBe "a'b"
                    types("'a''b'") shouldBe listOf(TokenType.STRING, TokenType.EOF)
                }
            }
        }

        given("number edge cases") {
            `when`("lexing zero, leading zeros and the largest Long") {
                val result = tokens("0 007 9223372036854775807")
                then("they are integers with the expected values") {
                    result.dropLast(1).map { it.type } shouldBe List(3) { TokenType.INTEGER }
                    result.dropLast(1).map { it.value } shouldBe listOf(0L, 7L, Long.MAX_VALUE)
                }
            }

            `when`("lexing exponent forms") {
                val result = tokens("1e5 1E+5 1.5E-2 .5e2")
                then("each is a single decimal") {
                    result.dropLast(1).map { it.type } shouldBe List(4) { TokenType.DECIMAL }
                    result.dropLast(1).map { it.value } shouldBe listOf(
                        BigDecimal("1e5"),
                        BigDecimal("1E+5"),
                        BigDecimal("1.5E-2"),
                        BigDecimal(".5e2")
                    )
                }
            }

            `when`("a number ends with a dot and nothing after it") {
                then("the dot is left over as its own token") {
                    types("1.") shouldBe listOf(TokenType.INTEGER, TokenType.DOT, TokenType.EOF)
                }
            }
        }

        given("operator edge cases") {
            `when`("operators touch their neighbours with no spaces") {
                then("each still splits at the longest match") {
                    types("a<>b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.NE,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a!==b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.NE,
                        TokenType.EQ,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a||b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.CONCAT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }

            `when`("'<' or '>' is the very last character") {
                then("it is LT or GT, not an error") {
                    types("a <") shouldBe listOf(TokenType.IDENTIFIER, TokenType.LT, TokenType.EOF)
                    types("a >") shouldBe listOf(TokenType.IDENTIFIER, TokenType.GT, TokenType.EOF)
                }
            }

            `when`("two minus signs are separated by a space, or glued together") {
                then("spaced they are two MINUS tokens, glued they start a comment") {
                    types("a - -b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.MINUS,
                        TokenType.MINUS,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a--b") shouldBe listOf(TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("a comment terminator appears without an opener") {
                then("'*' and '/' are separate tokens") {
                    types("*/") shouldBe listOf(TokenType.STAR, TokenType.SLASH, TokenType.EOF)
                    types("/ *") shouldBe listOf(TokenType.SLASH, TokenType.STAR, TokenType.EOF)
                }
            }
        }

        given("comment and whitespace edge cases") {
            `when`("lexing empty or star-only block comments") {
                then("they are skipped") {
                    types("/**/") shouldBe listOf(TokenType.EOF)
                    types("/***/") shouldBe listOf(TokenType.EOF)
                    types("a/*c*/b") shouldBe listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("the slash of the opener doubles as the slash of the closer") {
                then("'/*/' is still an unterminated comment") {
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.UNTERMINATED_COMMENT) { Lexer("/*/").tokenize() }.detail.offset shouldBe 0
                }
            }

            `when`("block comments are nested") {
                then("the first '*/' ends the comment; the rest is ordinary tokens") {
                    types("/* /* */ */") shouldBe listOf(TokenType.STAR, TokenType.SLASH, TokenType.EOF)
                }
            }

            `when`("the input is only a comment, or only whitespace") {
                then("nothing but EOF comes back, positioned at the input length") {
                    tokens("-- x").map { it.type } shouldBe listOf(TokenType.EOF)
                    tokens("/* x */").map { it.type } shouldBe listOf(TokenType.EOF)
                    tokens("  \t\r\n").let { it.map { t -> t.type } shouldBe listOf(TokenType.EOF); it[0].offset shouldBe 5 }
                }
            }

            `when`("lines end with carriage returns, and tabs separate tokens") {
                then("they are plain whitespace, and a line comment stops at the newline") {
                    types("a\tb\r\nc") shouldBe List(3) { TokenType.IDENTIFIER } + TokenType.EOF
                    types("-- x\r\ny") shouldBe listOf(TokenType.IDENTIFIER, TokenType.EOF)
                }
            }
        }

        given("offsets and token text") {
            `when`("lexing across several lines") {
                val result = tokens("select\n  x")
                then("offsets count every character, newlines and indentation included") {
                    result.map { it.offset } shouldBe listOf(0, 9, 10)
                }
            }

            `when`("the input ends with trailing whitespace and a comment") {
                then("EOF sits at the very end of the input") {
                    tokens("a  -- c").last().offset shouldBe 7
                }
            }

            `when`("lexing a whole statement") {
                val sql = "SELECT id, email FROM users WHERE id >= 10 AND email LIKE 'a%' LIMIT 5;"
                val result = tokens(sql)
                then("every token's text is exactly the source slice at its offset") {
                    for (token in result.dropLast(1)) {
                        sql.substring(token.offset, token.offset + token.text.length) shouldBe token.text
                    }
                    result.last().let { it.offset shouldBe sql.length; it.text shouldBe "" }
                }
            }
        }

        given("whole statements") {
            `when`("lexing a SELECT with a filter and a limit") {
                val result = tokens("SELECT id, email FROM users WHERE id >= 10 AND email LIKE 'a%' LIMIT 5;")
                then("the token stream matches the grammar's view of it") {
                    result.map { it.type } shouldBe listOf(
                        TokenType.SELECT, TokenType.IDENTIFIER, TokenType.COMMA, TokenType.IDENTIFIER,
                        TokenType.FROM, TokenType.IDENTIFIER, TokenType.WHERE, TokenType.IDENTIFIER,
                        TokenType.GE, TokenType.INTEGER, TokenType.AND, TokenType.IDENTIFIER,
                        TokenType.LIKE, TokenType.STRING, TokenType.LIMIT, TokenType.INTEGER,
                        TokenType.SEMICOLON, TokenType.EOF,
                    )
                    result[9].value shouldBe 10L
                    result[13].value shouldBe "a%"
                    result[15].value shouldBe 5L
                }
            }

            `when`("lexing an UPDATE that reads the column it writes") {
                then("the expression is plain identifiers and operators") {
                    types("UPDATE t SET a = a + 1 WHERE id = 3;") shouldBe listOf(
                        TokenType.UPDATE, TokenType.IDENTIFIER, TokenType.SET, TokenType.IDENTIFIER,
                        TokenType.EQ, TokenType.IDENTIFIER, TokenType.PLUS, TokenType.INTEGER,
                        TokenType.WHERE, TokenType.IDENTIFIER, TokenType.EQ, TokenType.INTEGER,
                        TokenType.SEMICOLON, TokenType.EOF,
                    )
                }
            }

            `when`("lexing a CREATE TABLE that uses context keywords as names") {
                val result = tokens("CREATE TABLE t (key INT PRIMARY KEY, name text NOT NULL)")
                then("TABLE, KEY and the type names are identifiers; PRIMARY, NOT and NULL are keywords") {
                    result.map { it.type } shouldBe listOf(
                        TokenType.CREATE, TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.LPAREN,
                        TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.PRIMARY, TokenType.IDENTIFIER,
                        TokenType.COMMA, TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.NOT,
                        TokenType.NULL, TokenType.RPAREN, TokenType.EOF,
                    )
                    result[1].value shouldBe "table"
                    result[4].value shouldBe "key"
                    result[5].value shouldBe "int"
                }
            }
        }

        given("more malformed input") {
            fun error(sql: String, code: ErrorCode, offset: Int) {
                val e = shouldThrowCode<SqlSyntaxException>(code) { Lexer(sql).tokenize() }
                e.detail.offset shouldBe offset
            }

            `when`("a problem sits on a later line") {
                then("the offset still counts from the start of the whole input") {
                    error("a\n  'abc", ErrorCode.UNTERMINATED_STRING, 4)
                    error("a 12x", ErrorCode.INVALID_NUMBER, 2)
                }
            }

            `when`("a number has a dangling exponent or an underscore") {
                then("INVALID_NUMBER is reported") {
                    error("1e", ErrorCode.INVALID_NUMBER, 0)
                    error("1_000", ErrorCode.INVALID_NUMBER, 0)
                    error("1.5abc", ErrorCode.INVALID_NUMBER, 0)
                }
            }

            `when`("an integer is just past Long.MAX_VALUE") {
                then("NUMBER_OUT_OF_RANGE is reported") {
                    error("9223372036854775808", ErrorCode.NUMBER_OUT_OF_RANGE, 0)
                }
            }

            `when`("a string ends in a doubled quote") {
                then("the doubled quote is an escape, so the string is still unterminated") {
                    error("'it''", ErrorCode.UNTERMINATED_STRING, 0)
                }
            }

            `when`("two problems appear in one input") {
                then("only the first one, by position, is reported") {
                    error("a @ 'abc", ErrorCode.UNEXPECTED_CHARACTER, 2)
                    error("'abc @", ErrorCode.UNTERMINATED_STRING, 0)
                }
            }

            `when`("'!' or '|' is the last character") {
                then("UNEXPECTED_CHARACTER points at it") {
                    error("a !", ErrorCode.UNEXPECTED_CHARACTER, 2)
                    error("a |", ErrorCode.UNEXPECTED_CHARACTER, 2)
                }
            }

            `when`("symbols this SQL subset does not use appear") {
                then("each is UNEXPECTED_CHARACTER at its position") {
                    for (symbol in listOf("#", "?", "$", "`", "~", "^", "{", "}", "[", "]", ":", "@", "\\", "&")) {
                        error(symbol, ErrorCode.UNEXPECTED_CHARACTER, 0)
                    }
                }
            }

            `when`("a real NUL character is in the input") {
                then("it is an ordinary unexpected character, not mistaken for the end of input") {
                    error("a\u0000b", ErrorCode.UNEXPECTED_CHARACTER, 1)
                    tokens("'a\u0000b'")[0].value shouldBe "a\u0000b"
                }
            }
        }
    })
