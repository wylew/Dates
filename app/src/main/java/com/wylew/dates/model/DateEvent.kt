package com.wylew.dates.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class DateEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    @Serializable(with = LocalDateTimeSerializer::class)
    val dateTime: LocalDateTime,
    @Serializable(with = LocalDateTimeNullableSerializer::class)
    val endDateTime: LocalDateTime? = null,
    val isAllDay: Boolean = false,
    val useEmojiPattern: Boolean = false,
    val patternEmojis: List<String> = emptyList(),
    val recurrence: Recurrence = Recurrence.None,
    val imageUri: String? = null,
    val emoji: String? = null,
    @Serializable(with = ColorSerializer::class)
    val customColor: Color? = null,
    @Serializable(with = ColorSerializer::class)
    val autoColor: Color? = null
)

@Serializable
sealed class Recurrence {
    @Serializable
    object None : Recurrence()
    @Serializable
    object Daily : Recurrence()
    @Serializable
    object Weekly : Recurrence()
    @Serializable
    object Monthly : Recurrence()
    @Serializable
    object Yearly : Recurrence()
    @Serializable
    data class Custom(
        val frequency: Frequency,
        val interval: Int = 1,
        val dayOfWeek: Int? = null, // 1-7 (Monday-Sunday)
        val dayOfMonth: Int? = null, // 1-31
        val weekOfMonth: Int? = null, // 1-5
        val monthOfYear: Int? = null // 1-12
    ) : Recurrence()
}

enum class Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString(), formatter)
    }
}

object LocalDateTimeNullableSerializer : KSerializer<LocalDateTime?> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTimeNullable", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime?) {
        if (value == null) {
            encoder.encodeString("")
        } else {
            encoder.encodeString(value.format(formatter))
        }
    }

    override fun deserialize(decoder: Decoder): LocalDateTime? {
        val str = decoder.decodeString()
        return if (str.isEmpty()) null else LocalDateTime.parse(str, formatter)
    }
}

object ColorSerializer : KSerializer<Color?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Color?) {
        if (value == null) {
            encoder.encodeInt(-1)
        } else {
            encoder.encodeInt(value.toArgb())
        }
    }

    override fun deserialize(decoder: Decoder): Color? {
        val argb = decoder.decodeInt()
        return if (argb == -1) null else Color(argb)
    }
}
