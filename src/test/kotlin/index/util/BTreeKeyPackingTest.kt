package index.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/*
* INT, LONG, STRING, BOOLEAN, BYTE, SHORT, FLOAT, DOUBLE, LOCAL_DATE, LOCAL_DATE_TIME, INSTANT, UUID, BYTES
* */
class BTreeKeyPackingTest {
    @Test
    @DisplayName("Given `field(int)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_int`(){
        val value = listOf(10)
        val schema = KeySchema(
            listOf(
                Column("id", ColumnType.INT, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(long)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_long`(){
        val value = listOf(10L)
        val schema = KeySchema(
            listOf(
                Column("id", ColumnType.LONG, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(string)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_string`(){
        val value = listOf("Test Code")
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(boolean)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_boolean`(){
        val value = listOf(true)
        val schema = KeySchema(
            listOf(
                Column("isActive", ColumnType.BOOLEAN, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(byte)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_byte`(){
        val value = listOf(1.toByte())
        val schema = KeySchema(
            listOf(
                Column("byte", ColumnType.BYTE, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(short)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_short`(){
        val value = listOf(10.toShort())
        val schema = KeySchema(
            listOf(
                Column("idShort", ColumnType.SHORT, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(float)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_float`(){
        val value = listOf(10.0f)
        val schema = KeySchema(
            listOf(
                Column("price", ColumnType.FLOAT, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(double)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_double`(){
        val value = listOf(10.0)
        val schema = KeySchema(
            listOf(
                Column("id", ColumnType.DOUBLE, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(local date)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_local_date`(){
        val value = listOf(LocalDate.of(2025, 1, 1))
        val schema = KeySchema(
            listOf(
                Column("date", ColumnType.LOCAL_DATE, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(local date time)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_local_date_time`(){
        val value = listOf(LocalDateTime.of(2025, 1, 1, 12, 0, 0))
        val schema = KeySchema(
            listOf(
                Column("dateTime", ColumnType.LOCAL_DATE_TIME, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(instant)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_instant`(){
        val value = listOf(Instant.ofEpochSecond(100))
        val schema = KeySchema(
            listOf(
                Column("epoch", ColumnType.INSTANT, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(uuid)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_uuid`(){
        val value = listOf(UUID.randomUUID())
        val schema = KeySchema(
            listOf(
                Column("uuid", ColumnType.UUID, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(bytes)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_bytes`(){
        val value: List<Any?> = listOf(ByteBuffer.allocate(2).putShort(10).array())
        val schema = KeySchema(
            listOf(
                Column("bytes", ColumnType.BYTES, descending = false),
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked: List<Any?> = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            val (bytes1, bytes2) = key1 as ByteArray to key2 as ByteArray
            assert(bytes1.contentEquals(bytes2))
        }
    }

    @Test
    @DisplayName("Given `field(int, string, date)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_int_string_date`(){
        val value = listOf(10, "Alice", LocalDate.of(2025, 5, 10))
        val schema = KeySchema(
            listOf(
                Column("id", ColumnType.INT, descending = false),
                Column("name", ColumnType.STRING, descending = false),
                Column("birth", ColumnType.LOCAL_DATE, descending = false)
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }

    @Test
    @DisplayName("Given `field(int, string(collation), date)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_int_string_collation_date`(){
        val value = listOf(10, "Alice", LocalDate.of(2025, 5, 10))
        val collatorInstance = Collator.getInstance(Locale.US)
        val schema = KeySchema(
            listOf(
                Column("id", ColumnType.INT, descending = false),
                Column("name", ColumnType.STRING, descending = false, collation = collatorInstance),
                Column("birth", ColumnType.LOCAL_DATE, descending = false)
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            if (key1 == "Alice"){
                val collatedBytes = collatorInstance.getCollationKey("Alice").toByteArray()
                assertEquals(key2, "[CollationKey(${collatedBytes.joinToString(" ")})]")
            } else {
                assertEquals(key1, key2)
            }

        }
    }

    @Test
    @DisplayName("Given `field(Instant, string, Bytes)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_instant_string_bytes`(){
        val value = listOf(Instant.ofEpochSecond(100), "Alice", ByteBuffer.allocate(2).putShort(10).array())
        val schema = KeySchema(
            listOf(
                Column("epoch", ColumnType.INSTANT, descending = false),
                Column("name", ColumnType.STRING, descending = false),
                Column("bytes", ColumnType.BYTES, descending = false)
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            if(key1 is ByteArray){
                val (bytes1, bytes2) = key1 to key2 as ByteArray
                assert(bytes1.contentEquals(bytes2))
            }else{
                assertEquals(key1, key2)
            }
        }
    }

    @Test
    @DisplayName("Given `field(boolean, float, uuid)`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking_boolean_float_uuid`(){
        val value = listOf(true, 10.0f, UUID.randomUUID())
        val schema = KeySchema(
            listOf(
                Column("isActive", ColumnType.BOOLEAN, descending = false),
                Column("price", ColumnType.FLOAT, descending = false),
                Column("uuid", ColumnType.UUID, descending = false)
            )
        )
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        for ((key1, key2) in value.zip(unpacked)){
            assertEquals(key1, key2)
        }
    }
}