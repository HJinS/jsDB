package index.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate


object LocalDateSerializer: KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeLong(value.toEpochDay())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.ofEpochDay(decoder.decodeLong())
    }
}