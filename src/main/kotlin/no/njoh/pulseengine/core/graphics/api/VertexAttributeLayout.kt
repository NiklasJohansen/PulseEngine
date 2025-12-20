package no.njoh.pulseengine.core.graphics.api

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glVertexAttribIPointer
import org.lwjgl.opengl.GL33.glVertexAttribDivisor
import java.lang.IllegalArgumentException

class VertexAttributeLayout
{
    val attributes = mutableListOf<Attribute>()
    var strideInBytes = 0L

    fun withAttribute(name: String, count: Int, type: Int, divisor: Int = 0, normalized: Boolean = false): VertexAttributeLayout
    {
        val size = count * sizeOf(type)
        strideInBytes += size
        attributes.add(Attribute(name, count, type, size, divisor, normalized))
        return this
    }

    fun bind(program: ShaderProgram? = null, instanceOffset: Int = 0)
    {
        var index = 0
        var byteOffset = strideInBytes * instanceOffset
        for (attr in attributes) {
            val location = program?.attributeLocationOf(attr.name) ?: index
            glEnableVertexAttribArray(location)
            when (attr.type) {
                GL_INT,
                GL_UNSIGNED_INT -> glVertexAttribIPointer(location, attr.count, attr.type, strideInBytes.toInt(), byteOffset)
                GL_FLOAT        -> glVertexAttribPointer(location, attr.count, attr.type, attr.normalized, strideInBytes.toInt(), byteOffset)
            }
            glVertexAttribDivisor(location, attr.divisor)
            byteOffset += attr.bytes
            index++
        }
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

private fun sizeOf(glType: Int): Int = when (glType)
{
    GL_FLOAT -> 4
    GL_INT -> 4
    GL_UNSIGNED_INT -> 4
    GL_UNSIGNED_BYTE -> 1
    else -> throw IllegalArgumentException("Type $glType not supported")
}