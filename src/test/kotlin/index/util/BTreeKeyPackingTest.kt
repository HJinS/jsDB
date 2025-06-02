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
    private val schema = KeySchema(
        listOf(
            Column("id", ColumnType.INT, descending = false),
            Column("name", ColumnType.STRING, descending = false, collation = Collator.getInstance(Locale.US)),
            Column("birth", ColumnType.LOCAL_DATE, descending = false)
        )
    )

    @Test
    @DisplayName("Given `field`, when do pack and unpack, Then result will be same")
    fun `테스트_packing_unpacking`(){
        val value = listOf(10, "Alice", LocalDate.of(2025, 5, 10))
        val packed = KeyTool.pack(value, schema)
        val unpacked = KeyTool.unpack(packed, schema)
        log.info { "Packed: $packed Unpacked: $unpacked" }
        for ((key1, key2) in value.zip(unpacked)){
            log.info { "Original: $key1 Unpacked: $key2" }
            assertEquals(key1, key2)
        }
    }
}