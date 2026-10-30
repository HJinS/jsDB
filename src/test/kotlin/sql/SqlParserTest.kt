package sql

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import sql.ast.DropTable
import sql.ast.Select
import util.ErrorCode

class SqlParserTest :
    BehaviorSpec({
        given("SqlParser.parse") {
            `when`("given valid SQL") {
                then("it lexes and parses into a statement") {
                    SqlParser.parse("select * from t").shouldBeInstanceOf<Select>()
                    SqlParser.parse("DROP TABLE t").shouldBeInstanceOf<DropTable>()
                }
            }

            `when`("given the same SQL twice") {
                then("the results are equal") {
                    SqlParser.parse("select a from t where a = 1") shouldBe SqlParser.parse("select a from t where a = 1")
                }
            }

            `when`("the lexer rejects the input") {
                then("the lexer's error propagates") {
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.UNTERMINATED_STRING) { SqlParser.parse("select 'abc") }
                }
            }

            `when`("the parser rejects the tokens") {
                then("the parser's error propagates") {
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.UNEXPECTED_TOKEN) { SqlParser.parse("select from") }
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.UNEXPECTED_TOKEN) { SqlParser.parse("") }
                }
            }
        }
    })
