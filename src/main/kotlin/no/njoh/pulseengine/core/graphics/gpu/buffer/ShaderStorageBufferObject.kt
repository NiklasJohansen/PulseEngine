package no.njoh.pulseengine.core.graphics.gpu.buffer

/**
 * GPU buffer that can be attached to a shader storage block binding point.
 */
interface ShaderStorageBufferObject
{
    fun bindStorageBuffer(binding: Int)
}