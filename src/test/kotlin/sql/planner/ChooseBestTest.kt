package sql.planner

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.Bound
import schema.IndexRow

class ChooseBestTest : BehaviorSpec({
    /** A candidate with only the fields chooseBest looks at; the bounds and the rest don't matter here. */
    fun candidate(
        name: String,
        equality: Int = 0,
        range: Boolean = false,
        ordered: Boolean = false,
        primary: Boolean = false,
        unique: Boolean = false,
    ) = IndexMatch(
        index = IndexRow(0L, name, "t", null, isPrimary = primary, isUnique = unique, keyColumns = emptyList()),
        equalityCount = equality,
        rangeColumn = if (range) "x" else null,
        lower = Bound(null, true),
        upper = Bound(null, true),
        consumed = emptySet(),
        satisfiesOrderBy = ordered,
        scanOrderBy = emptyList(),
    )

    fun winner(vararg matches: IndexMatch): String? = chooseBest(matches.toList())?.index?.indexName

    given("no candidates") {
        `when`("choosing") {
            then("there is nothing to choose, so the planner falls back to a full scan") {
                chooseBest(emptyList()) shouldBe null
            }
        }
    }

    given("a single candidate") {
        `when`("choosing") {
            then("it is chosen") {
                winner(candidate("only", range = true)) shouldBe "only"
            }
        }
    }

    given("candidates with different equality counts") {
        `when`("one pins more columns") {
            then("it wins even against a range, an ORDER BY and a primary key on the other side") {
                val more = candidate("more", equality = 2)
                val fewer = candidate("fewer", equality = 1, range = true, ordered = true, primary = true)
                winner(more, fewer) shouldBe "more"
                winner(fewer, more) shouldBe "more"
            }
        }
    }

    given("candidates with the same equality count") {
        `when`("only one has a range") {
            then("the range wins, even against an ORDER BY and a primary key on the other side") {
                val ranged = candidate("ranged", equality = 1, range = true)
                val plain = candidate("plain", equality = 1, ordered = true, primary = true)
                winner(ranged, plain) shouldBe "ranged"
                winner(plain, ranged) shouldBe "ranged"
            }
        }

        `when`("neither has a pinned column and only one has a range") {
            then("the range still wins") {
                val ranged = candidate("ranged", range = true)
                val orderOnly = candidate("orderOnly", ordered = true, unique = true)
                winner(orderOnly, ranged) shouldBe "ranged"
            }
        }
    }

    given("candidates equal on equality count and range") {
        `when`("only one satisfies the ORDER BY") {
            then("it wins, even against a primary key on the other side") {
                val ordered = candidate("ordered", equality = 1, ordered = true)
                val primary = candidate("primary", equality = 1, primary = true)
                winner(ordered, primary) shouldBe "ordered"
                winner(primary, ordered) shouldBe "ordered"
            }
        }
    }

    given("candidates equal on everything but uniqueness") {
        `when`("one is unique") {
            then("the unique one wins") {
                val unique = candidate("unique", equality = 1, unique = true)
                val plain = candidate("plain", equality = 1)
                winner(plain, unique) shouldBe "unique"
                winner(unique, plain) shouldBe "unique"
            }
        }

        `when`("one is the primary index") {
            then("the primary one wins") {
                val primary = candidate("primary", range = true, primary = true)
                val plain = candidate("plain", range = true)
                winner(plain, primary) shouldBe "primary"
                winner(primary, plain) shouldBe "primary"
            }
        }

        `when`("one is primary and the other unique") {
            then("they count the same, so the first one wins") {
                val primary = candidate("primary", equality = 1, primary = true)
                val unique = candidate("unique", equality = 1, unique = true)
                winner(primary, unique) shouldBe "primary"
                winner(unique, primary) shouldBe "unique"
            }
        }
    }

    given("candidates equal on every key") {
        `when`("choosing") {
            then("the first one in the list wins") {
                val a = candidate("a", equality = 1, range = true)
                val b = candidate("b", equality = 1, range = true)
                winner(a, b) shouldBe "a"
                winner(b, a) shouldBe "b"
            }
        }
    }

    given("many candidates") {
        `when`("they differ on different keys") {
            then("the keys are applied in priority order, wherever the winner sits in the list") {
                val all = listOf(
                    candidate("plain"),
                    candidate("orderedUnique", ordered = true, unique = true),
                    candidate("ranged", range = true),
                    candidate("onePinned", equality = 1),
                    candidate("onePinnedRanged", equality = 1, range = true),
                    candidate("onePinnedRangedOrdered", equality = 1, range = true, ordered = true),
                )
                for (rotation in all.indices) {
                    val rotated = all.drop(rotation) + all.take(rotation)
                    chooseBest(rotated)?.index?.indexName shouldBe "onePinnedRangedOrdered"
                }
            }
        }
    }
})
