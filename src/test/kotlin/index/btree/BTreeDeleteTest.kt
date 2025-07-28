package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate


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

        val schema2 = KeySchema(listOf(
            Column("name", ColumnType.STRING, descending = false),
            Column("date", ColumnType.LOCAL_DATE, descending = false)
        ))
        val btree2 = BTree("test", "test table 2", schema2, 3, true)
        val keys2 = listOf(
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("ElijahKim", LocalDate.of(1997, 12, 25)),
            listOf("Lucas", LocalDate.of(1697, 12, 25)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("soif", LocalDate.of(2020, 1, 30)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25)),
            listOf("Chloed", LocalDate.of(2020, 12, 25))
        )
        for (key in keys2) {
            btree2.insert(key)
        }

        btree2.printTree()

        Given("A Tree with schema $schema2"){
            var deleteKey = listOf("ElijahKim", LocalDate.of(1997, 12, 25))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Ava", LocalDate.of(2025, 4, 30)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Chloed", LocalDate.of(2020, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Elijah", LocalDate.of(1997, 12, 25)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2020, 1, 30)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("soif", LocalDate.of(2020, 1, 30))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Elijah", LocalDate.of(1997, 12, 25))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Ava", LocalDate.of(2025, 4, 30)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Chloed", LocalDate.of(2020, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2020, 1, 30)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("soif", LocalDate.of(2020, 1, 30))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Chloed", LocalDate.of(2020, 12, 25))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Ava", LocalDate.of(2025, 4, 30)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2020, 1, 30)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("soif", LocalDate.of(2020, 1, 30))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Grace", LocalDate.of(2020, 1, 30))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Ava", LocalDate.of(2025, 4, 30)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("soif", LocalDate.of(2020, 1, 30))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Ava", LocalDate.of(2025, 4, 30))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("soif", LocalDate.of(2020, 1, 30))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("soif", LocalDate.of(2020, 1, 30))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Faith", LocalDate.of(2022, 1, 18))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Lucas", LocalDate.of(1697, 12, 25))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Grace", LocalDate.of(2024, 3, 20))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }
        }
    }
})