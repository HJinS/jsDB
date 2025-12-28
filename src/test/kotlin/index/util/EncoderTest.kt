package index.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.text.Collator
import java.util.Locale


class EncoderTest: BehaviorSpec({
    var originalInteger = Integer.MAX_VALUE
    given("an integer $originalInteger encoded with encodeVarInt"){
        val encodedNumber = encodeVarInt(originalInteger)
        `when`("decode the integer"){
            val (decodedInteger, length) = decodeVarInt(encodedNumber)
            then("the decodedInteger $decodedInteger should be same as originalInteger $originalInteger.") {
                originalInteger shouldBe decodedInteger
            }
        }
    }

    originalInteger = Integer.MIN_VALUE
    given("an integer $originalInteger encoded with encodeVarInt"){
        val encodedNumber = encodeVarInt(originalInteger)
        `when`("decode the integer"){
            val (decodedInteger, length) = decodeVarInt(encodedNumber)
            then("the decodedInteger $decodedInteger should be same as originalInteger $originalInteger.") {
                originalInteger shouldBe decodedInteger
            }
        }
    }

    originalInteger = 1
    given("an integer $originalInteger encoded with encodeVarInt"){
        val encodedNumber = encodeVarInt(originalInteger)
        `when`("decode the integer"){
            val (decodedInteger, length) = decodeVarInt(encodedNumber)
            then("the decodedInteger $decodedInteger should be same as originalInteger $originalInteger.") {
                originalInteger shouldBe decodedInteger
            }
        }
    }

    originalInteger = 40
    given("an integer $originalInteger encoded with encodeVarInt"){
        val encodedNumber = encodeVarInt(originalInteger)
        `when`("decode the integer"){
            val (decodedInteger, _) = decodeVarInt(encodedNumber)
            then("the decodedInteger $decodedInteger should be same as originalInteger $originalInteger.") {
                originalInteger shouldBe decodedInteger
            }
        }
    }

    originalInteger = 50
    given("an integer $originalInteger encoded with encodeVarInt"){
        val encodedNumber = encodeVarInt(originalInteger)
        val buffer = ByteBuffer.allocate(10)
        buffer.put(encodedNumber, 0, encodedNumber.size).array()
        buffer.putInt(encodedNumber.size+1, 10)
        val byteWithEncodedNumber = buffer.array()
        `when`("decode the integer"){
            val (decodedInteger, _) = decodeVarInt(byteWithEncodedNumber)
            then("the decodedInteger $decodedInteger should be same as originalInteger $originalInteger.") {
                originalInteger shouldBe decodedInteger
            }
        }
    }

    var originalString = "fapwoq\\!@#ㄴㅇㄹ)ihesdfj"
    val collatorInstance = Collator.getInstance(Locale.US)
    given("a string $originalString"){
        val encodedString = originalString.encodeSortable(null)
        `when`("encode with no collator and decode the string"){
            val decodedString = encodedString.decodeSortableString(null)
            then("the decoded string $decodedString should be equal with $originalString") {
                decodedString shouldBe originalString
            }
        }
        val encodedStringCollated = originalString.encodeSortable(collatorInstance)
        val originalStringCollated = "[CollationKey(${
            collatorInstance
                .getCollationKey(originalString)
                .toByteArray()
                .joinToString("") { "%02x".format(it) }
        })]"
        `when`("encode with collator and decode the string"){
            val decodedString = encodedStringCollated.decodeSortableString(collatorInstance)
            then("the decoded string $decodedString should be equal with $originalStringCollated"){
                decodedString shouldBe originalStringCollated
            }
        }
    }

    originalString = "f apwoq \\!@#ㄴㅇㄹ)ihesdfj"
    given("a string $originalString encoded with encodeSortable"){
        val encodedString = originalString.encodeSortable(null)
        `when`("encode with no collator and decode the string"){
            val decodedString = encodedString.decodeSortableString(null)
            then("the decoded string $decodedString should be equal with $originalString"){
                decodedString shouldBe originalString
            }
        }
        val encodedStringCollated = originalString.encodeSortable(collatorInstance)
        val originalStringCollated = "[CollationKey(${
            collatorInstance
                .getCollationKey(originalString)
                .toByteArray()
                .joinToString("") { "%02x".format(it) }
        })]"
        `when`("encode with collator and decode the string"){
            val decodedString = encodedStringCollated.decodeSortableString(collatorInstance)
            then("the decoded string $decodedString should be equal with $originalStringCollated"){
                decodedString shouldBe originalStringCollated
            }
        }
    }

    var originalFloat = 0.2134325f
    given("a float $originalFloat encoded with encodeSortable"){
        val encodedFloat = originalFloat.encodeSortable()
        `when`("decode the float"){
            val decodedFloat = encodedFloat.decodeSortableFloat()
            then("the decoded float $decodedFloat should be equal with $originalFloat"){
                decodedFloat shouldBe originalFloat
            }
        }
    }

    var originalDouble = 0.2134325321478
    given("a double $originalDouble encoded with encodeSortable"){
        val encodedDouble = originalDouble.encodeSortable()
        `when`("decode the double"){
            val decodedDouble = encodedDouble.decodeSortableDouble()
            then("the decoded double $decodedDouble should be equal with $originalDouble"){
                decodedDouble shouldBe originalDouble
            }
        }
    }

    var originalShort: Short = Short.MAX_VALUE
    given("a short $originalShort encoded with encodeSortable"){
        val encodedShort = originalShort.encodeSortable()
        `when`("decode the short"){
            val decodedShort = encodedShort.decodeSortableShort()
            then("the decoded short $decodedShort should be equal with $originalShort"){
                decodedShort shouldBe originalShort
            }
        }
    }

    originalShort = Short.MIN_VALUE
    given("a short $originalShort encoded with encodeSortable"){
        val encodedShort = originalShort.encodeSortable()
        `when`("decode the short"){
            val decodedShort = encodedShort.decodeSortableShort()
            then("the decoded short $decodedShort should be equal with $originalShort"){
                decodedShort shouldBe originalShort
            }
        }
    }

    var originalLong: Long = Long.MAX_VALUE
    given("a long $originalLong encoded with encodeSortable"){
        val encodedLong = originalLong.encodeSortable()
        `when`("decode the long"){
            val decodedLong = encodedLong.decodeSortableLong()
            then("the decoded long $decodedLong should be equal with $originalLong"){
                decodedLong shouldBe originalLong
            }
        }
    }

    originalLong = Long.MIN_VALUE
    given("a long $originalLong encoded with encodeSortable"){
        val encodedLong = originalLong.encodeSortable()
        `when`("decode the long"){
            val decodedLong = encodedLong.decodeSortableLong()
            then("the decoded long $decodedLong should be equal with $originalLong"){
                decodedLong shouldBe originalLong
            }
        }
    }
})