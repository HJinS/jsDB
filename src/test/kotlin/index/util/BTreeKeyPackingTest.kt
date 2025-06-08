package index.util

import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.text.Collator
import java.time.LocalDate
import java.util.*

class BTreeKeyPackingTest {
    private val log = KotlinLogging.logger {}

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
}