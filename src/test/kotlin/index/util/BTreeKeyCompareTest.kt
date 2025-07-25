package index.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime


class BTreeKeyCompareTest: FunSpec({
    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf(10),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5000L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(15232324),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 5023200L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(-15232324),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, -5023200L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, -52345132323002L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf(11),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5000L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice"),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", false),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("AAA", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Ali", false),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("AliceBro", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Banana", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 50.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(8.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(12.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 50.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(8.0F, 200.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 200.00),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(12.0F, 200.00),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(1609, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100)),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(30).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] > [${parameter.second}] with schema ${parameter.third}"){
            val packedValue = KeyTool.pack(parameter.first, parameter.third)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val packedKey = KeyTool.pack(parameter.second, parameter.third)
            val resultPacked = packedValue.comparePackedKey(packedKey, parameter.third)
            result shouldBeGreaterThan 0
            resultPacked shouldBe 1
        }
    }

    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf(20),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf(1523232423),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 52345132323002L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf(8),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 10000L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("AliceBro", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Banana", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", false),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("AAA", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Ali", false),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice"),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(12.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 50.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(8.toByte(), 200.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 200.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(12.0F, 200.00),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 50.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(8.0F, 200.00),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(20).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100)),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] < [${parameter.second}] with ${parameter.third}"){
            val packedValue = KeyTool.pack(parameter.first, parameter.third)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val packedKey = KeyTool.pack(parameter.second, parameter.third)
            val resultPacked = packedValue.comparePackedKey(packedKey, parameter.third)
            result shouldBeLessThan 0
            resultPacked shouldBe -1
        }
    }

    listOf(
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, 523451323230L),
            listOf<Number>(15232324, 523451323230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(10, 5230L),
            listOf<Number>(10, 5230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, -523451323230L),
            listOf<Number>(15232324, -523451323230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(-15232324, 523451323230L),
            listOf<Number>(-15232324, 523451323230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(15232324, -523451323230L),
            listOf<Number>(15232324, -523451323230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf<Number>(-15232324, 523451323230L),
            listOf<Number>(-15232324, 523451323230L),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = true)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            ))
        ),
        Triple(
            listOf("Alice", true),
            listOf("Alice", true),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 100.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            ))
        ),
        Triple(
            listOf(10.toByte(), 100.toShort()),
            listOf(10.toByte(), 100.toShort()),
            KeySchema(listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 100.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            ))
        ),
        Triple(
            listOf(10.0F, 100.0),
            listOf(10.0F, 100.0),
            KeySchema(listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            ))
        ),
        Triple(
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0)),
            KeySchema(listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            ))
        ),
        Triple(
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array()),
            KeySchema(listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            ))
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] [${parameter.first}] == [${parameter.second}] with ${parameter.third}"){
            val packedValue = KeyTool.pack(parameter.first, parameter.third)
            val result = parameter.first.compareUnpackedKey(parameter.second, parameter.third)
            val packedKey = KeyTool.pack(parameter.second, parameter.third)
            val resultPacked = packedValue.comparePackedKey(packedKey, parameter.third)
            result shouldBe 0
            resultPacked shouldBe 0
        }
    }
})