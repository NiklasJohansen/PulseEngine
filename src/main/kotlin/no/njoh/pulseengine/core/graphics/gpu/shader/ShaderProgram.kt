package no.njoh.pulseengine.core.graphics.gpu.shader

import no.njoh.pulseengine.core.asset.types.Shader
import no.njoh.pulseengine.core.graphics.gpu.buffer.ShaderStorageBufferObject
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.RenderTexture
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy.Companion.defaultFor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.*
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureArray
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureCompare
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureSampler
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.emptyObjectIntHashMap
import no.njoh.pulseengine.core.shared.utils.getOrPut
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.ARBUniformBufferObject.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL32.GL_TEXTURE_2D_MULTISAMPLE
import org.lwjgl.opengl.GL30.glUniform1ui
import org.lwjgl.opengl.GL43.GL_BUFFER_BINDING
import org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BLOCK
import org.lwjgl.opengl.GL43.glGetProgramResourceIndex
import org.lwjgl.opengl.GL43.glGetProgramResourceiv
import java.nio.FloatBuffer

class ShaderProgram(
    id: Int,
    private val shaders: List<Shader>,
) {
    /** Locally mutable program ID */
    var id = id; private set

    /** Cache of uniform locations */
    private var uniformLocations = emptyObjectIntHashMap<String>(16) // Uniform name -> location

    /** Cache of attribute locations */
    private var attributeLocations = emptyObjectIntHashMap<String>(16) // Attribute name -> location

    /** Cache of shader storage block bindings declared by the linked shaders */
    private var shaderStorageBufferBindings = emptyObjectIntHashMap<String>(16) // Block name -> binding

    /** Missing required storage blocks already reported for the current linked shaders */
    private val reportedMissingShaderStorageBlocks = HashSet<String>(8)

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

    fun destroy()
    {
        glUseProgram(0)
        glDeleteProgram(id)
    }

    fun attributeLocationOf(name: String): Int =
        attributeLocations.getOrPut(name) { glGetAttribLocation(id, name) }

    fun uniformLocationOf(name: String): Int =
        uniformLocations.getOrPut(name) { getUniformLocation(name) }

    fun setUniform(name: String, vec2: Vector2f) =
        glUniform2f(uniformLocationOf(name), vec2.x, vec2.y)

    fun setUniform(name: String, vec3: Vector3f) =
        glUniform3f(uniformLocationOf(name), vec3.x, vec3.y, vec3.z)

    fun setUniform(name: String, vec4: Vector4f) =
        glUniform4f(uniformLocationOf(name), vec4.x, vec4.y, vec4.z, vec4.w)

    fun setUniform(name: String, matrix: Matrix4f) =
        glUniformMatrix4fv(uniformLocationOf(name), false, matrix.get(matrixFloatArray))

    fun setUniform(name: String, matrices: Array<Matrix4f>) 
    {
        val count = matrices.size 
        if (count >= matrixFloatArrays.size) 
             matrixFloatArrays = matrixFloatArrays.copyOf(count + 1)
        var data = matrixFloatArrays[count]
        if (data == null) 
            data = FloatArray(count * 16).also { matrixFloatArrays[count] = it } 
        for (i in matrices.indices)
            matrices[i].get(data, i * 16) 
        glUniformMatrix4fv(uniformLocationOf(name), false, data)
    }
    
    fun setUniform(name: String, value: Boolean) =
        glUniform1i(uniformLocationOf(name), if (value) 1 else 0)

    fun setUniform(name: String, value: Int) =
        glUniform1i(uniformLocationOf(name), value)

    fun setUniform(name: String, value: UInt) =
        glUniform1ui(uniformLocationOf(name), value.toInt())

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

    fun setUniformVec4Array(name: String, buffer: FloatBuffer) =
        glUniform4fv(uniformLocationOf(name), buffer)

    fun setUniform(name: String, color: Color, convertFromSRgbToLinear: Boolean = true)
    {
        val c = if (convertFromSRgbToLinear) color.asLinear() else color
        glUniform4f(uniformLocationOf(name), c.red, c.green, c.blue, c.alpha)
    }

    fun setUniformSampler(
        samplerName: String,
        texture: RenderTexture,
        filter: TextureFilter = texture.filter,
        anisotropy: TextureAnisotropy = defaultFor(filter),
        wrapping: TextureWrapping = texture.wrapping,
        compare: TextureCompare = TextureCompare.NONE,
        borderColor: Color? = null
    ) = setUniformSampler(samplerName, texture.handle, filter, anisotropy, wrapping, compare, borderColor, texture.multisampling)

    fun setUniformSampler(
        samplerName: String,
        textureHandle: TextureHandle,
        filter: TextureFilter = LINEAR,
        anisotropy: TextureAnisotropy = defaultFor(filter),
        wrapping: TextureWrapping = CLAMP_TO_EDGE,
        compare: TextureCompare = TextureCompare.NONE,
        borderColor: Color? = null,
        multisampling: Multisampling = Multisampling.NONE,
    ) {
        val unit = assignSamplerUnit(samplerName)
        val target = if (multisampling == Multisampling.NONE) GL_TEXTURE_2D else GL_TEXTURE_2D_MULTISAMPLE
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(target, textureHandle.glId)
        TextureSampler.getFor(filter, anisotropy, wrapping, compare, borderColor).bind(unit)
    }

    fun setUniformSamplerArrays(textureArrays: List<TextureArray>, filter: TextureFilter? = null, anisotropy: TextureAnisotropy? = null, wrapping: TextureWrapping? = null) =
        textureArrays.forEachFast { setUniformSamplerArray(it, filter ?: it.filter, anisotropy ?: it.anisotropy, wrapping ?: it.wrapping) }

    fun setUniformSamplerArray(
        textureArray: TextureArray,
        filter: TextureFilter = textureArray.filter,
        anisotropy: TextureAnisotropy = textureArray.anisotropy,
        wrapping: TextureWrapping = textureArray.wrapping,
        compare: TextureCompare = TextureCompare.NONE,
        borderColor: Color? = null,
    ) {
        val samplerName = textureBankNames[textureArray.textureArraySlot]
        val unit = assignSamplerUnit(samplerName)
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray.id)
        TextureSampler.getFor(filter, anisotropy, wrapping, compare, borderColor).bind(unit)
    }

    fun setUniformSamplerArray(
        samplerName: String,
        textureArray: TextureArray,
        filter: TextureFilter = textureArray.filter,
        anisotropy: TextureAnisotropy = textureArray.anisotropy,
        wrapping: TextureWrapping = textureArray.wrapping,
        compare: TextureCompare = TextureCompare.NONE,
        borderColor: Color? = null,
    ) {
        val unit = assignSamplerUnit(samplerName)
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray.id)
        TextureSampler.getFor(filter, anisotropy, wrapping, compare, borderColor).bind(unit)
    }

    fun assignSamplerUnit(samplerName: String): Int
    {
        val unit = textureUnits.getOrPut(samplerName) { textureUnits.size() }
        setUniform(samplerName, unit)
        return unit
    }

    fun assignUniformBlockBinding(blockName: String, blockBinding: Int): Int
    {
        val index = glGetUniformBlockIndex(id, blockName)
        glUniformBlockBinding(id, index, blockBinding)
        return index
    }

    fun bindStorageBuffer(blockName: String, buffer: ShaderStorageBufferObject?)
    {
        if (buffer == null) return

        val binding = shaderStorageBufferBindingOf(blockName)
        if (binding != MISSING_SHADER_STORAGE_BLOCK)
        {
            buffer.bindStorageBuffer(binding)
        }
        else if (reportedMissingShaderStorageBlocks.add(blockName))
        {
            Logger.error { "Shader storage block '$blockName' not found in shader program #$id (${shaders.joinToString { it.filePath }})" }
        }
    }

    internal fun shaderStorageBufferBindingOf(blockName: String): Int =
        shaderStorageBufferBindings.getOrPut(blockName)
        {
            val blockIndex = glGetProgramResourceIndex(id, GL_SHADER_STORAGE_BLOCK, blockName)
            if (blockIndex != MISSING_SHADER_STORAGE_BLOCK)
            {
                glGetProgramResourceiv(id, GL_SHADER_STORAGE_BLOCK, blockIndex, shaderStorageBufferBindingProperty, null, shaderStorageBufferBindingValue)
                shaderStorageBufferBindingValue[0]
            }
            else MISSING_SHADER_STORAGE_BLOCK
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
        attributeLocations.clear()
        shaderStorageBufferBindings.clear()
        reportedMissingShaderStorageBlocks.clear()
        textureUnits.clear()
        shaderCompileHash = hash
    }

    companion object
    {
        private val shaderIds = IntArray(5)
        private val shaderCount = IntArray(1)
        private val matrixFloatArray = FloatArray(16)
        private var matrixFloatArrays = Array<FloatArray?>(0) { null }
        private val textureBankNames = Array(64) { "uTextureBanks[$it]" }
        private val shaderStorageBufferBindingProperty = intArrayOf(GL_BUFFER_BINDING)
        private val shaderStorageBufferBindingValue = IntArray(1)
        private const val MISSING_SHADER_STORAGE_BLOCK = -1

        fun create(vararg shaders: Shader) = ShaderProgram(glCreateProgram(), shaders.toList())
    }
}