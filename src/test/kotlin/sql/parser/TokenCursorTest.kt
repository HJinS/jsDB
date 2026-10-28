package sql.parser

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import sql.lexer.Lexer
import sql.lexer.TokenType
import util.ErrorCode

class TokenCursorTest :
    BehaviorSpec({
        fun cursorOf(sql: String) = TokenCursor(Lexer(sql).tokenize())

        fun unexpected(block: () -> Any?): SqlSyntaxException =
            shouldThrowCode<SqlSyntaxException>(ErrorCode.UNEXPECTED_TOKEN) { block() }

        given("looking ahead") {
            `when`("peeking at tokens by distance") {
                val cursor = cursorOf("a b")
                then("nothing moves, and anything past the end reads as EOF") {
                    cursor.current.type shouldBe TokenType.IDENTIFIER
                    cursor[1].value shouldBe "b"
                    cursor[2].type shouldBe TokenType.EOF
                    cursor[50].type shouldBe TokenType.EOF
                    cursor.pos shouldBe 0
                }
            }
        }

        given("advancing") {
            `when`("advancing past the last real token") {
                val cursor = cursorOf("a")
                val first = cursor.advance()
                then("the cursor parks on EOF and never moves beyond it") {
                    first.type shouldBe TokenType.IDENTIFIER
                    cursor.pos shouldBe 1
                    repeat(5) { cursor.advance().type shouldBe TokenType.EOF }
                    cursor.pos shouldBe 1
                }
            }
        }

        given("match and expect") {
            `when`("matching") {
                val cursor = cursorOf("select a")
                then("a miss leaves the cursor alone and a hit consumes the token") {
                    cursor.match(TokenType.FROM) shouldBe false
                    cursor.pos shouldBe 0
                    cursor.match(TokenType.SELECT) shouldBe true
                    cursor.pos shouldBe 1
                }
            }

            `when`("expecting the right token") {
                then("it is consumed and returned") {
                    val cursor = cursorOf("select")
                    cursor.expect(TokenType.SELECT, "SELECT").type shouldBe TokenType.SELECT
                    cursor.pos shouldBe 1
                }
            }

            `when`("expecting a token that is not there") {
                then("the error names what was expected and what was found, and nothing is consumed") {
                    val cursor = cursorOf("a")
                    unexpected {
                        cursor.expect(
                            TokenType.FROM,
                            "FROM"
                        )
                    }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected FROM but found 'a'"
                    cursor.pos shouldBe 0
                    unexpected {
                        cursorOf("").expect(
                            TokenType.FROM,
                            "FROM"
                        )
                    }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected FROM but found end of input"
                }
            }
        }

        given("words that are keywords only in some places") {
            `when`("checking, matching and expecting them") {
                then("only an unquoted identifier counts, never a quoted one or a real keyword") {
                    val cursor = cursorOf("table \"table\" select")
                    cursor.checkIsWord("table") shouldBe true
                    cursor.checkIsWord("table", 1) shouldBe false
                    cursor.checkIsWord("select", 2) shouldBe false
                    cursor.checkIsWord("table", 5) shouldBe false
                    cursor.matchWord("table") shouldBe true
                    cursor.matchWord("table") shouldBe false
                    cursor.pos shouldBe 1
                }
            }

            `when`("the expected word is missing") {
                then("the error shows the word in uppercase") {
                    unexpected { cursorOf("x").expectWord("key") }.message shouldBe
                            "[42601/UNEXPECTED_TOKEN] at offset 0: expected KEY but found 'x'"
                }
            }
        }

        given("identifiers") {
            `when`("reading unquoted and quoted names") {
                val cursor = cursorOf("Name \"Name\"")
                then("the unquoted one is lowercased and the quoted one keeps its case") {
                    cursor.expectIdentifier() shouldBe "name"
                    cursor.expectIdentifier() shouldBe "Name"
                }
            }

            `when`("a keyword, a number or the end of input stands where a name belongs") {
                then("it is rejected") {
                    unexpected { cursorOf("select").expectIdentifier() }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected identifier but found 'select'"
                    unexpected { cursorOf("1").expectIdentifier() }
                    unexpected { cursorOf("").expectIdentifier() }
                }
            }
        }

        given("integers") {
            `when`("reading unsigned and signed integers") {
                then("an optional minus is allowed, even with a space after it") {
                    cursorOf("5").expectInteger() shouldBe 5L
                    cursorOf("5").expectSignedInteger() shouldBe 5L
                    cursorOf("-5").expectSignedInteger() shouldBe -5L
                    cursorOf("- 5").expectSignedInteger() shouldBe -5L
                }
            }

            `when`("something other than an integer is found") {
                then("it is rejected, and an unsigned read refuses a minus") {
                    unexpected { cursorOf("-").expectSignedInteger() }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 1: expected integer but found end of input"
                    unexpected { cursorOf("1.5").expectInteger() }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected integer but found '1.5'"
                    unexpected { cursorOf("-x").expectSignedInteger() }
                    unexpected { cursorOf("-5").expectInteger() }
                }
            }
        }

        given("comma-separated lists") {
            `when`("reading one or several items") {
                then("every item comes back in order") {
                    cursorOf("a, b ,c").let { c -> c.commaSeparated { c.expectIdentifier() } } shouldBe listOf(
                        "a",
                        "b",
                        "c"
                    )
                    cursorOf("a").let { c -> c.commaSeparated { c.expectIdentifier() } } shouldBe listOf("a")
                }
            }

            `when`("the list is followed by something else") {
                val cursor = cursorOf("a b")
                val items = cursor.commaSeparated { cursor.expectIdentifier() }
                then("it stops after the last comma-joined item and leaves the rest") {
                    items shouldBe listOf("a")
                    cursor.current.value shouldBe "b"
                }
            }

            `when`("the list is malformed") {
                then("a trailing, a leading or a missing first item is rejected") {
                    unexpected { cursorOf("a,").let { c -> c.commaSeparated { c.expectIdentifier() } } }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 2: expected identifier but found end of input"
                    unexpected { cursorOf(",a").let { c -> c.commaSeparated { c.expectIdentifier() } } }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected identifier but found ','"
                    unexpected { cursorOf("").let { c -> c.commaSeparated { c.expectIdentifier() } } }
                }
            }
        }

        given("parentheses") {
            `when`("reading a parenthesized item and list") {
                then("both read through the closing parenthesis") {
                    cursorOf("(x)").let { c -> c.parenthesized { c.expectIdentifier() } } shouldBe "x"
                    cursorOf("(a, b)").let { c -> c.parenthesizedList { c.expectIdentifier() } } shouldBe listOf(
                        "a",
                        "b"
                    )
                }
            }

            `when`("a parenthesis is missing or the list is empty") {
                then("each case is rejected with its own message") {
                    unexpected { cursorOf("()").let { c -> c.parenthesizedList { c.expectIdentifier() } } }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 1: expected identifier but found ')'"
                    unexpected { cursorOf("(a").let { c -> c.parenthesizedList { c.expectIdentifier() } } }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 2: expected ')' but found end of input"
                    unexpected { cursorOf("a)").let { c -> c.parenthesizedList { c.expectIdentifier() } } }.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 0: expected '(' but found 'a'"
                }
            }
        }

        given("sort directions") {
            `when`("a direction word follows") {
                then("DESC is true, ASC is false, and either is consumed") {
                    cursorOf("desc").let { c -> c.matchDescending() shouldBe true; c.pos shouldBe 1 }
                    cursorOf("ASC").let { c -> c.matchDescending() shouldBe false; c.pos shouldBe 1 }
                }
            }

            `when`("no direction follows, or it is a quoted name") {
                then("it is false and nothing is consumed") {
                    cursorOf("x").let { c -> c.matchDescending() shouldBe false; c.pos shouldBe 0 }
                    cursorOf("\"desc\"").let { c -> c.matchDescending() shouldBe false; c.pos shouldBe 0 }
                }
            }
        }

        given("nesting depth") {
            fun dive(cursor: TokenCursor, levels: Int) {
                if (levels > 0) cursor.nested { dive(cursor, levels - 1) }
            }

            `when`("running a block nested") {
                val cursor = cursorOf("a")
                then("the depth rises inside it, falls after it, and the block's value is returned") {
                    cursor.depth shouldBe 0
                    cursor.nested { cursor.depth shouldBe 1; cursor.nested { cursor.depth shouldBe 2 } }
                    cursor.depth shouldBe 0
                    cursor.nested { 42 } shouldBe 42
                }
            }

            `when`("nesting reaches exactly the limit and one level past it") {
                then("200 levels are fine and 201 are rejected") {
                    dive(cursorOf("a"), 200)
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.NESTING_TOO_DEEP) { dive(cursorOf("a"), 201) }
                }
            }
        }

        given("building an error for a token other than the current one") {
            `when`("asking for it without throwing") {
                val cursor = cursorOf("a b")
                val error = cursor.unexpected("X", cursor[1])
                then("it points at that token and the cursor stays put") {
                    error.message shouldBe "[42601/UNEXPECTED_TOKEN] at offset 2: expected X but found 'b'"
                    cursor.pos shouldBe 0
                }
            }
        }
    })
