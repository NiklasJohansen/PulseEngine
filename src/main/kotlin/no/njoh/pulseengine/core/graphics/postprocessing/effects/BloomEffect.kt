package no.njoh.pulseengine.core.graphics.postprocessing.effects

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.api.TextureDescriptor
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGB16F
import no.njoh.pulseengine.core.graphics.api.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.*
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.*

class BloomEffect(
    override val name: String,
    override val order: Int,
    var intensity: Float = 1.5f,
    var threshold: Float = 1.3f,
    var thresholdSoftness: Float = 0.7f,
    var radius: Float = 0.015f,
    var dirtIntensity: Float = 1f,
    var dirtTextureName: String = ""
) : BaseEffect(
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGBA16F, scale = 1f / 1f), // Output texture
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 2f), // Final bloom texture
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 4f),
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 8f),
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 16f),
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 32f),
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 64f),
    TextureDescriptor(filter = LINEAR, wrapping = CLAMP, format = RGB16F,  scale = 1f / 128f)
) {

    override fun loadShaderPrograms() = listOf(
        ShaderProgram.create("/pulseengine/shaders/effects/bloom.vert", "/pulseengine/shaders/effects/bloom_downsample.frag"),
        ShaderProgram.create("/pulseengine/shaders/effects/bloom.vert", "/pulseengine/shaders/effects/bloom_upsample.frag"),
        ShaderProgram.create("/pulseengine/shaders/effects/bloom.vert", "/pulseengine/shaders/effects/bloom_final.frag")
    )

    override fun applyEffect(engine: PulseEngine, inTextures: List<Texture>): List<Texture>
    {
        val srcTexture = inTextures[0]

        fbo.bind()
        fbo.clear()
        downSample(srcTexture)
        upSample()
        compose(engine, srcTexture)
        fbo.release()

        setViewportSizeToFit(srcTexture)

        return fbo.getTextures()
    }

    private fun downSample(srcTexture: Texture)
    {
        val textures = fbo.getTextures()
        val program = programs[0] // bloom_downsample
        val knee = threshold * thresholdSoftness

        program.bind()
        program.setUniform("prefilterEnabled", true)
        program.setUniform("prefilterParams", threshold, threshold - knee, 2f * knee, 0.25f / (knee + 0.00001f))
        program.setUniform("resolution", srcTexture.width.toFloat(), srcTexture.height.toFloat())
        program.setUniformSampler("srcTexture", srcTexture)

        for (i in 1 until textures.size)
        {
            val texture = textures[i]
            setViewportSizeToFit(texture)
            fbo.attachOutputTexture(texture)
            renderer.draw()

            program.setUniform("resolution", texture.width.toFloat(), texture.height.toFloat())
            program.setUniform("prefilterEnabled", false)
            program.setUniformSampler("srcTexture", texture)
        }
    }

    private fun upSample()
    {
        val textures = fbo.getTextures()
        val program = programs[1] // bloom_upsample
        program.bind()
        program.setUniform("filterRadius", radius)
        program.setUniform("intensity", intensity)
        enableAdditiveBlend()

        for (i in textures.lastIndex downTo 2)
        {
            val texture = textures[i]
            val nextTexture = textures[i - 1]
            program.setUniformSampler("srcTexture", texture)
            setViewportSizeToFit(nextTexture)
            fbo.attachOutputTexture(nextTexture)
            renderer.draw()
        }

        disableBlend()
    }

    private fun compose(engine: PulseEngine, srcTexture: Texture)
    {
        val program = programs[2] // bloom_final
        val bloomTexture = fbo.getTexture(1) ?: srcTexture
        val outputTexture = fbo.getTexture(0) ?: srcTexture
        val dirtTexture = engine.asset.getOrNull<Texture>(dirtTextureName)
        val textureBank = (engine.gfx as GraphicsInternal).textureBank
        val textureArray = textureBank.getTextureArrayFor(dirtTexture)?: textureBank.getTextureArrays().firstOrNull() ?: return

        program.bind()
        program.setUniform("dirtTextureIntensity", 0f)
        program.setUniformSampler("srcTexture", srcTexture)
        program.setUniformSampler("bloomTexture", bloomTexture)
        program.setUniformSamplerArray("textureArray", textureArray)

        if (dirtTexture != null)
        {
            program.setUniform("dirtTextureIndex", dirtTexture.handle.textureIndex.toFloat())
            program.setUniform("dirtTextureUv", dirtTexture.uMax, dirtTexture.vMax)
            program.setUniform("dirtTextureIntensity", dirtIntensity)
        }

        setViewportSizeToFit(outputTexture)
        fbo.attachOutputTexture(outputTexture)
        renderer.draw()
    }

    private fun setViewportSizeToFit(texture: Texture)
    {
        glViewport(0, 0, texture.width, texture.height)
    }

    private fun enableAdditiveBlend()
    {
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE)
        glBlendEquation(GL_FUNC_ADD)
    }

    private fun disableBlend()
    {
        glDisable(GL_BLEND)
    }

    fun getBloomTexture() = fbo.getTexture(1)

    override fun getTexture(index: Int) = fbo.getTexture(index)
}