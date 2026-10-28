package sql.lexer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CharCursorTest :
    BehaviorSpec({
        given("a cursor over \"abc\"") {
            `when`("freshly created") {
                val cursor = CharCursor("abc")
                then("it sits at the start and can look ahead without moving") {
                    cursor.pos shouldBe 0
                    cursor.current shouldBe 'a'
                    cursor[1] shouldBe 'b'
                    cursor[2] shouldBe 'c'
                    cursor.pos shouldBe 0
                    cursor.atEnd shouldBe false
                }
            }

            `when`("looking past the end") {
                val cursor = CharCursor("abc")
                then("it yields the NUL sentinel instead of throwing") {
                    cursor[3] shouldBe '\u0000'
                    cursor[100] shouldBe '\u0000'
                }
            }

            `when`("advancing one at a time and then by several") {
                val cursor = CharCursor("abc")
                cursor.advance()
                val afterOne = cursor.current
                cursor.advance(2)
                then("the position follows, and the end is reached exactly at the length") {
                    afterOne shouldBe 'b'
                    cursor.pos shouldBe 3
                    cursor.atEnd shouldBe true
                    cursor.current shouldBe '\u0000'
                }
            }

            `when`("slicing from an earlier position") {
                val cursor = CharCursor("abc")
                cursor.advance(2)
                then("it returns the text between that position and the current one") {
                    cursor.slice(0) shouldBe "ab"
                    cursor.slice(1) shouldBe "b"
                    cursor.slice(2) shouldBe ""
                }
            }
        }

        given("a cursor over an empty input") {
            `when`("freshly created") {
                val cursor = CharCursor("")
                then("it is already at the end") {
                    cursor.atEnd shouldBe true
                    cursor.current shouldBe '\u0000'
                }
            }
        }

        given("a cursor over text that really contains a NUL character") {
            `when`("reading the NUL at the end-of-input sentinel's value") {
                val cursor = CharCursor("a\u0000")
                cursor.advance()
                then("the end is decided by position, not by the character's value") {
                    cursor.current shouldBe '\u0000'
                    cursor.atEnd shouldBe false
                    cursor.advance()
                    cursor.atEnd shouldBe true
                }
            }
        }
    })
