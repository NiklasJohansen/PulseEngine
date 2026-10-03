package no.njoh.pulseengine.core.graphics.gpu.shader

import no.njoh.pulseengine.core.graphics.gpu.buffer.ShaderStorageBufferObject
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant.*
import no.njoh.pulseengine.core.shared.utils.Logger

class ShaderProgramSet(
    val staticProgram: ShaderProgram,
    val skinnedProgram: ShaderProgram,
    val pomStaticProgram: ShaderProgram = staticProgram,
    val pomSkinnedProgram: ShaderProgram = skinnedProgram
) {
    private val reportedMissingShaderStorageBlocks = HashSet<String>(8)

    operator fun get(variant: ShaderVariant) = when (variant)
    {
        STATIC -> staticProgram
        SKINNED -> skinnedProgram
        STATIC_POM -> pomStaticProgram
        SKINNED_POM -> pomSkinnedProgram
    }

    inline fun forEachProgram(action: (ShaderProgram) -> Unit)
    {
        action(staticProgram)
        action(skinnedProgram)
        if (pomStaticProgram !== staticProgram) action(pomStaticProgram)
        if (pomSkinnedProgram !== skinnedProgram) action(pomSkinnedProgram)
    }

    fun bindStorageBuffer(blockName: String, buffer: ShaderStorageBufferObject?): Boolean =
        bindStorageBuffer(blockName, buffer, logMissingBlock = true)

    fun bindStorageBufferIfPresent(blockName: String, buffer: ShaderStorageBufferObject?): Boolean =
        bindStorageBuffer(blockName, buffer, logMissingBlock = false)

    private fun bindStorageBuffer(blockName: String, buffer: ShaderStorageBufferObject?, logMissingBlock: Boolean): Boolean
    {
        if (buffer == null) return false

        var lastBinding = -1
        forEachProgram()
        {
            val binding = it.shaderStorageBufferBindingOf(blockName)
            if (binding >= 0 && binding != lastBinding)
            {
                buffer.bindStorageBuffer(binding)
                lastBinding = binding
            }
        }

        if (lastBinding < 0)
        {
            if (logMissingBlock && reportedMissingShaderStorageBlocks.add(blockName))
                Logger.error { "Shader storage block '$blockName' not found in shader program set (#${staticProgram.id}, #${skinnedProgram.id})" }

            return false
        }
        else if (reportedMissingShaderStorageBlocks.isNotEmpty())
        {
            reportedMissingShaderStorageBlocks.remove(blockName)
        }

        return true
    }

    enum class ShaderVariant { STATIC, SKINNED, STATIC_POM, SKINNED_POM }
}