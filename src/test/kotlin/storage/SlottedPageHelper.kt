package storage

import index.serializer.KeySerializer
import index.serializer.ValueSerializer
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import storage.SlottedPageTest.Companion.pageSize
import storageEngine.page.SlottedPage
import storageEngine.page.SlottedPage.Companion.HEADER_SIZE
import storageEngine.page.SlottedPage.Companion.SLOT_SIZE
import java.util.Arrays

fun SlottedPage.checkInvariant(){
    HEADER_SIZE shouldBeLessThanOrEqual freeSpaceStart
    freeSpaceStart shouldBeLessThanOrEqual freeSpaceEnd
    freeSpaceEnd shouldBeLessThanOrEqual pageSize - 1
    recordCount shouldBeEqual (freeSpaceStart - HEADER_SIZE) / SLOT_SIZE
}

fun SlottedPage.getInsertPosition(key: ByteArray): Int{
    val slotId = binarySearch(key)
    return if(slotId >= 0) slotId+1 else -(slotId + 1)
}

fun <K, V> SlottedPage.insertTyped(
    key: K,
    value: V,
    keySerializer: KeySerializer<K>,
    valueSerializer: ValueSerializer<V>
): Int{
    val serializedKey = keySerializer.serialize(key)
    val serializedValue = valueSerializer.serialize(value)
    val slotId = getInsertPosition(serializedKey)
    return insertData(slotId, serializedKey, serializedValue)
}

fun <V> SlottedPage.updateValueTyped(
    slotId: Int,
    value: V,
    valueSerializer: ValueSerializer<V>
): Int{
    val key = getData(slotId).first
    val serializedValue = valueSerializer.serialize(value)
    return updateData(slotId, key, serializedValue)
}


fun SlottedPage.checkSorted() {
    val keys = mutableListOf<ByteArray>()
    val startSlotId = 0
    val endSlotId = recordCount - 1

    for(slot in startSlotId..endSlotId){
        val (key, _) = getData(slot)
        keys.add(key)
    }
    keys.toList().shouldBeSortedWith { bytes1, bytes2 ->  Arrays.compareUnsigned(bytes1, bytes2)}
}

fun <K, V> serialize(
    key: K,
    value: V,
    keySerializer: KeySerializer<K>,
    valueSerializer: ValueSerializer<V>
): Pair<ByteArray, ByteArray>{
    return serializeKey(key, keySerializer) to serializeValue(value, valueSerializer)
}

fun <K> serializeKey(
    key: K,
    keySerializer: KeySerializer<K>,
): ByteArray{
    val serializedValue = keySerializer.serialize(key)
    return serializedValue
}

fun <V> serializeValue(
    value: V,
    valueSerializer: ValueSerializer<V>
): ByteArray{
    val serializedValue = valueSerializer.serialize(value)
    return serializedValue
}
