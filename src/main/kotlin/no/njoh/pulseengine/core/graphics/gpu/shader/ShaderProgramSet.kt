package no.njoh.pulseengine.core.graphics.gpu.shader

import no.njoh.pulseengine.core.graphics.gpu.buffer.ShaderStorageBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.*
import no.njoh.pulseengine.core.shared.utils.Logger

class ShaderProgramSet(
    val staticProgram: ShaderProgram,
    val skinnedProgram: ShaderProgram
) {
    private val reportedMissingShaderStorageBlocks = HashSet<String>(8)

    operator fun get(variant: ShaderVariant) = when (variant)
    {
        STATIC -> staticProgram
        SKINNED -> skinnedProgram
    }

    fun bindStorageBuffer(blockName: String, buffer: ShaderStorageBufferObject?): Boolean =
        bindStorageBuffer(blockName, buffer, logMissingBlock = true)

    fun bindStorageBufferIfPresent(blockName: String, buffer: ShaderStorageBufferObject?): Boolean =
        bindStorageBuffer(blockName, buffer, logMissingBlock = false)

    private fun bindStorageBuffer(blockName: String, buffer: ShaderStorageBufferObject?, logMissingBlock: Boolean): Boolean
    {
        if (buffer == null) return false

        val staticBinding = staticProgram.shaderStorageBufferBindingOf(blockName)
        val skinnedBinding = skinnedProgram.shaderStorageBufferBindingOf(blockName)

        if (staticBinding < 0 && skinnedBinding < 0)
        {
            if (logMissingBlock && reportedMissingShaderStorageBlocks.add(blockName))
                Logger.error { "Shader storage block '$blockName' not found in shader program set (#${staticProgram.id}, #${skinnedProgram.id})" }
            return false
        }
        else if (reportedMissingShaderStorageBlocks.isNotEmpty())
            reportedMissingShaderStorageBlocks.remove(blockName)

        if (staticBinding >= 0)
            buffer.bindStorageBuffer(staticBinding)

        if (skinnedBinding >= 0 && skinnedBinding != staticBinding)
            buffer.bindStorageBuffer(skinnedBinding)

        return true
    }

    enum class ShaderVariant { STATIC, SKINNED }
}