package index.btree

import index.comparator.MultiColumnKeyComparator
import index.serializer.LocalDateSerializer
import index.serializer.MultiColumnKeySerializer
import index.serializer.RowDataSerializer
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.time.LocalDate


class BTreeDeleteTest: BehaviorSpec({
    @Serializable
    data class IDData(val id: Int, val longId: Long)

    @Serializable
    data class UserData(
        val name: String,
        @Serializable(with = LocalDateSerializer::class)
        val birthDate: LocalDate
    )

    val idValueSerializer = RowDataSerializer(IDData::class)

    val userDataSerializer = RowDataSerializer(UserData::class)

    context("After deleting keys, the tree's state should be balanced by re-balancing"){
        val schema = KeySchema(listOf(
            Column("count", ColumnType.INT, descending = false),
            Column("largeCount", ColumnType.LONG, descending = false)
        ))

        val keySerializer = MultiColumnKeySerializer(schema)
        val btree = BTree(
            "test",
            "test table",
            keySerializer,
            idValueSerializer,
            MultiColumnKeyComparator(schema),
            2,
            true
        )

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
            val value = IDData(
                id = key[0] as Int,
                longId = key[1] as Long
            )
            btree.insert(key, value)
        }


        Given("A Tree with schema $schema"){
            var deleteKey = listOf<Number>(523, 123932L)
            When("Delete key $deleteKey"){
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults = listOf(
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(4,1032L),
                    IDData(4,23276L),
                    IDData(5,50L),
                    IDData(12,12342322L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(32,1223342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(4,1032L),
                    IDData(4,23276L),
                    IDData(5,50L),
                    IDData(12,12342322L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(4,1032L),
                    IDData(4,23276L),
                    IDData(5,50L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(4,23276L),
                    IDData(5,50L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(5,50L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(21,1231342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(3,4L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(21,1231342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(210,1234203L),
                    IDData(235,123123932L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(210,1234203L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(2,12342L),
                    IDData(325,1232932L)
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
                    IDData(1,10L),
                    IDData(1,10L),
                    IDData(2,12342L),
                    IDData(325,1232932L)
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
        val keySerializer2 = MultiColumnKeySerializer(schema2)
        val btree2 = BTree(
            "test",
            "test table",
            keySerializer2,
            userDataSerializer,
            MultiColumnKeyComparator(schema2),
            2,
            true
        )
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
            val value = UserData(
                name = key[0] as String,
                birthDate = key[1] as LocalDate,
            )
            btree2.insert(key, value)
        }

        btree2.printTree()

        Given("A Tree with schema $schema2"){
            var deleteKey = listOf("ElijahKim", LocalDate.of(1997, 12, 25))
            When("Delete key $deleteKey"){
                btree2.delete(deleteKey)
                btree2.printTree()
                val expectedResults = listOf(
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Ava", LocalDate.of(2025, 4, 30)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Chloed", LocalDate.of(2020, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Elijah", LocalDate.of(1997, 12, 25)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2020, 1, 30)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    UserData("soif", LocalDate.of(2020, 1, 30))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Ava", LocalDate.of(2025, 4, 30)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Chloed", LocalDate.of(2020, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2020, 1, 30)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    UserData("soif", LocalDate.of(2020, 1, 30))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Ava", LocalDate.of(2025, 4, 30)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2020, 1, 30)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    UserData("soif", LocalDate.of(2020, 1, 30))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Ava", LocalDate.of(2025, 4, 30)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    UserData("soif", LocalDate.of(2020, 1, 30))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    UserData("soif", LocalDate.of(2020, 1, 30))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Faith", LocalDate.of(2022, 1, 18)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Daniel", LocalDate.of(2018, 4, 9)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Grace", LocalDate.of(2024, 3, 20)),
                    UserData("Lucas", LocalDate.of(1697, 12, 25))
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
                    UserData("Ava", LocalDate.of(2019, 12, 25)),
                    UserData("Chloe", LocalDate.of(2019, 12, 25)),
                    UserData("Grace", LocalDate.of(2024, 3, 20))
                )
                Then("Trace result should be $expectedResults"){
                    val allKeys = btree2.traverse()
                    allKeys.toList().map{ it.second } shouldBe expectedResults
                }
            }
        }
    }
})