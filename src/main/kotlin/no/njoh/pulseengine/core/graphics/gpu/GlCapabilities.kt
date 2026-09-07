package no.njoh.pulseengine.core.graphics.gpu

import no.njoh.pulseengine.core.graphics.gpu.GlContract.OPENGL_44
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS
import org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS
import org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS
import org.lwjgl.opengl.GL30.GL_MAX_ARRAY_TEXTURE_LAYERS
import org.lwjgl.opengl.GL30.GL_MAX_COLOR_ATTACHMENTS
import org.lwjgl.opengl.GL30.GL_MAX_SAMPLES
import org.lwjgl.opengl.GL43.*

/**
 * Cached OpenGL feature flags for the current context.
 *
 * The configured [glContract] constrains the exposed feature set even when the driver
 * returns a newer context. This keeps the base graphics profile deterministic and prevents a game
 * from silently accessing full graphics features.
 */
object GlCapabilities
{
    /** Increasing identity for the OpenGL context initialized by [create]. */
    var contextGeneration = 0L
        private set

    internal lateinit var glContract: GlContract
        private set

    lateinit var limits: GlCapabilityLimits
        private set

    var openGlVersion = "Unknown"; private set
    var glslVersion   = "Unknown"; private set
    var vendor        = "Unknown"; private set
    var renderer      = "Unknown"; private set

    /** True when `glDraw*BaseInstance` entry points are enabled by the selected contract. */
    var baseInstance = false; private set

    /** True when shaders can read draw parameters such as `gl_BaseInstance`. */
    var shaderDrawParameters = false; private set

    /** True when multi-draw indirect entry points are enabled by the selected contract. */
    var multiDrawIndirect = false; private set

    /** True when immutable persistent mapped buffer storage is enabled by the selected contract. */
    var persistentMappedBuffers = false; private set

    /** True when compute shaders are enabled by the selected contract. */
    var computeShaders = false; private set

    /** True when shader storage buffer objects are enabled by the selected contract. */
    var shaderStorageBuffers = false; private set

    /** True when immutable texture storage is enabled by the selected contract. */
    var immutableTextureStorage = false; private set

    /** True when direct texture clearing is enabled by the selected contract. */
    var clearTexture = false; private set

    /** True when shader draw parameters are available through core OpenGL 4.6. */
    var shaderDrawParametersCore = false; private set

    /** True when shader draw parameters are available through `GL_ARB_shader_draw_parameters`. */
    var shaderDrawParametersArb = false; private set

    /** True when anisotropic texture filtering is available. */
    var textureFilterAnisotropic = false; private set

    /** Maximum anisotropy supported by the current OpenGL context. */
    var maxTextureAnisotropy = 1f; private set

    /** True when the configured runtime profile enables the full graphics contract. */
    val fullGraphicsEnabled get() = glContract == OPENGL_44

    /**
     * Creates LWJGL's capability table and validates the complete configured graphics contract.
     * Must be called after an OpenGL context has been made current.
     */
    internal fun create(contract: GlContract)
    {
        val caps = GL.createCapabilities()
        contextGeneration++
        glContract = contract

        openGlVersion = glGetString(GL_VERSION) ?: "Unknown"
        glslVersion   = glGetString(GL_SHADING_LANGUAGE_VERSION) ?: "Unknown"
        vendor        = glGetString(GL_VENDOR) ?: "Unknown"
        renderer      = glGetString(GL_RENDERER) ?: "Unknown"

        val fullGraphicsContract = contract == OPENGL_44
        baseInstance             = fullGraphicsContract && (caps.OpenGL42 || caps.GL_ARB_base_instance)
        shaderDrawParametersCore = fullGraphicsContract && caps.OpenGL46
        shaderDrawParametersArb  = fullGraphicsContract && caps.GL_ARB_shader_draw_parameters
        shaderDrawParameters     = shaderDrawParametersCore || shaderDrawParametersArb
        multiDrawIndirect        = fullGraphicsContract && (caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect)
        persistentMappedBuffers  = fullGraphicsContract && (caps.OpenGL44 || caps.GL_ARB_buffer_storage)
        computeShaders           = fullGraphicsContract && (caps.OpenGL43 || caps.GL_ARB_compute_shader)
        shaderStorageBuffers     = fullGraphicsContract && (caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object)
        immutableTextureStorage  = fullGraphicsContract && (caps.OpenGL42 || caps.GL_ARB_texture_storage)
        clearTexture             = fullGraphicsContract && (caps.OpenGL44 || caps.GL_ARB_clear_texture)
        textureFilterAnisotropic = caps.OpenGL46 || caps.GL_ARB_texture_filter_anisotropic || caps.GL_EXT_texture_filter_anisotropic

        maxTextureAnisotropy = if (textureFilterAnisotropic) FloatArray(1).also { glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, it) }.first().coerceAtLeast(1f) else 1f

        val hasSsboQueries    = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object
        val hasComputeQueries = caps.OpenGL43 || caps.GL_ARB_compute_shader

