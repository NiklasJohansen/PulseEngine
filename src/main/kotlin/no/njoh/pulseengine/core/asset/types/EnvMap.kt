package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy.Companion.defaultFor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA32F
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.REPEAT_HORIZONTAL_CLAMP_VERTICAL
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.graphics.scene3d.lighting.EnvironmentMapBuilder.generateDiffuseIBL
import no.njoh.pulseengine.core.graphics.scene3d.lighting.EnvironmentMapBuilder.generateSpecularIBL

@Icon("IMAGE")
class EnvMap(
    filePath: String,
    name: String,
    initWidth: Int = 1,
    initHeight: Int = 1,
    filter: TextureFilter = LINEAR_MIPMAP,
    anisotropy: TextureAnisotropy = defaultFor(filter),
    wrapping: TextureWrapping = REPEAT_HORIZONTAL_CLAMP_VERTICAL,
    format: TextureFormat = RGBA32F,
    maxMipLevels: Int = 10,
    val buildIblMaps: Boolean = true,
    val iblSourceName: String? = null
) : Texture(filePath, name, initWidth, initHeight, filter, anisotropy, wrapping, format, maxMipLevels) {

    override val uploadExactTextureDimensions = true

    override fun getSubAssets(): List<Asset>
    {
        if (!buildIblMaps || loadFailed) return emptyList()
        
        // Specular map
        val maxSpecWidth = 2048
        val baseWidth  = width.coerceAtLeast(1)
        val baseHeight = height.coerceAtLeast(1)
        val aspect = baseHeight.toFloat() / baseWidth.toFloat()
        val specWidth  = minOf(baseWidth / 4, maxSpecWidth) // Quarter res
        val specHeight = maxOf(1, (specWidth * aspect).toInt())
        val mipCount = 1 + (Integer.SIZE - Integer.numberOfLeadingZeros(minOf(specWidth, specHeight)))
        val specularTex = EnvMap(
            filePath = "",
            name = "${name}_specular_ibl",
            initWidth = specWidth,
            initHeight = specHeight,
            filter = filter,
            format = format,
            maxMipLevels = mipCount.coerceAtMost(8),
            buildIblMaps = false,
            iblSourceName = name,
            anisotropy = anisotropy
        )

        // Diffuse map
        val diffWidthBase = baseWidth / 16 // 8192 -> 512
        val diffWidth = diffWidthBase.coerceAtLeast(32)
        val diffHeight = maxOf(1, (diffWidth * aspect).toInt())
        val diffuseTex = EnvMap(
            filePath = "",
            name = "${name}_diffuse_ibl",
            initWidth = diffWidth,
            initHeight = diffHeight,
            filter = TextureFilter.LINEAR,
            format = format,
            maxMipLevels = 1,
            buildIblMaps = false,
            iblSourceName = name,
            anisotropy = defaultFor(TextureFilter.LINEAR)
        )

        return listOf(specularTex, diffuseTex)
    }

    override fun postProcess(engine: PulseEngineInternal)
    {
        val srcEnvMap = engine.asset.getOrNull<EnvMap>(iblSourceName ?: return) ?: return
        if (!handle.isArrayTexture || !srcEnvMap.handle.isArrayTexture) 
            return

        when
        {
            "_specular_ibl" in name -> generateSpecularIBL(engine, srcEnvMap, dstEnv = this, maxMipLevels)
            "_diffuse_ibl"  in name -> generateDiffuseIBL(engine, srcEnvMap, dstEnv = this)
        }
    }
}