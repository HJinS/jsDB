package index.serializer

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import java.util.Arrays
import kotlin.math.sign

/**
 * A key shorter than the schema (a prefix) is what range scans seek to and stop at, so its bytes must sit at the
 * *start* of its group: at or before every full key that shares the prefix, and after every key of an earlier group.
 * Issue #76: prefix keys used to be padded with the missing column's NULL byte, and a DESC NULL (0xFF) sorts after
 * every non-NULL DESC value.
 */
class PrefixKeyTest : BehaviorSpec({
    val values: List<Int?> = listOf(null, Int.MIN_VALUE, -1, 0, 1, 5, Int.MAX_VALUE)

    fun serializer(vararg descending: Boolean) = MultiColumnKeySerializer(
        IndexKeySchema(descending.mapIndexed { i, desc -> IndexColumn("c$i", ColumnType.INT, desc) })
    )

    fun direction(desc: Boolean) = if (desc) "DESC" else "ASC"

    /** Where [x] sorts relative to [y] in a column: NULL first when ascending, last when descending. */
    fun columnOrder(x: Int?, y: Int?, descending: Boolean): Int = when {
        x == y -> 0
        x == null -> if (descending) 1 else -1
        y == null -> if (descending) -1 else 1
        else -> if (descending) y.compareTo(x) else x.compareTo(y)
    }

    for (first in listOf(false, true)) for (second in listOf(false, true)) {
        val keys = serializer(first, second)

        given("an index on (${direction(first)}, ${direction(second)}) and a one-column prefix") {
            `when`("compared with the full keys that share the prefix") {
                then("it sorts strictly before every one of them, so it never equals a stored key") {
                    for (p in values) for (v in values) withClue("prefix [$p] vs key [$p, $v]") {
                        (Arrays.compareUnsigned(keys.serialize(listOf(p)), keys.serialize(listOf(p, v))) < 0) shouldBe true
                    }
                }
            }

            `when`("deserialized") {
                then("it comes back as exactly the prefix, with no extra column") {
                    for (p in values) withClue("prefix [$p]") {
                        keys.deserialize(keys.serialize(listOf(p))) shouldBe listOf(p)
                    }
                }
            }

            `when`("compared with the full keys of other groups") {
                then("it sorts exactly where its group does") {
                    for (p in values) for (q in values) for (v in values) {
                        if (p == q) continue
                        withClue("prefix [$p] vs key [$q, $v]") {
                            Arrays.compareUnsigned(keys.serialize(listOf(q, v)), keys.serialize(listOf(p))).sign shouldBe
                                    columnOrder(q, p, first).sign
                        }
                    }
                }
            }

            `when`("its successor (serializeUpper) is compared with the group") {
                then("it sorts after every key of the group and at or before the next group") {
                    for (p in values) {
                        val upper = keys.serializeUpper(listOf(p)) ?: continue // no successor: the group is the tree's end
                        for (v in values) withClue("upper of [$p] vs key [$p, $v]") {
                            (Arrays.compareUnsigned(upper, keys.serialize(listOf(p, v))) > 0) shouldBe true
                        }
                        for (q in values) for (v in values) {
                            if (columnOrder(q, p, first) <= 0) continue
                            withClue("upper of [$p] vs later key [$q, $v]") {
                                (Arrays.compareUnsigned(upper, keys.serialize(listOf(q, v))) <= 0) shouldBe true
                            }
                        }
                    }
                }
            }
        }
    }

    given("an index on (ASC, ASC, DESC) and a two-column prefix") {
        val keys = serializer(false, false, true)

        `when`("compared with the full keys that share the prefix") {
            then("it sorts at or before every one of them") {
                for (p in values) for (q in values) for (v in values) withClue("prefix [$p, $q] vs key [$p, $q, $v]") {
                    (Arrays.compareUnsigned(keys.serialize(listOf(p, q)), keys.serialize(listOf(p, q, v))) <= 0) shouldBe true
                }
            }
        }
    }

    given("an index on (ASC, DESC, ASC) and a one-column prefix") {
        val keys = serializer(false, true, false)

        `when`("compared with the full keys that share the prefix") {
            then("it sorts at or before every one of them, whatever the later columns hold") {
                for (p in values) for (q in values) for (v in values) withClue("prefix [$p] vs key [$p, $q, $v]") {
                    (Arrays.compareUnsigned(keys.serialize(listOf(p)), keys.serialize(listOf(p, q, v))) <= 0) shouldBe true
                }
            }
        }
    }
})
