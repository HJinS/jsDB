package index.serializer

import index.util.IndexColumn
import index.util.ColumnType
import index.util.IndexKeySchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

class SerializerTest: FunSpec({
    listOf(
        listOf<Number>(10, 5230L) to IndexKeySchema(
            listOf(
                IndexColumn("count", ColumnType.INT, descending = false),
                IndexColumn("largeCount", ColumnType.LONG, descending = false)
            )
        ),
        listOf(10L) to IndexKeySchema(
            listOf(
                IndexColumn("id", ColumnType.LONG, descending = false)
            )
        ),
        listOf("Test Code") to IndexKeySchema(
            listOf(
                IndexColumn("name", ColumnType.STRING, descending = false)
            )
        ),
        listOf(true) to IndexKeySchema(
            listOf(
                IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
            )
        ),
        listOf(1.toByte()) to IndexKeySchema(
            listOf(
                IndexColumn("byte", ColumnType.BYTE, descending = false)
            )
        ),
        listOf(10.toShort()) to IndexKeySchema(
            listOf(
                IndexColumn("idShort", ColumnType.SHORT, descending = false)
            )
        ),
        listOf(10.0f) to IndexKeySchema(
            listOf(
                IndexColumn("price", ColumnType.FLOAT, descending = false)
            )
        ),
        listOf(10.0) to IndexKeySchema(
            listOf(
                IndexColumn("id", ColumnType.DOUBLE, descending = false)
            )
        ),
        listOf(LocalDate.of(2025, 1, 1)) to IndexKeySchema(
            listOf(
                IndexColumn("date", ColumnType.LOCAL_DATE, descending = false)
            )
        ),
        listOf(LocalDateTime.of(2025, 1, 1, 12, 0, 0)) to IndexKeySchema(
            listOf(
                IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            )
        ),
        listOf(Instant.ofEpochSecond(100)) to IndexKeySchema(
            listOf(
                IndexColumn("epoch", ColumnType.INSTANT, descending = false)
            )
        ),
        listOf(UUID.randomUUID()) to IndexKeySchema(
            listOf(
                IndexColumn("uuid", ColumnType.UUID, descending = false)
            )
        ),
        listOf(ByteBuffer.allocate(2).putShort(10).array()) to IndexKeySchema(
            listOf(
                IndexColumn("bytes", ColumnType.BYTES, descending = false)
            )
        ),
        listOf(10, "Alice", LocalDate.of(2025, 5, 10)) to IndexKeySchema(
            listOf(
                IndexColumn("id", ColumnType.INT, descending = false),
                IndexColumn("name", ColumnType.STRING, descending = false),
                IndexColumn("birth", ColumnType.LOCAL_DATE, descending = false)
            )
        ),
        listOf(true, 10.0f, UUID.randomUUID()) to IndexKeySchema(
            listOf(
                IndexColumn("isActive", ColumnType.BOOLEAN, descending = false),
                IndexColumn("price", ColumnType.FLOAT, descending = false),
                IndexColumn("uuid", ColumnType.UUID, descending = false)
            )
        ),
        listOf(Instant.ofEpochSecond(100), "Alice", ByteBuffer.allocate(2).putShort(10).array()) to IndexKeySchema(
            listOf(
                IndexColumn("epoch", ColumnType.INSTANT, descending = false),
                IndexColumn("name", ColumnType.STRING, descending = false),
                IndexColumn("bytes", ColumnType.BYTES, descending = false)
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] Original value[${parameter.first}] should be same after packing, unpacking"){
            val serializer = MultiColumnKeySerializer(parameter.second)
            val serializedKey1 = serializer.serialize(parameter.first)
            val deSerialized = serializer.deserialize(serializedKey1)
            for ((key1, key2) in parameter.first.zip(deSerialized)){
                key1 shouldBe key2
            }
        }
    }

    test("Original value should be same after packing, unpacking string collator comparison case"){
        val key = listOf(10, "Alice", LocalDate.of(2025, 5, 10))
        val collatorInstance = Collator.getInstance(Locale.US)
        val schema = IndexKeySchema(
            listOf(
                IndexColumn("id", ColumnType.INT, descending = false),
                IndexColumn("name", ColumnType.STRING, descending = false, collation = collatorInstance),
                IndexColumn("birth", ColumnType.LOCAL_DATE, descending = false)
            )
        )
        val serializer = MultiColumnKeySerializer(schema)
        val serializedKey1 = serializer.serialize(key)
        val deSerialized = serializer.deserialize(serializedKey1)

        for ((key1, key2) in key.zip(deSerialized)){
            if (key1 == "Alice"){
                val collatedBytes = collatorInstance.getCollationKey("Alice").toByteArray()
                key2 shouldBe "[CollationKey(${collatedBytes.joinToString(""){ "%02x".format(it) }})]"
            } else {
                key1 shouldBe key2
            }
        }
    }
})