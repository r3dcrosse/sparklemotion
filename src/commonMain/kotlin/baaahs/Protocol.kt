package baaahs

interface Ports {
    companion object {
        val MAPPER = 8001
        val PINKY = 8002
        val BRAIN = 8003
    }
}

enum class Type {
    BRAIN_HELLO,
    BRAIN_PANEL_SHADE,
    MAPPER_HELLO,
    BRAIN_ID_REQUEST,
    BRAIN_ID_RESPONSE,
    PINKY_PONG;

    companion object {
        val values = values()
        fun get(i: Byte) = values[i.toInt()]
    }
}

fun parse(bytes: ByteArray): Message {
    val reader = ByteArrayReader(bytes)
    return when (Type.get(reader.readByte())) {
        Type.BRAIN_HELLO -> BrainHelloMessage.parse(reader)
        Type.BRAIN_PANEL_SHADE -> BrainShaderMessage.parse(reader)
        Type.MAPPER_HELLO -> MapperHelloMessage.parse(reader)
        Type.BRAIN_ID_REQUEST -> BrainIdRequest.parse(reader)
        Type.BRAIN_ID_RESPONSE -> BrainIdResponse.parse(reader)
        Type.PINKY_PONG -> PinkyPongMessage.parse(reader)
    }
}

class BrainHelloMessage(val panelName: String) : Message(Type.BRAIN_HELLO) {
    companion object {
        fun parse(reader: ByteArrayReader) = BrainHelloMessage(reader.readString())
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeString(panelName)
    }
}

class BrainShaderMessage(val shader: Shader) : Message(Type.BRAIN_PANEL_SHADE) {
    companion object {
        fun parse(reader: ByteArrayReader): BrainShaderMessage {
            val shader = Shader.parse(reader)
            shader.readBuffer(reader)
            return BrainShaderMessage(shader)
        }
    }

    override fun serialize(writer: ByteArrayWriter) {
        shader.serialize(writer)
        shader.serializeBuffer(writer)
    }
}

class MapperHelloMessage(val isRunning: Boolean) : Message(Type.MAPPER_HELLO) {
    companion object {
        fun parse(reader: ByteArrayReader): MapperHelloMessage {
            return MapperHelloMessage(reader.readBoolean())
        }
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeBoolean(isRunning)
    }
}

class BrainIdRequest(val port: Int) : Message(Type.BRAIN_ID_REQUEST) {
    companion object {
        fun parse(reader: ByteArrayReader) = BrainIdRequest(reader.readInt())
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeInt(port)
    }
}

class BrainIdResponse(val name: String) : Message(Type.BRAIN_ID_RESPONSE) {
    companion object {
        fun parse(reader: ByteArrayReader) = BrainIdResponse(reader.readString())
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeString(name)
    }
}

class PinkyPongMessage(val brainIds: List<String>) : Message(Type.PINKY_PONG) {
    companion object {
        fun parse(reader: ByteArrayReader): PinkyPongMessage {
            val brainCount = reader.readInt();
            val brainIds = mutableListOf<String>()
            for (i in 0 until brainCount) {
                brainIds.add(reader.readString())
            }
            return PinkyPongMessage(brainIds)
        }
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeInt(brainIds.size)
        brainIds.forEach { writer.writeString(it) }
    }
}

open class Message(val type: Type) {
    fun toBytes(): ByteArray {
        val writer = ByteArrayWriter(1 + size())
        writer.writeByte(type.ordinal.toByte())
        serialize(writer)
        return writer.toBytes()
    }

    open fun serialize(writer: ByteArrayWriter) {
    }

    open fun size(): Int = 127
}