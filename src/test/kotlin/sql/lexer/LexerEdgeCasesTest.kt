package sql.lexer

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import util.ErrorCode

class LexerEdgeCasesTest :
    BehaviorSpec({
        fun tokens(sql: String) = Lexer(sql).tokenize()
        fun types(sql: String) = tokens(sql).map { it.type }

        fun error(sql: String, code: ErrorCode, offset: Int) {
            val e = shouldThrowCode<SqlSyntaxException>(code) { Lexer(sql).tokenize() }
            e.detail.offset shouldBe offset
        }

        given("tokens written without spaces") {
            `when`("keywords touch punctuation") {
                then("every piece is still its own token") {
                    types("SELECT*FROM t") shouldBe listOf(
                        TokenType.SELECT, TokenType.STAR, TokenType.FROM, TokenType.IDENTIFIER, TokenType.EOF
                    )
                    types("a,b") shouldBe listOf(
                        TokenType.IDENTIFIER, TokenType.COMMA, TokenType.IDENTIFIER, TokenType.EOF
                    )
                    types("(a)") shouldBe listOf(
                        TokenType.LPAREN, TokenType.IDENTIFIER, TokenType.RPAREN, TokenType.EOF
                    )
                }
            }

            `when`("strings and operators are glued") {
                then("they split at the quote and at the operator") {
                    types("'a'||'b'") shouldBe
                            listOf(TokenType.STRING, TokenType.CONCAT, TokenType.STRING, TokenType.EOF)
                    types("x=-1") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.EQ,
                        TokenType.MINUS,
                        TokenType.INTEGER,
                        TokenType.EOF
                    )
                    types("a<>-1") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.NE,
                        TokenType.MINUS,
                        TokenType.INTEGER,
                        TokenType.EOF
                    )
                }
            }

            `when`("a word touches a quote") {
                then("a quote ends the word and begins a new token") {
                    types("a'b'") shouldBe listOf(TokenType.IDENTIFIER, TokenType.STRING, TokenType.EOF)
                    types("a\"b\"") shouldBe listOf(TokenType.IDENTIFIER, TokenType.QUOTED_IDENTIFIER, TokenType.EOF)
                    types("\"a\" \"b\"") shouldBe listOf(
                        TokenType.QUOTED_IDENTIFIER, TokenType.QUOTED_IDENTIFIER, TokenType.EOF
                    )
                }
            }

            `when`("names are dotted") {
                then("each dot is a DOT token") {
                    types("a.b.c") shouldBe listOf(
                        TokenType.IDENTIFIER, TokenType.DOT, TokenType.IDENTIFIER,
                        TokenType.DOT, TokenType.IDENTIFIER, TokenType.EOF,
                    )
                }
            }
        }

        given("numbers at their edges") {
            `when`("a number has a trailing dot, doubled dots or two decimal points") {
                then("the dot only joins a number when a digit follows it") {
                    types("1..2") shouldBe listOf(
                        TokenType.INTEGER, TokenType.DOT, TokenType.DECIMAL, TokenType.EOF
                    )
                    types("1.e5") shouldBe listOf(
                        TokenType.INTEGER, TokenType.DOT, TokenType.IDENTIFIER, TokenType.EOF
                    )
                    tokens("1.5.2").map { it.type } shouldBe listOf(
                        TokenType.DECIMAL, TokenType.DECIMAL, TokenType.EOF
                    )
                    tokens("1.5.2").map { it.value } shouldBe listOf(
                        BigDecimal("1.5"), BigDecimal(".2"), null
                    )
                }
            }

            `when`("a decimal has leading zeros or very many digits") {
                then("its value is exact") {
                    tokens("00.50")[0].value shouldBe BigDecimal("0.50")
                    tokens("123456789012345678901234567890.5")[0].value shouldBe BigDecimal("123456789012345678901234567890.5")
                }
            }

            `when`("a number is malformed") {
                then("INVALID_NUMBER points at its start") {
                    error("0x10", ErrorCode.INVALID_NUMBER, 0)
                    error("1e+", ErrorCode.INVALID_NUMBER, 0)
                    error("1e5e", ErrorCode.INVALID_NUMBER, 0)
                    error("a 12abc", ErrorCode.INVALID_NUMBER, 2)
                }
            }

            `when`("the smallest Long is written as a literal") {
                then("the minus is a separate token, so the magnitude is out of range") {
                    error("-9223372036854775808", ErrorCode.NUMBER_OUT_OF_RANGE, 1)
                }
            }
        }

        given("operators at their edges") {
            `when`("operator characters repeat or combine in ways SQL does not define") {
                then("they split at the longest valid operator") {
                    types("a<<b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.LT,
                        TokenType.LT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a>>b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.GT,
                        TokenType.GT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a==b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.EQ,
                        TokenType.EQ,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a=>b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.EQ,
                        TokenType.GT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a<=>b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.LE,
                        TokenType.GT,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                    types("a<-b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.LT,
                        TokenType.MINUS,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }

            `when`("a less-than runs straight into a line comment") {
                then("the comment swallows the rest") {
                    types("a<--b") shouldBe listOf(TokenType.IDENTIFIER, TokenType.LT, TokenType.EOF)
                }
            }

            `when`("a lone bang or bar is mixed into longer runs") {
                then("the pair is read first and the leftover character is the error") {
                    error("a !< b", ErrorCode.UNEXPECTED_CHARACTER, 2)
                    error("a ||| b", ErrorCode.UNEXPECTED_CHARACTER, 4)
                }
            }
        }

        given("comments at their edges") {
            `when`("a line comment contains quotes or comment terminators") {
                then("none of it is interpreted") {
                    types("a -- it's \"x\" */ /*\nb") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }

            `when`("a block comment contains quotes or line comment markers") {
                then("none of it is interpreted either") {
                    types("/* it's -- \"x\" */ a") shouldBe listOf(TokenType.IDENTIFIER, TokenType.EOF)
                    types("a/**/-b") shouldBe listOf(
                        TokenType.IDENTIFIER,
                        TokenType.MINUS,
                        TokenType.IDENTIFIER,
                        TokenType.EOF
                    )
                }
            }

            `when`("comment markers sit inside a quoted identifier") {
                then("they are part of the name") {
                    tokens("\"a--b\"")[0].value shouldBe "a--b"
                    tokens("\"a/*b*/\"")[0].value shouldBe "a/*b*/"
                    types("\"a--b\"") shouldBe listOf(TokenType.QUOTED_IDENTIFIER, TokenType.EOF)
                }
            }

            `when`("a block comment's closer shares a character with its opener, or is repeated") {
                then("the first complete closer ends it, and a second one is just two tokens") {
                    types("/*/ */") shouldBe listOf(TokenType.EOF)
                    types("/* */*/") shouldBe listOf(TokenType.STAR, TokenType.SLASH, TokenType.EOF)
                }
            }

            `when`("a block comment ends in a lone star") {
                then("it is unterminated") {
                    error("/* *", ErrorCode.UNTERMINATED_COMMENT, 0)
                    error("a /* b * c", ErrorCode.UNTERMINATED_COMMENT, 2)
                }
            }
        }

        given("offsets with unusual characters") {
            `when`("the input has Windows line endings") {
                then("both characters of the line ending count") {
                    tokens("a\r\nb").map { it.offset } shouldBe listOf(0, 3, 4)
                }
            }

            `when`("the input has Korean text and an emoji") {
                then("offsets count UTF-16 characters, so an emoji takes two") {
                    tokens("'한' x").map { it.offset } shouldBe listOf(0, 4, 5)
                    tokens("'😀' x").map { it.offset } shouldBe listOf(0, 5, 6)
                    tokens("'😀'")[0].value shouldBe "😀"
                }
            }

            `when`("a Korean name is used") {
                then("it is an identifier, and an emoji outside a string is an unexpected character") {
                    tokens("_한글2")[0].let { it.type shouldBe TokenType.IDENTIFIER; it.value shouldBe "_한글2" }
                    error("a 😀", ErrorCode.UNEXPECTED_CHARACTER, 2)
                }
            }
        }

        given("zero-length quoted identifiers") {
            `when`("nothing sits between the quotes") {
                then("EMPTY_QUOTED_IDENTIFIER points at the opening quote") {
                    error("\"\"", ErrorCode.EMPTY_QUOTED_IDENTIFIER, 0)
                    error("a \"\"", ErrorCode.EMPTY_QUOTED_IDENTIFIER, 2)
                    error("\"a\" \"\"", ErrorCode.EMPTY_QUOTED_IDENTIFIER, 4)
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.EMPTY_QUOTED_IDENTIFIER) {
                        Lexer("\"\"").tokenize()
                    }.message shouldBe "[42601/EMPTY_QUOTED_IDENTIFIER] at offset 0: zero-length quoted identifier"
                }
            }

            `when`("the quotes hold something, even just an escaped quote, or it is a string") {
                then("they are not empty") {
                    tokens("\"\"\"\"")[0].value shouldBe "\""
                    tokens("\" \"")[0].value shouldBe " "
                    tokens("''")[0].value shouldBe ""
                }
            }
        }

        given("how an unexpected character is shown in the error") {
            fun messageOf(sql: String) = shouldThrowCode<SqlSyntaxException>(ErrorCode.UNEXPECTED_CHARACTER) {
                Lexer(sql).tokenize()
            }.message

            `when`("the character is visible") {
                then("it is shown as itself, quoted") {
                    messageOf("a @ b") shouldBe "[42601/UNEXPECTED_CHARACTER] at offset 2: unexpected character '@'"
                    messageOf("a 😀") shouldBe "[42601/UNEXPECTED_CHARACTER] at offset 2: unexpected character '😀'"
                }
            }

            `when`("the character looks like a space or like nothing") {
                then("it is shown by its code point, so it can be found") {
                    val cases = mapOf(
                        " " to "U+00A0", // non-breaking space, the classic copy-paste culprit
                        "　" to "U+3000", // ideographic space
                        "​" to "U+200B", // zero-width space
                        "﻿" to "U+FEFF", // byte order mark
                        "\u000B" to "U+000B", // vertical tab
                        "\u000C" to "U+000C", // form feed
                        "\u0001" to "U+0001",
                        "\u0000" to "U+0000",
                    )
                    for ((character, shown) in cases) {
                        messageOf("a $character b") shouldBe "[42601/UNEXPECTED_CHARACTER] at offset 2: unexpected character $shown"
                    }
                }
            }

            `when`("half of a surrogate pair is all there is") {
                then("it is shown by its code unit") {
                    messageOf("\uD83D") shouldBe "[42601/UNEXPECTED_CHARACTER] at offset 0: unexpected character U+D83D"
                }
            }
        }

        given("calling tokenize more than once") {
            `when`("the input is valid") {
                val lexer = Lexer("select a from t")
                val first = lexer.tokenize()
                val second = lexer.tokenize()
                then("every call returns the same tokens, with exactly one EOF") {
                    second.map { it.type } shouldBe first.map { it.type }
                    second.map { it.offset } shouldBe first.map { it.offset }
                    second.count { it.type == TokenType.EOF } shouldBe 1
                }
                then("a later call is not affected by earlier results") {
                    first.size shouldBe 5
                    lexer.tokenize().size shouldBe 5
                }
            }

            `when`("the input is malformed") {
                val lexer = Lexer("a 'abc")
                then("every call fails the same way, rather than starting from where the last one stopped") {
                    repeat(3) {
                        val e = shouldThrowCode<SqlSyntaxException>(ErrorCode.UNTERMINATED_STRING) { lexer.tokenize() }
                        e.detail.offset shouldBe 2
                    }
                }
            }
        }

        given("large input") {
            `when`("the input has hundreds of thousands of tokens") {
                then("it is split in one pass") {
                    val result = tokens("a ".repeat(200_000))
                    result.size shouldBe 200_001
                    result.last().offset shouldBe 400_000
                }
            }

            `when`("a single token is huge") {
                then("it is still one token") {
                    tokens("'" + "x".repeat(1_000_000) + "'")[0].let { (it.value as String).length shouldBe 1_000_000 }
                    tokens("a".repeat(1_000_000)).size shouldBe 2
                }
            }

            `when`("parentheses are nested hundreds of thousands deep") {
                then("the lexer, which does not recurse, handles it") {
                    tokens("(".repeat(300_000)).size shouldBe 300_001
                }
            }
        }
    })
