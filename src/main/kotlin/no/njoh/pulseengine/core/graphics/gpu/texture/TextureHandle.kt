package no.njoh.pulseengine.core.graphics.gpu.texture

/**
 * A validated reference to either a layer in a texture array or a standalone OpenGL texture.
 *
 * Array handles contain the texture-array slot and layer. Their lower 32 bits use the same packed format expected by shaders.
 * OpenGL handles contain a type marker and preserve the full 32-bit OpenGL texture ID.
 */
@JvmInline
value class TextureHandle private constructor(private val handle: Long)
{
    val textureArraySlot  get() = (handle ushr TEXTURE_ARRAY_SLOT_SHIFT).toShort().toInt()
    val textureArrayLayer get() = (handle ushr TEXTURE_ARRAY_LAYER_SHIFT).toShort().toInt()
    val glId              get() = handle.toInt()

    val isArrayTexture get() = handle >= 0L
    val isGlTexture    get() = handle ushr PAYLOAD_BITS == GL_TEXTURE_TYPE
    val isNone         get() = handle == NONE.handle
    val isInvalid      get() = handle == INVALID.handle

    fun toFloat() = Float.fromBits(handle.toInt())
    fun toLong() = handle

    companion object
    {
        fun createArrayHandle(textureArraySlot: Int, textureArrayLayer: Int): TextureHandle
        {
            require(textureArraySlot in 0..MAX_TEXTURE_ARRAY_SLOT) { "Texture array slot must be in the range 0..$MAX_TEXTURE_ARRAY_SLOT: $textureArraySlot" }
            require(textureArrayLayer in 0..MAX_TEXTURE_ARRAY_LAYER) { "Texture array layer must be in the range 0..$MAX_TEXTURE_ARRAY_LAYER: $textureArrayLayer" }
            val payload = (textureArraySlot.toLong() shl PACKED_TEXTURE_ARRAY_SLOT_SHIFT) or textureArrayLayer.toLong()
            return TextureHandle((payload shl PAYLOAD_BITS) or payload)
        }

        fun createGlHandle(glId: Int) = TextureHandle((GL_TEXTURE_TYPE shl PAYLOAD_BITS) or (glId.toLong() and PAYLOAD_MASK))

        internal fun fromLong(handle: Long) = TextureHandle(handle)

        val NONE    = TextureHandle((NONE_TYPE shl PAYLOAD_BITS) or NO_TEXTURE_SHADER_PAYLOAD)
        val INVALID = TextureHandle((INVALID_TYPE shl PAYLOAD_BITS) or NO_TEXTURE_SHADER_PAYLOAD)

        private const val PAYLOAD_BITS                    = 32
        private const val TEXTURE_ARRAY_SLOT_SHIFT        = 48
        private const val TEXTURE_ARRAY_LAYER_SHIFT       = 32
        private const val PACKED_TEXTURE_ARRAY_SLOT_SHIFT = 16
        private const val MAX_TEXTURE_ARRAY_SLOT          = 32_767
        private const val MAX_TEXTURE_ARRAY_LAYER         = 32_767
        private const val PAYLOAD_MASK                    = 0xFFFF_FFFFL
        private const val NO_TEXTURE_SHADER_PAYLOAD       = 0x0000_FFFEL
        private const val GL_TEXTURE_TYPE                 = 0x8000_0000L
        private const val INVALID_TYPE                    = 0xFFFE_FFFFL
        private const val NONE_TYPE                       = 0xFFFF_FFFFL
    }
}