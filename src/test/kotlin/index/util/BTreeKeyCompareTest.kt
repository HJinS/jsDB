package index.util

import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/*
* INT, LONG, STRING, BOOLEAN, BYTE, SHORT, FLOAT, DOUBLE, LOCAL_DATE, LOCAL_DATE_TIME, INSTANT, UUID, BYTES
* */
class BTreeKeyCompareTest {
    private val logger = KotlinLogging.logger {}
    @Test
    @DisplayName("Given `field(int, long), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key int long`() {
        val value = listOf<Number>(10, 5230L)
        val findKey = listOf(10)
        val schema = KeySchema(
            listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(20)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, -1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf<Number>(10, 5000L)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 1)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf<Number>(10, 10000L)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -1)
        assertEquals(result4, resultPacked4)
    }

    @Test
    @DisplayName("Given `field(int(desc), Long), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key int desc long`() {
        val value = listOf<Number>(10, 5230L)
        val findKey = listOf(11)
        val schema = KeySchema(
            listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(8)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, -1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf<Number>(10, 5000L)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 1)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf<Number>(10, 10000L)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -1)
        assertEquals(result4, resultPacked4)
    }

    @Test
    @DisplayName("Given `field(string, boolean), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key string boolean`() {
        val value = listOf("Alice", true)
        val findKey = listOf("Alice")
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("isActive", ColumnType.BOOLEAN, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf("Alice", true)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, 0)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf("Alice", false)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 1)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf("AliceBro", true)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -3)
        assertEquals(resultPacked4, -1)

        val findKey5 = listOf("Banana", true)
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, -1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf("AAA", true)
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, 43)
        assertEquals(resultPacked6, 1)

        val findKey7 = listOf("Ali", false)
        val result7 = value.compareUnpackedKey(findKey7, schema)
        val packedKey7 = KeyTool.pack(findKey7, schema)
        val resultPacked7 = packedValue.comparePackedKey(packedKey7, schema)
        assertEquals(result7, 2)
        assertEquals(resultPacked7, 1)
    }

    @Test
    @DisplayName("Given `field(string(desc), boolean(desc)), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key string desc boolean desc`() {
        val value = listOf("Alice", true)
        val findKey = listOf("Alice")
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("isActive", ColumnType.BOOLEAN, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, -1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf("Alice", true)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, 0)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf("Alice", false)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, -1)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf("AliceBro", true)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, 3)
        assertEquals(resultPacked4, 1)

        val findKey5 = listOf("Banana", true)
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, 1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf("AAA", true)
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, -43)
        assertEquals(resultPacked6, -1)

        val findKey7 = listOf("Ali", false)
        val result7 = value.compareUnpackedKey(findKey7, schema)
        val packedKey7 = KeyTool.pack(findKey7, schema)
        val resultPacked7 = packedValue.comparePackedKey(packedKey7, schema)
        assertEquals(result7, -2)
        assertEquals(resultPacked7, -1)
    }

    @Test
    @DisplayName("Given `field(byte, short), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key byte short`() {
        val value = listOf(10.toByte(), 100.toShort())
        val findKey = listOf(10.toByte())
        val schema = KeySchema(
            listOf(
                Column("byte", ColumnType.BYTE, descending = false),
                Column("short", ColumnType.SHORT, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(10.toByte(), 50.toShort())
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, 1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(10.toByte(), 100.toShort())
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(10.toByte(), 200.toShort())
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -1)
        assertEquals(result4, resultPacked4)

        val findKey5 = listOf(8.toByte(), 200.toShort())
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, 1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(12.toByte(), 200.toShort())
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, -1)
        assertEquals(result6, resultPacked6)
    }

    @Test
    @DisplayName("Given `field(byte(desc), short(desc)), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key byte desc short desc`() {
        val value = listOf(10.toByte(), 100.toShort())
        val findKey = listOf(10.toByte())
        val schema = KeySchema(
            listOf(
                Column("byte", ColumnType.BYTE, descending = true),
                Column("short", ColumnType.SHORT, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, -1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(10.toByte(), 50.toShort())
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, -1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(10.toByte(), 100.toShort())
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(10.toByte(), 200.toShort())
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, 1)
        assertEquals(result4, resultPacked4)

        val findKey5 = listOf(8.toByte(), 200.toShort())
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, -1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(12.toByte(), 200.toShort())
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, 1)
        assertEquals(result6, resultPacked6)
    }

    @Test
    @DisplayName("Given `field(float, double), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key float double`() {
        val value = listOf(10.0F, 100.0)
        val findKey = listOf(10.0F)
        val schema = KeySchema(
            listOf(
                Column("float", ColumnType.FLOAT, descending = false),
                Column("double", ColumnType.DOUBLE, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(10.0F, 50.0)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, 1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(10.0F, 100.00)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(10.0F, 200.0)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -1)
        assertEquals(result4, resultPacked4)

        val findKey5 = listOf(8.0F, 200.0)
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, 1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(12.0F, 200.00)
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, -1)
        assertEquals(result6, resultPacked6)
    }

    @Test
    @DisplayName("Given `field(float(desc), double(desc)), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key float desc double desc`() {
        val value = listOf(10.0F, 100.0)
        val findKey = listOf(10.0F)
        val schema = KeySchema(
            listOf(
                Column("float", ColumnType.FLOAT, descending = true),
                Column("double", ColumnType.DOUBLE, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, -1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(10.0F, 50.0)
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, -1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(10.0F, 100.00)
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(10.0F, 200.00)
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, 1)
        assertEquals(result4, resultPacked4)

        val findKey5 = listOf(8.0F, 200.00)
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, -1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(12.0F, 200.00)
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, 1)
        assertEquals(result6, resultPacked6)
    }

    @Test
    @DisplayName("Given `field(localDate, localDateTime), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key local date local date time`() {
        val value = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val findKey = listOf(LocalDate.of(2024, 1, 1))
        val schema = KeySchema(
            listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, 1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0))
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, 1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0))
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(result4, -1)
        assertEquals(result4, resultPacked4)

        val findKey5 = listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(result5, 1)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(result6, -1)
        assertEquals(result6, resultPacked6)
    }

    @Test
    @DisplayName("Given `field(localDate(desc), localDateTime(desc)), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key local date desc local date time desc`() {
        val value = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val findKey = listOf(LocalDate.of(2024, 1, 1))
        val schema = KeySchema(
            listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = true),
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(result, -1)
        assertEquals(result, resultPacked)

        val findKey2 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,18,0,0))
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(result2, -1)
        assertEquals(result2, resultPacked2)

        val findKey3 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(result3, 0)
        assertEquals(result3, resultPacked3)

        val findKey4 = listOf(LocalDate.of(2024, 1, 1), LocalDateTime.of(2024,1,1,23,59,0))
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(1, result4)
        assertEquals(resultPacked4, result4)

        val findKey5 = listOf(LocalDate.of(2023, 12, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(-1, result5)
        assertEquals(result5, resultPacked5)

        val findKey6 = listOf(LocalDate.of(2025, 1, 1), LocalDateTime.of(2024,1,1,23,0,0))
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(1, result6)
        assertEquals(resultPacked6, result6)
    }

    @Test
    @DisplayName("Given `field(instant, bytes), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key instant bytes`() {
        val value = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array())
        val findKey = listOf(Instant.ofEpochSecond(100))
        val schema = KeySchema(
            listOf(
                Column("instant", ColumnType.INSTANT, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            )
        )
        val descSchema = KeySchema(
            listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(1, result)
        assertEquals(resultPacked, result)

        val findKey2 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array())
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val packedKeyDesc2 = KeyTool.pack(findKey2, descSchema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(1, result2)
        assertEquals(resultPacked2, result2)

        val findKey3 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array())
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(0, result3)
        assertEquals(resultPacked3, result3)

        val findKey4 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(20).array())
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(-1, result4)
        assertEquals(resultPacked4, result4)


        val findKey5 = listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array())
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(1, result5)
        assertEquals(resultPacked5, result5)


        val findKey6 = listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array())
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(-1, result6)
        assertEquals(resultPacked6, result6)

    }

    @Test
    @DisplayName("Given `field(instant(desc), bytes(desc)), when compare keys, Then result will be lexicographic and packedKey result should be same`")
    fun `테스트 compare key instant desc bytes desc`() {
        val value = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array())
        val findKey = listOf(Instant.ofEpochSecond(100))
        val schema = KeySchema(
            listOf(
                Column("instant", ColumnType.INSTANT, descending = true),
                Column("bytes", ColumnType.BYTES, descending = true)
            )
        )
        val packedValue = KeyTool.pack(value, schema)
        val result = value.compareUnpackedKey(findKey, schema)
        val packedKey = KeyTool.pack(findKey, schema)
        val resultPacked = packedValue.comparePackedKey(packedKey, schema)
        assertEquals(-1, result)
        assertEquals(resultPacked, result)

        val findKey2 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(5).array())
        val result2 = value.compareUnpackedKey(findKey2, schema)
        val packedKey2 = KeyTool.pack(findKey2, schema)
        val resultPacked2 = packedValue.comparePackedKey(packedKey2, schema)
        assertEquals(-1, result2)
        assertEquals(resultPacked2, result2)


        val findKey3 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(10).array())
        val result3 = value.compareUnpackedKey(findKey3, schema)
        val packedKey3 = KeyTool.pack(findKey3, schema)
        val resultPacked3 = packedValue.comparePackedKey(packedKey3, schema)
        assertEquals(0, result3)
        assertEquals(resultPacked3, result3)


        val findKey4 = listOf(Instant.ofEpochSecond(100), ByteBuffer.allocate(2).putShort(30).array())
        val result4 = value.compareUnpackedKey(findKey4, schema)
        val packedKey4 = KeyTool.pack(findKey4, schema)
        val resultPacked4 = packedValue.comparePackedKey(packedKey4, schema)
        assertEquals(1, result4)
        assertEquals(resultPacked4, result4)

        val findKey5 = listOf(Instant.ofEpochSecond(50), ByteBuffer.allocate(2).putShort(5).array())
        val result5 = value.compareUnpackedKey(findKey5, schema)
        val packedKey5 = KeyTool.pack(findKey5, schema)
        val resultPacked5 = packedValue.comparePackedKey(packedKey5, schema)
        assertEquals(-1, result5)
        assertEquals(resultPacked5, result5)

        val findKey6 = listOf(Instant.ofEpochSecond(200), ByteBuffer.allocate(2).putShort(5).array())
        val result6 = value.compareUnpackedKey(findKey6, schema)
        val packedKey6 = KeyTool.pack(findKey6, schema)
        val resultPacked6 = packedValue.comparePackedKey(packedKey6, schema)
        assertEquals(1, result6)
        assertEquals(resultPacked6, result6)
    }
}