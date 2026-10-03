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
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable

class MaterialModifier : SceneEntity(), Named, Initiable, Scene3DRenderable
{
    @Prop(i=0) override var name = "Material Modifier"
    @Prop(i=1) var material      = AssetHandle<Material>()

    @Prop("Color", i=1)                     var baseColorMap      = AssetHandle<Texture>()
    @Prop("Color", i=2)                     var baseColor         = Color(1f, 1f, 1f)
    
    @Prop("PBR", i=1)                       var aoRoughMetalMap   = AssetHandle<Texture>()
    @Prop("PBR", i=2, min=0f, max=1f)       var occlusionStrength = 1f
    @Prop("PBR", i=3, min=0f)               var metallicFactor    = 0f
    @Prop("PBR", i=4, min=0f)               var roughnessFactor   = 1f
    
    @Prop("Normals", i=1)                   var normalMap         = AssetHandle<Texture>()
    @Prop("Normals", i=2, min=0f)           var normalScale       = 1f
    @Prop("Normals", i=3)                   var normalOrientation = NormalOrientation.NORMAL

    @Prop("Emissive", i=1)                  var emissiveMap       = AssetHandle<Texture>()
    @Prop("Emissive", i=2)                  var emissiveFactor    = Color(0f, 0f, 0f)
    @Prop("Emissive", i=3, min=0f)          var emissiveIntensity = 1f

    @Prop("Height", i=1)                    var heightMap         = AssetHandle<Texture>()
    @Prop("Height", i=2, min=0f)            var heightScale       = Material.DEFAULT_HEIGHT_SCALE

    @Prop("Tilling", i=1, min=0.0001f)      var xTiling           = 1f
    @Prop("Tilling", i=2, min=0.0001f)      var yTiling           = 1f

    @Prop("Rendering", i=1)                 var cullMode          = CullMode.BACK
    @Prop("Rendering", i=2)                 var blendMode         = BlendMode.OPAQUE
    @Prop("Rendering", i=3, min=0f, max=1f) var alphaCutoff       = 0.5f

    @Prop(hidden = true)
    var setFromMaterial = true

    private var lastMaterialName = ""
    private var markedDirty      = false

    override fun onCreate()
    {
        lastMaterialName = material.name
    }

    override fun onStart(engine: PulseEngine)
    {
        markedDirty = false
    }

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        if (material.name != lastMaterialName)
        {
            lastMaterialName = material.name
            setFromMaterial = true
        }

        val material = engine.asset.getOrNull(material) ?: return

        if (setFromMaterial)
        {
            baseColor.setFrom(material.baseColor)
            baseColorMap.name = material.albedo?.name ?: ""
            normalMap.name = material.normal?.name ?: ""
            aoRoughMetalMap.name = material.aoRoughMetal?.name ?: ""
            emissiveMap.name = material.emissive?.name ?: ""
            heightMap.name = material.height?.name ?: ""
            cullMode = material.cullMode
            blendMode = material.blendMode
            alphaCutoff = material.alphaCutoff
            metallicFactor = material.metallicFactor
            roughnessFactor = material.roughnessFactor
            emissiveIntensity = 1f
            emissiveFactor.setFrom(material.emissiveFactor)
            occlusionStrength = material.occlusionStrength
            heightScale = material.heightScale
            normalScale = material.normalScale
            normalOrientation = material.normalOrientation
            xTiling = material.xTiling
            yTiling = material.yTiling

            setFromMaterial = false
            set(PROPERTIES_UPDATED)
        }
        else
        {
            material.baseColor.setFrom(baseColor)
            material.albedo = engine.asset.getOrNull(baseColorMap)
            material.normal = engine.asset.getOrNull(normalMap)
            material.aoRoughMetal = engine.asset.getOrNull(aoRoughMetalMap)
            material.emissive = engine.asset.getOrNull(emissiveMap)
            material.height = engine.asset.getOrNull(heightMap)
            material.cullMode = cullMode
            material.blendMode = blendMode
            material.alphaCutoff = alphaCutoff
            material.metallicFactor = metallicFactor
            material.roughnessFactor = roughnessFactor
            material.emissiveFactor.setFrom(emissiveFactor).multiplyRgb(emissiveIntensity)
            material.occlusionStrength = occlusionStrength
            material.heightScale = heightScale
            material.normalScale = normalScale
            material.normalOrientation = normalOrientation
            material.xTiling = xTiling
            material.yTiling = yTiling

            if (!markedDirty || engine.scene.state == SceneState.STOPPED)
            {
                material.markDirty()
                markedDirty = true
            }
        }
    }
}