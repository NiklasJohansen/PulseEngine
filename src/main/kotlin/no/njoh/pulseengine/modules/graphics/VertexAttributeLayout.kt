package no.njoh.pulseengine.modules.graphics

import org.lwjgl.opengl.GL11.*
import java.lang.IllegalArgumentException

class VertexAttributeLayout
{
    val attributes = mutableListOf<Attribute>()
    var strideInBytes = 0L

    fun withAttribute(name: String, count: Int, type: Int, divisor: Int = 0, normalized: Boolean = false): VertexAttributeLayout =
        this.also {
            val size = count * sizeOf(type)
            strideInBytes += size
            attributes.add(Attribute(name, count, type, size, divisor, normalized))
        }

    private fun sizeOf(glType: Int): Int = when (glType)
    {
        GL_FLOAT -> 4
        GL_INT -> 4
        GL_UNSIGNED_INT -> 4
        GL_UNSIGNED_BYTE -> 1
        else -> throw IllegalArgumentException("Type $glType not supported")
    }

    data class Attribute(
        val name: String,
        val count: Int,
        val type: Int,
        val bytes: Int,
        val divisor: Int,
        val normalized: Boolean
    )
}