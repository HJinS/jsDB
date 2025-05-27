package index.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/*
* page로 넘어가게 되면 page 크기를 기준으로 고정 크기 buffer 사용 할 것
* */
class KeyPacker {
    private fun packKeyItem(key: Any?): ByteArray{
        return when (key){
            null -> byteArrayOf(0x00)
            is Boolean -> byteArrayOf(0x01) + byteArrayOf(if (key) 1 else 0)

            is Byte -> byteArrayOf(0x01) + byteArrayOf(key)

            is Short -> byteArrayOf(0x01) + ByteBuffer.allocate(2).putShort(key).array()
            is Int -> byteArrayOf(0x01) + ByteBuffer.allocate(4).putInt(key).array()
            is Long -> byteArrayOf(0x01) + ByteBuffer.allocate(8).putLong(key).array()

            is Float -> byteArrayOf(0x01) + ByteBuffer.allocate(4).putFloat(key).array()
            is Double -> byteArrayOf(0x01) + ByteBuffer.allocate(8).putDouble(key).array()

            is String -> {
                val bytes = key.toByteArray(StandardCharsets.UTF_8)
                byteArrayOf(0x01) + ByteBuffer.allocate(2).putShort(bytes.size.toShort()).array() + bytes
            }

            is LocalDate -> byteArrayOf(0x01) + ByteBuffer.allocate(4).putInt(key.toEpochDay().toInt()).array()
            is LocalDateTime -> byteArrayOf(0x01) + ByteBuffer.allocate(8).putLong(key.toEpochSecond(ZoneOffset.UTC)).array()
            is Instant -> byteArrayOf(0x01) + ByteBuffer.allocate(8).putLong(key.epochSecond).array()

            is UUID -> byteArrayOf(0x01) + ByteBuffer.allocate(16)
                .putLong(key.mostSignificantBits)
                .putLong(key.leastSignificantBits)
                .array()
            is ByteArray -> byteArrayOf(0x01) + ByteBuffer.allocate(4).putInt(key.size).array() + key
            else -> throw java.lang.IllegalArgumentException("Unsupported type: ${key::class.simpleName}")
        }
    }
    fun pack(keyList: List<Any?>): ByteArray{
        val buffer = ByteArrayOutputStream()
        for (key in keyList){
            val packed = packKeyItem(key)
            buffer.write(packed)
        }
        return buffer.toByteArray()
    }
}