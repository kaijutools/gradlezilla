package tools.kaiju.gradlezilla.inspector.versioncatalog

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VersionSerializer::class)
data class Version(
    val ref: String? = null,
    val literal: String? = null,
)

object VersionSerializer : KSerializer<Version> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Version") {
            element<String>(elementName = "ref", isOptional = true)
            element<String>(elementName = "literal", isOptional = true)
            element<String>(elementName = "strictly", isOptional = true)
            element<String>(elementName = "require", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): Version =
        try {
            val stringValue = decoder.decodeString()
            Version(literal = stringValue)
        } catch (e: Exception) {
            val composite = decoder.beginStructure(descriptor)
            var ref: String? = null
            var literal: String? = null

            while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> {
                        break
                    }

                    0 -> {
                        ref = composite.decodeStringElement(descriptor, 0)
                    }

                    1 -> {
                        literal = composite.decodeStringElement(descriptor, 1)
                    }

                    2 -> {
                        literal = composite.decodeStringElement(descriptor, 2)
                    }

                    3 -> {
                        literal = composite.decodeStringElement(descriptor, 3)
                    }

                    else -> {
                        // ignore unknown keys
                    }
                }
            }
            composite.endStructure(descriptor)
            Version(ref = ref, literal = literal)
        }

    override fun serialize(
        encoder: Encoder,
        value: Version,
    ) {
        when {
            value.ref != null -> {
                val composite = encoder.beginStructure(descriptor)
                composite.encodeStringElement(descriptor, 0, value.ref)
                composite.endStructure(descriptor)
            }

            value.literal != null -> {
                encoder.encodeString(value.literal)
            }

            else -> {
                encoder.encodeString("")
            }
        }
    }
}
