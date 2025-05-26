package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Shader
import no.njoh.pulseengine.core.graphics.api.TextureFilter.*
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.emptyObjectIntHashMap
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.getOrPut
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL33.glVertexAttribDivisor

class ShaderProgram(
    id: Int,
    private val shaders: List<Shader>,
) {
    /** Locally mutable program ID */
    var id = id; private set

    /** Cache of uniform locations */
    private var uniformLocations = emptyObjectIntHashMap<String>(16) // Uniform name -> location

    /** Used for setting texture sampler bindings */
    private val textureUnits = emptyObjectIntHashMap<String>(32) // Sampler name -> texture unit

    /** Hash of the last time the shaders were compiled */
    private var shaderCompileHash = -1L

    fun bind()
    {
        linkProgramIfNecessary()
        glUseProgram(id)
    }

    fun unbind() = glUseProgram(0)

    fun delete()
    {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    fun attributeLocationOf(name: String): Int =
        glGetAttribLocation(id, name)

    fun uniformLocationOf(name: String): Int =
        uniformLocations.getOrPut(name) { getUniformLocation(name) }

    fun setUniform(name: String, vec3: Vector3f) =
        glUniform3f(uniformLocationOf(name), vec3[0], vec3[1], vec3[2])

    fun setUniform(name: String, vec4: Vector4f) =
        glUniform4f(uniformLocationOf(name), vec4[0], vec4[1], vec4[2], vec4[3])

    fun setUniform(name: String, matrix: Matrix4f) =
        glUniformMatrix4fv(uniformLocationOf(name), false, matrix.get(floatArray16))

    fun setUniform(name: String, value: Boolean) =
        glUniform1i(uniformLocationOf(name), if (value) 1 else 0)

    fun setUniform(name: String, value: Int) =
        glUniform1i(uniformLocationOf(name), value)

    fun setUniform(name: String, value1: Int, value2: Int) =
        glUniform2i(uniformLocationOf(name), value1, value2)

    fun setUniform(name: String, value1: Int, value2: Int, value3: Int) =
        glUniform3i(uniformLocationOf(name), value1, value2, value3)

    fun setUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
        glUniform4i(uniformLocationOf(name), value1, value2, value3, value4)

    fun setUniform(name: String, value: Float) =
        glUniform1f(uniformLocationOf(name), value)

    fun setUniform(name: String, value1: Float, value2: Float) =
        glUniform2f(uniformLocationOf(name), value1, value2)

    fun setUniform(name: String, value1: Float, value2: Float, value3: Float) =
        glUniform3f(uniformLocationOf(name), value1, value2, value3)

    fun setUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
        glUniform4f(uniformLocationOf(name), value1, value2, value3, value4)

    fun setUniform(name: String, color: Color, convertFromSRgbToLinear: Boolean = true)
    {
        val c = if (convertFromSRgbToLinear) color.asLinear() else color
        glUniform4f(uniformLocationOf(name), c.red, c.green, c.blue, c.alpha)
    }

    fun setVertexAttributeLayout(name: String, count: Int, type: Int, stride: Int, offset: Long, divisor: Int = 0, normalized: Boolean = false)
    {
        val location = attributeLocationOf(name)
        glEnableVertexAttribArray(location)
        glVertexAttribPointer(location, count, type, normalized, stride, offset)
        glVertexAttribDivisor(location, divisor)
    }

    fun setVertexAttributeLayout(layout: VertexAttributeLayout)
    {
        var offset = 0L
        layout.attributes.forEachFast { attribute ->
            setVertexAttributeLayout(
                name = attribute.name,
                count = attribute.count,
                type = attribute.type,
                stride = layout.strideInBytes.toInt(),
                offset = offset,
                divisor = attribute.divisor,
                normalized = attribute.normalized
            )
            offset += attribute.bytes
        }
    }

    fun setUniformSampler(samplerName: String, texture: RenderTexture, filter: TextureFilter = texture.filter, wrapping: TextureWrapping = texture.wrapping) =
        setUniformSampler(samplerName, texture.handle, filter, wrapping)

    fun setUniformSampler(samplerName: String, textureHandle: TextureHandle, filter: TextureFilter = LINEAR, wrapping: TextureWrapping = CLAMP_TO_EDGE)
    {
        val unit = textureUnits.getOrPut(samplerName) { textureUnits.size() }
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureHandle.textureIndex)
        setUniform(samplerName, unit)
        TextureSampler.getFor(filter, wrapping).bind(unit)
    }

    fun setUniformSamplerArrays(textureArrays: List<TextureArray>, filter: TextureFilter? = null, wrapping: TextureWrapping? = null) =
        textureArrays.forEachFast { setUniformSamplerArray(it, filter ?: it.filter, wrapping ?: it.wrapping) }

    fun setUniformSamplerArray(textureArray: TextureArray, filter: TextureFilter = textureArray.filter, wrapping: TextureWrapping = textureArray.wrapping)
    {
        val unit = textureArray.samplerIndex
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray.id)
        setUniform(textureArrayNames[unit], unit)
        TextureSampler.getFor(filter, wrapping).bind(unit)
    }

    fun setUniformSamplerArray(samplerName: String, textureArray: TextureArray, filter: TextureFilter = textureArray.filter, wrapping: TextureWrapping = textureArray.wrapping)
    {
        val unit = textureUnits.getOrPut(samplerName) { textureUnits.size() }
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray.id)
        setUniform(samplerName, unit)
        TextureSampler.getFor(filter, wrapping).bind(unit)
    }

    fun assignUniformBlockBinding(blockName: String, blockBinding: Int): Int
    {
        val index = glGetUniformBlockIndex(id, blockName)
        glUniformBlockBinding(id, index, blockBinding)
        return index
    }

    private fun getUniformLocation(name: String): Int =
        glGetUniformLocation(id, name).also()
        {
            if (it == -1) Logger.warn { "Uniform '$name' not found in shader program #$id (${shaders.joinToString { it.filePath }})" }
        }

    private fun linkProgramIfNecessary()
    {
        var hash = 1L
        shaders.forEachFast { hash = hash * 31 + it.compileTimestamp }
        if (hash == shaderCompileHash)
            return // No need to relink program if the shaders have not been recompiled

        Logger.debug { "Linking program #$id (shaders: ${shaders.joinToString { "#${it.getId()}" }})" }

        // Detach all shaders in case recompiled shaders have gotten new IDs
        glGetAttachedShaders(id, shaderCount, shaderIds)
        for (i in 0 until shaderCount[0])
            glDetachShader(id, shaderIds[i])

        // Reattach all shaders again
        shaders.forEachFast { glAttachShader(id, it.getId()) }

        // Link and verify
        glLinkProgram(id)
        if (glGetProgrami(id, GL_LINK_STATUS) != GL_TRUE)
            throw RuntimeException("Failed to link shaders: ${shaders.joinToString { it.filePath }} \n${glGetProgramInfoLog(id)}")

        uniformLocations.clear()
        textureUnits.clear()
        shaderCompileHash = hash
    }

    companion object
    {
        private val shaderIds = IntArray(5)
        private val shaderCount = IntArray(1)
        private val floatArray16 = FloatArray(16)
        private val textureArrayNames = Array(64) { "textureArrays[$it]" }

        fun create(vararg shaders: Shader) = ShaderProgram(glCreateProgram(), shaders.toList())
    }
}