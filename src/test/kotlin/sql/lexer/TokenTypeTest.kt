package sql.lexer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TokenTypeTest :
    BehaviorSpec({
        given("TokenType.keywordOf") {
            `when`("looking up a keyword in any case") {
                then("it finds the keyword's token type") {
                    TokenType.keywordOf("select") shouldBe TokenType.SELECT
                    TokenType.keywordOf("SELECT") shouldBe TokenType.SELECT
                    TokenType.keywordOf("SeLeCt") shouldBe TokenType.SELECT
                }
            }

            `when`("looking up an ordinary word") {
                then("there is no match") {
                    TokenType.keywordOf("users") shouldBe null
                    TokenType.keywordOf("") shouldBe null
                }
            }

            `when`("looking up the name of a TokenType constant that is not a keyword") {
                then("there is no match, so a user's identifier can't turn into an internal token") {
                    for (type in TokenType.entries.filter { !it.isKeyword }) {
                        TokenType.keywordOf(type.name) shouldBe null
                        TokenType.keywordOf(type.name.lowercase()) shouldBe null
                    }
                }
            }

            `when`("looking up the words that are only keywords in some grammar positions") {
                then("they are not in the table") {
                    for (word in listOf("table", "index", "unique", "key", "add", "asc", "desc")) {
                        TokenType.keywordOf(word) shouldBe null
                    }
                }
            }

            `when`("looking up every keyword by its own name") {
                then("each maps back to itself") {
                    for (type in TokenType.entries.filter { it.isKeyword }) {
                        TokenType.keywordOf(type.name) shouldBe type
                    }
                }
            }
        }
    })
