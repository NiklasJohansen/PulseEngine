package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode
import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Material.NormalOrientation
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable

open class SceneMaterial : SceneEntity(), Named, Initiable, Scene3DRenderable
{
    @Prop(i=0) override var name    = "SceneMaterial"
    @Prop(i=1) var overrideMaterial = AssetHandle<Material>()

    @Prop("Color", i=1)                      var baseColorMap      = AssetHandle<Texture>()
    @Prop("Color", i=2)                      var baseColor         = Color(1f, 1f, 1f)
    
    @Prop("PBR", i=3)                        var aoRoughMetalMap   = AssetHandle<Texture>()
    @Prop("PBR", i=4, min=0f, max=1f)        var occlusionStrength = 1f
    @Prop("PBR", i=5, min=0f)                var metallicFactor    = 0f
    @Prop("PBR", i=6, min=0f)                var roughnessFactor   = 1f
    
    @Prop("Normals", i=7)                    var normalMap         = AssetHandle<Texture>()
    @Prop("Normals", i=8, min=0f)            var normalScale       = 1f
    @Prop("Normals", i=9)                    var normalOrientation = NormalOrientation.NORMAL

    @Prop("Emissive", i=10)                  var emissiveMap       = AssetHandle<Texture>()
    @Prop("Emissive", i=11)                  var emissiveFactor    = Color(0f, 0f, 0f)
    @Prop("Emissive", i=12, min=0f)          var emissiveIntensity = 1f

    @Prop("Height", i=13)                    var heightMap         = AssetHandle<Texture>()
    @Prop("Height", i=14, min=0f)            var heightScale       = Material.DEFAULT_HEIGHT_SCALE

    @Prop("Tiling", i=15, min=0.0001f)       var xTiling           = 1f
    @Prop("Tiling", i=16, min=0.0001f)       var yTiling           = 1f

    @Prop("Rendering", i=17)                 var cullMode          = CullMode.BACK
    @Prop("Rendering", i=18)                 var blendMode         = BlendMode.OPAQUE
    @Prop("Rendering", i=19, min=0f, max=1f) var alphaCutoff       = 0.5f
    @Prop("Rendering", i=29)                 var layerMask         = 1

    @Prop(hidden = true)
    var setFromMaterial = true

    private var lastMaterialName = ""

    override fun onCreate()
    {
        lastMaterialName = overrideMaterial.name
    }

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        if (overrideMaterial.name != lastMaterialName)
        {
            lastMaterialName = overrideMaterial.name
            setFromMaterial = true
        }

        val source = engine.asset.getOrNull(overrideMaterial)

        if (setFromMaterial && source != null)
        {
            baseColor.setFrom(source.baseColor)
            emissiveFactor.setFrom(source.emissiveFactor)

            baseColorMap.name    = source.albedoMap?.name ?: ""
            normalMap.name       = source.normalMap?.name ?: ""
            aoRoughMetalMap.name = source.aoRoughMetalMap?.name ?: ""
            emissiveMap.name     = source.emissiveMap?.name ?: ""
            heightMap.name       = source.heightMap?.name ?: ""
            cullMode             = source.cullMode
            blendMode            = source.blendMode
            alphaCutoff          = source.alphaCutoff
            layerMask            = source.layerMask
            metallicFactor       = source.metallicFactor
            roughnessFactor      = source.roughnessFactor
            emissiveIntensity    = 1f
            occlusionStrength    = source.occlusionStrength
            heightScale          = source.heightScale
            normalScale          = source.normalScale
            normalOrientation    = source.normalOrientation
            xTiling              = source.xTiling
            yTiling              = source.yTiling

            setFromMaterial = false
            set(PROPERTIES_UPDATED)
        }

        context.submitMaterial(
            engine             = engine,
            sourceId           = id,
            name               = name,
            materialToOverride = overrideMaterial,
            baseColor          = baseColor,
            albedoMap          = engine.asset.getOrNull(baseColorMap),
            normalMap          = engine.asset.getOrNull(normalMap),
            aoRoughMetalMap    = engine.asset.getOrNull(aoRoughMetalMap),
            emissiveMap        = engine.asset.getOrNull(emissiveMap),
            heightMap          = engine.asset.getOrNull(heightMap),
            cullMode           = cullMode,
            blendMode          = blendMode,
            alphaCutoff        = alphaCutoff,
            metallicFactor     = metallicFactor,
            roughnessFactor    = roughnessFactor,
            emissiveFactor     = emissiveFactor.asLinear().multiplyRgb(emissiveIntensity).asSrgb(),
            occlusionStrength  = occlusionStrength,
            heightScale        = heightScale,
            normalScale        = normalScale,
            normalOrientation  = normalOrientation,
            xTiling            = xTiling,
            yTiling            = yTiling,
            layerMask          = layerMask
        )
    }
}