package index.serializer

import index.util.ColumnType
import index.util.RowColumn
import index.util.RowSchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random


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
        listOf(Random.nextBits(8).toByte()) to RowSchema(
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
        listOf(Random.nextBytes(Random.nextInt(100))) to RowSchema(
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
        listOf(Instant.ofEpochSecond(100), "Alice", Random.nextBytes(Random.nextInt(100))) to RowSchema(
            listOf(
                RowColumn("epoch", ColumnType.INSTANT),
                RowColumn("name", ColumnType.STRING),
                RowColumn("bytes", ColumnType.BYTES)
            )
        ),
        listOf(
            Instant.ofEpochSecond(100),
            "Alice",
            Random.nextBytes(Random.nextInt(100)),
            null,
            Random.nextFloat(),
            Random.nextDouble(),
            Random.nextInt(),
            null,
            null
        ) to RowSchema(
            listOf(
                RowColumn("column1", ColumnType.INSTANT),
                RowColumn("column2", ColumnType.STRING),
                RowColumn("column3", ColumnType.BYTES),
                RowColumn("column4", ColumnType.UUID),
                RowColumn("column5", ColumnType.FLOAT),
                RowColumn("column6", ColumnType.DOUBLE),
                RowColumn("column7", ColumnType.INT),
                RowColumn("column8", ColumnType.BYTE),
                RowColumn("column9", ColumnType.BOOLEAN)
            )
        ),
        listOf(
            Random.nextInt(),
            UUID.randomUUID(),
            null,
            Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
            Random.nextLong(),
            LocalDate.of(2025, 5, 10),
            null,
            null,
            null,
            Random.nextBytes(Random.nextInt(100)),
            Random.nextBits(8).toByte(),
            null,
            Random.nextDouble(),
            "adsfasasdfa-as-dfashdf-dfas",
            Random.nextBytes(Random.nextInt(100)),
            null
        ) to RowSchema(
            listOf(
                RowColumn("column1", ColumnType.INT),
                RowColumn("column2", ColumnType.UUID),
                RowColumn("column3", ColumnType.LOCAL_DATE_TIME),
                RowColumn("column4", ColumnType.SHORT),
                RowColumn("column5", ColumnType.LONG),
                RowColumn("column6", ColumnType.LOCAL_DATE),
                RowColumn("column7", ColumnType.INT),
                RowColumn("column8", ColumnType.UUID),
                RowColumn("column9", ColumnType.BOOLEAN),
                RowColumn("column9", ColumnType.BYTES),
                RowColumn("column9", ColumnType.BYTE),
                RowColumn("column9", ColumnType.BOOLEAN),
                RowColumn("column9", ColumnType.DOUBLE),
                RowColumn("column9", ColumnType.STRING),
                RowColumn("column9", ColumnType.BYTES),
                RowColumn("column9", ColumnType.BYTES),
            )
        ),
        listOf(
            null,
            "Alice",
            Random.nextBytes(Random.nextInt( 100)),
            null,
            Random.nextFloat(),
            Random.nextDouble(),
            Random.nextInt(),
            null
        ) to RowSchema(
            listOf(
                RowColumn("column1", ColumnType.INSTANT),
                RowColumn("column2", ColumnType.STRING),
                RowColumn("column3", ColumnType.BYTES),
                RowColumn("column4", ColumnType.UUID),
                RowColumn("column5", ColumnType.FLOAT),
                RowColumn("column6", ColumnType.DOUBLE),
                RowColumn("column7", ColumnType.INT),
                RowColumn("column8", ColumnType.BYTE)
            )
        ),
        // 경계값: INT
        listOf(Int.MIN_VALUE, Int.MAX_VALUE) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.INT),
                RowColumn("max", ColumnType.INT)
            )
        ),
        // 경계값: LONG
        listOf(Long.MIN_VALUE, Long.MAX_VALUE) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.LONG),
                RowColumn("max", ColumnType.LONG)
            )
        ),
        // 경계값: SHORT
        listOf(Short.MIN_VALUE, Short.MAX_VALUE) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.SHORT),
                RowColumn("max", ColumnType.SHORT)
            )
        ),
        // 경계값: BYTE
        listOf(Byte.MIN_VALUE, Byte.MAX_VALUE) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.BYTE),
                RowColumn("max", ColumnType.BYTE)
            )
        ),
        // 경계값: FLOAT 특수값
        listOf(Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.FLOAT),
                RowColumn("max", ColumnType.FLOAT),
                RowColumn("nan", ColumnType.FLOAT),
                RowColumn("posInf", ColumnType.FLOAT),
                RowColumn("negInf", ColumnType.FLOAT)
            )
        ),
        // 경계값: DOUBLE 특수값
        listOf(Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY) to RowSchema(
            listOf(
                RowColumn("min", ColumnType.DOUBLE),
                RowColumn("max", ColumnType.DOUBLE),
                RowColumn("nan", ColumnType.DOUBLE),
                RowColumn("posInf", ColumnType.DOUBLE),
                RowColumn("negInf", ColumnType.DOUBLE)
            )
        ),
        // 빈 String
        listOf("") to RowSchema(
            listOf(RowColumn("empty", ColumnType.STRING))
        ),
        // 빈 ByteArray
        listOf(byteArrayOf()) to RowSchema(
            listOf(RowColumn("empty", ColumnType.BYTES))
        ),
        // Unicode 문자열
        listOf("한글", "🎉日本語") to RowSchema(
            listOf(
                RowColumn("korean", ColumnType.STRING),
                RowColumn("emoji", ColumnType.STRING)
            )
        ),
        // null bitmap 경계: 8번째 컬럼 null (bit 0 of byte 0)
        listOf(1, 2, 3, 4, 5, 6, 7, null) to RowSchema(
            listOf(
                RowColumn("col1", ColumnType.INT),
                RowColumn("col2", ColumnType.INT),
                RowColumn("col3", ColumnType.INT),
                RowColumn("col4", ColumnType.INT),
                RowColumn("col5", ColumnType.INT),
                RowColumn("col6", ColumnType.INT),
                RowColumn("col7", ColumnType.INT),
                RowColumn("col8", ColumnType.INT)
            )
        ),
        // null bitmap 경계: 9번째 컬럼 null (bit 7 of byte 1, byte 경계)
        listOf(1, 2, 3, 4, 5, 6, 7, 8, null) to RowSchema(
            listOf(
                RowColumn("col1", ColumnType.INT),
                RowColumn("col2", ColumnType.INT),
                RowColumn("col3", ColumnType.INT),
                RowColumn("col4", ColumnType.INT),
                RowColumn("col5", ColumnType.INT),
                RowColumn("col6", ColumnType.INT),
                RowColumn("col7", ColumnType.INT),
                RowColumn("col8", ColumnType.INT),
                RowColumn("col9", ColumnType.INT)
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] Original value[${parameter.first}] should be same after serializing, deserializing"){
            val serializer = BinaryRowSerializer(parameter.second)
            val serializedKey1 = serializer.serialize(parameter.first)
            val deSerialized = serializer.deserialize(serializedKey1)
            for ((key1, key2) in parameter.first.zip(deSerialized.first)){
                key1 shouldBe key2
            }
        }
    }
})