        limits = GlCapabilityLimits(
            maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE),
            maxArrayTextureLayers = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS),
            maxTextureImageUnits = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS),
            maxCombinedTextureImageUnits = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
            maxSamples = glGetInteger(GL_MAX_SAMPLES),
            maxDrawBuffers = glGetInteger(GL_MAX_DRAW_BUFFERS),
            maxColorAttachments = glGetInteger(GL_MAX_COLOR_ATTACHMENTS),
            maxShaderStorageBufferBindings = if (hasSsboQueries) glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) else 0,
            maxVertexShaderStorageBlocks   = if (hasSsboQueries) glGetInteger(GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS) else 0,
            maxFragmentShaderStorageBlocks = if (hasSsboQueries) glGetInteger(GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS) else 0,
            maxComputeShaderStorageBlocks  = if (hasSsboQueries && hasComputeQueries) glGetInteger(GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS) else 0,
            maxCombinedShaderStorageBlocks = if (hasSsboQueries) glGetInteger(GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS) else 0,
            maxComputeWorkGroupInvocations = if (hasComputeQueries) glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS) else 0
        )

        val missingRequirements = findMissingRequirements(
            supportsOpenGl41 = caps.OpenGL41,
            supportsOpenGl44 = caps.OpenGL44,
            supportsPersistentMappedBuffers = persistentMappedBuffers,
            supportsComputeShaders = computeShaders,
            supportsShaderStorageBuffers = shaderStorageBuffers,
            limits = limits,
            contract = contract
        )

        if (missingRequirements.isNotEmpty())
        {
            val message = buildString()
            {
                append("OpenGL contract $contract is not supported by $renderer ($openGlVersion):")
                missingRequirements.forEach { append("\n - ").append(it) }
            }
            throw IllegalStateException(message)
        }

        Logger.info { "Graphics contract: $contract, OpenGL: $openGlVersion, GLSL: $glslVersion, GPU: $renderer ($vendor), Limits: $limits" }
    }

    fun requireFullGraphics(feature: String)
    {
        check(fullGraphicsEnabled) { "$feature requires RuntimeProfile.FULL_GRAPHICS, but this game configured RuntimeProfile.BASE_GRAPHICS" }
    }

    private fun findMissingRequirements(
        supportsOpenGl41: Boolean,
        supportsOpenGl44: Boolean,
        supportsPersistentMappedBuffers: Boolean,
        supportsComputeShaders: Boolean,
        supportsShaderStorageBuffers: Boolean,
        limits: GlCapabilityLimits,
        contract: GlContract
    ) = buildList {

        if (!supportsOpenGl41) add("OpenGL 4.1 core is required")

        requireLimit("GL_MAX_TEXTURE_SIZE", limits.maxTextureSize, 8192)
        requireLimit("GL_MAX_ARRAY_TEXTURE_LAYERS", limits.maxArrayTextureLayers, 100)
        requireLimit("GL_MAX_TEXTURE_IMAGE_UNITS", limits.maxTextureImageUnits, 16)
        requireLimit("GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS", limits.maxCombinedTextureImageUnits, 16)
        requireLimit("GL_MAX_SAMPLES", limits.maxSamples, 4)

        if (contract == OPENGL_44)
        {
            if (!supportsOpenGl44)                add("OpenGL 4.4 core is required")
            if (!supportsPersistentMappedBuffers) add("Persistent mapped buffers are required")
            if (!supportsComputeShaders)          add("Compute shaders are required")
            if (!supportsShaderStorageBuffers)    add("Shader storage buffer objects are required")

            requireLimit("GL_MAX_TEXTURE_IMAGE_UNITS",            limits.maxTextureImageUnits,           required = 20)
            requireLimit("GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS",   limits.maxCombinedTextureImageUnits,   required = 20)
            requireLimit("GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS", limits.maxShaderStorageBufferBindings, required = 12)
            requireLimit("GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS",   limits.maxVertexShaderStorageBlocks,   required =  3)
            requireLimit("GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS", limits.maxFragmentShaderStorageBlocks, required =  5)
            requireLimit("GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS",  limits.maxComputeShaderStorageBlocks,  required = 12)
            requireLimit("GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS", limits.maxCombinedShaderStorageBlocks, required = 12)
            requireLimit("GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS", limits.maxComputeWorkGroupInvocations, required = 64)
            requireLimit("GL_MAX_DRAW_BUFFERS",                   limits.maxDrawBuffers,                 required =  3)
            requireLimit("GL_MAX_COLOR_ATTACHMENTS",              limits.maxColorAttachments,            required =  3)
        }
    }

    private fun MutableList<String>.requireLimit(name: String, actual: Int, required: Int)
    {
        if (actual < required) add("$name is $actual, requires at least $required")
    }
}

data class GlCapabilityLimits(
    val maxTextureSize: Int,
    val maxArrayTextureLayers: Int,
    val maxTextureImageUnits: Int,
    val maxCombinedTextureImageUnits: Int,
    val maxSamples: Int,
    val maxDrawBuffers: Int,
    val maxColorAttachments: Int,
    val maxShaderStorageBufferBindings: Int,
    val maxVertexShaderStorageBlocks: Int,
    val maxFragmentShaderStorageBlocks: Int,
    val maxComputeShaderStorageBlocks: Int,
    val maxCombinedShaderStorageBlocks: Int,
    val maxComputeWorkGroupInvocations: Int
)