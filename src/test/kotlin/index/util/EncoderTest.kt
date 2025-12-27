package index.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer

class EncoderTest: BehaviorSpec({
    var originalInteger = 12309
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
    given("a string $originalString encoded with encodeSortable"){
        val encodedString = originalString.encodeSortable(null)
        `when`("decode the string"){
            val decodedString = encodedString.decodeSortableString(null)
            then("the decoded string $decodedString should be equal with original one"){
                decodedString shouldBe originalString
            }
        }

    }
})