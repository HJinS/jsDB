package index.util

import index.comparator.MultiColumnKeyComparator
import index.serializer.MultiColumnKeySerializer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class CompareTest: FunSpec({
    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf(10),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5000L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(15232324),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 5023200L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(-15232324),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, -5023200L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, -52345132323002L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf(11),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5000L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice"),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", false),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("AAA", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Ali", false),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("AliceBro", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Banana", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 50.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(8.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(12.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 50.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(8.0F, 200.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 200.00),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(12.0F, 200.00),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(1609, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100)),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(30).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] > [${parameter.second}] with schema ${parameter.third}"){
            val serializer = MultiColumnKeySerializer(parameter.third)
            val comparator = MultiColumnKeyComparator(parameter.third)
            val serializedKey1 = serializer.serialize(parameter.first)
            val serializedKey2 = serializer.serialize(parameter.second)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val resultSerialized = comparator.compare(serializedKey1, serializedKey2)
            result shouldBeGreaterThan 0
            resultSerialized shouldBe 1
        }
    }

    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf(20),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(1523232423),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 52345132323002L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf(8),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("AliceBro", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Banana", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", false),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("AAA", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Ali", false),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice"),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(12.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 50.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(8.toByte(), 200.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 200.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(12.0F, 200.00),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 50.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(8.0F, 200.00),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(20).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100)),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] < [${parameter.second}] with ${parameter.third}"){
            val serializer = MultiColumnKeySerializer(parameter.third)
            val comparator = MultiColumnKeyComparator(parameter.third)
            val serializedKey1 = serializer.serialize(parameter.first)
            val serializedKey2 = serializer.serialize(parameter.second)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val resultSerialized = comparator.compare(serializedKey1, serializedKey2)
            result shouldBeLessThan 0
            resultSerialized shouldBe -1
        }
    }

    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 523451323230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, -523451323230L),
            listOf<Number>(15232324, -523451323230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(-15232324, 523451323230L),
            listOf<Number>(-15232324, 523451323230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(15232324, -523451323230L),
            listOf<Number>(15232324, -523451323230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = true),
                    IndexColumn("largeCount", ColumnType.LONG, descending = false)
                )
            )
        ),
        Triple(
            listOf<Number>(-15232324, 523451323230L),
            listOf<Number>(-15232324, 523451323230L),
            IndexKeySchema(
                listOf(
                    IndexColumn("count", ColumnType.INT, descending = false),
                    IndexColumn("largeCount", ColumnType.LONG, descending = true)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = false),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = false)
                )
            )
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", true),
            IndexKeySchema(
                listOf(
                    IndexColumn("name", ColumnType.STRING, descending = true),
                    IndexColumn("isActive", ColumnType.BOOLEAN, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 100.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = false),
                    IndexColumn("short", ColumnType.SHORT, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 100.toShort()),
            IndexKeySchema(
                listOf(
                    IndexColumn("byte", ColumnType.BYTE, descending = true),
                    IndexColumn("short", ColumnType.SHORT, descending = true)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 100.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = false),
                    IndexColumn("double", ColumnType.DOUBLE, descending = false)
                )
            )
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 100.0),
            IndexKeySchema(
                listOf(
                    IndexColumn("float", ColumnType.FLOAT, descending = true),
                    IndexColumn("double", ColumnType.DOUBLE, descending = true)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
                )
            )
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            IndexKeySchema(
                listOf(
                    IndexColumn("date", ColumnType.LOCAL_DATE, descending = true),
                    IndexColumn("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = false),
                    IndexColumn("bytes", ColumnType.BYTES, descending = false)
                )
            )
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            IndexKeySchema(
                listOf(
                    IndexColumn("instant", ColumnType.INSTANT, descending = true),
                    IndexColumn("bytes", ColumnType.BYTES, descending = true)
                )
            )
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] == [${parameter.second}] with ${parameter.third}"){
            val serializer = MultiColumnKeySerializer(parameter.third)
            val comparator = MultiColumnKeyComparator(parameter.third)
            val serializedKey1 = serializer.serialize(parameter.first)
            val serializedKey2 = serializer.serialize(parameter.second)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val resultSerialized = comparator.compare(serializedKey1, serializedKey2)
            result shouldBe 0
            resultSerialized shouldBe 0
        }
    }
})