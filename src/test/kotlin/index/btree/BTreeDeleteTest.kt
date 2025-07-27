package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe


/**
 * TODO key packing, compare 부분 int 숫자 키워서 비교 테스트 케이스 추가
 * - binarySearch 결과가 게속 이상함
 * */
class BTreeDeleteTest: BehaviorSpec({
    context("After deleting keys, the tree's state should be balanced by re-balancing"){
        val schema = KeySchema(listOf(
            Column("count", ColumnType.INT, descending = false),
            Column("largeCount", ColumnType.LONG, descending = false)
        ))
        val btree = BTree("test", "test table", schema, 3, true)
        val keys = listOf(
            listOf<Number>(1, 10L),
            listOf<Number>(5, 50L),
            listOf<Number>(3, 4L),
            listOf<Number>(4, 1032L),
            listOf<Number>(2, 12342L),
            listOf<Number>(210, 1234203L),
            listOf<Number>(523, 123932L),
            listOf<Number>(12, 12342322L),
            listOf<Number>(235, 123123932L),
            listOf<Number>(21, 1231342L),
            listOf<Number>(325, 1232932L),
            listOf<Number>(32, 1223342L),
            listOf<Number>(4, 23276L),
            listOf<Number>(1, 10L),
            listOf<Number>(2, 12342L),
            listOf<Number>(21, 1231342L)
        )
        for (key in keys) {
            btree.insert(key)
        }
        Given("A Tree with schema $schema"){
            var deleteKey = listOf<Number>(523, 123932L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(4,1032L),
                    listOf<Number>(4,23276L),
                    listOf<Number>(5,50L),
                    listOf<Number>(12,12342322L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(32,1223342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(32,1223342L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(4,1032L),
                    listOf<Number>(4,23276L),
                    listOf<Number>(5,50L),
                    listOf<Number>(12,12342322L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(12, 12342322L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(4,1032L),
                    listOf<Number>(4,23276L),
                    listOf<Number>(5,50L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(4,1032L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(4,23276L),
                    listOf<Number>(5,50L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(4,23276L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(5,50L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(5,50L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(21,1231342L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(3,4L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(3,4L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(21,1231342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(21,1231342L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(235,123123932L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(235,123123932L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(210,1234203L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(210,1234203L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(2,12342L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    listOf<Number>(1,10L),
                    listOf<Number>(1,10L),
                    listOf<Number>(2,12342L),
                    listOf<Number>(325,1232932L)
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }
        }
    }
})