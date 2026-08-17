package index.serializer

import index.util.ColumnType
import index.util.RowColumn
import index.util.RowSchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class BinaryRowSerializerTest: FunSpec({
    listOf(
        listOf<Number>(10, 5230L) to RowSchema(
            listOf(
                RowColumn("count", ColumnType.INT),
                RowColumn("largeCount", ColumnType.LONG)
            )
        ),
        listOf(10L) to RowSchema(
            listOf(
                RowColumn("id", ColumnType.LONG)
            )
        ),
        listOf("Test Code") to RowSchema(
            listOf(
                RowColumn("name", ColumnType.STRING)
            )
        ),
        listOf(true) to RowSchema(
            listOf(
                RowColumn("isActive", ColumnType.BOOLEAN)
            )
        ),
        listOf(1.toByte()) to RowSchema(
            listOf(
                RowColumn("byte", ColumnType.BYTE)
            )
        ),
        listOf(10.toShort()) to RowSchema(
            listOf(
                RowColumn("idShort", ColumnType.SHORT)
            )
        ),
        listOf(10.0f) to RowSchema(
            listOf(
                RowColumn("price", ColumnType.FLOAT)
            )
        ),
        listOf(10.0) to RowSchema(
            listOf(
                RowColumn("id", ColumnType.DOUBLE)
            )
        ),
        listOf(LocalDate.of(2025, 1, 1)) to RowSchema(
            listOf(
                RowColumn("date", ColumnType.LOCAL_DATE)
            )
        ),
        listOf(LocalDateTime.of(2025, 1, 1, 12, 0, 0)) to RowSchema(
            listOf(
                RowColumn("dateTime", ColumnType.LOCAL_DATE_TIME)
            )
        ),
        listOf(Instant.ofEpochSecond(100)) to RowSchema(
            listOf(
                RowColumn("epoch", ColumnType.INSTANT)
            )
        ),
        listOf(UUID.randomUUID()) to RowSchema(
            listOf(
                RowColumn("uuid", ColumnType.UUID)
            )
        ),
        listOf(ByteBuffer.allocate(2).putShort(10).array()) to RowSchema(
            listOf(
                RowColumn("bytes", ColumnType.BYTES)
            )
        ),
        listOf(10, "Alice", LocalDate.of(2025, 5, 10)) to RowSchema(
            listOf(
                RowColumn("id", ColumnType.INT),
                RowColumn("name", ColumnType.STRING),
                RowColumn("birth", ColumnType.LOCAL_DATE)
            )
        ),
        listOf(true, 10.0f, UUID.randomUUID()) to RowSchema(
            listOf(
                RowColumn("isActive", ColumnType.BOOLEAN),
                RowColumn("price", ColumnType.FLOAT),
                RowColumn("uuid", ColumnType.UUID)
            )
        ),
        listOf(Instant.ofEpochSecond(100), "Alice", ByteBuffer.allocate(2).putShort(10).array()) to RowSchema(
            listOf(
                RowColumn("epoch", ColumnType.INSTANT),
                RowColumn("name", ColumnType.STRING),
                RowColumn("bytes", ColumnType.BYTES)
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] Original value[${parameter.first}] should be same after serializing, deserializing"){
            val serializer = BinaryRowSerializer(parameter.second)
            val serializedKey1 = serializer.serialize(parameter.first)
            val deSerialized = serializer.deserialize(serializedKey1)
            for ((key1, key2) in parameter.first.zip(deSerialized)){
                key1 shouldBe key2
            }
        }
    }
})