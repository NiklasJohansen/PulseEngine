package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Material.BlendMode
import no.njoh.pulseengine.core.asset.types.Material.CullMode
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

    @Prop("Properties", i= 1) var baseColor         = Color(1f, 1f, 1f)
    @Prop("Properties", i= 2) var albedo            = AssetHandle<Texture>()
    @Prop("Properties", i= 3) var normal            = AssetHandle<Texture>()
    @Prop("Properties", i= 4) var aoMetalRough      = AssetHandle<Texture>()
    @Prop("Properties", i= 5) var emissive          = AssetHandle<Texture>()
    @Prop("Properties", i= 6) var height            = AssetHandle<Texture>()
    @Prop("Properties", i= 7) var cullMode          = CullMode.BACK
    @Prop("Properties", i= 8) var blendMode         = BlendMode.OPAQUE
    @Prop("Properties", i= 9) var alphaCutoff       = 0.5f
    @Prop("Properties", i=10) var metallicFactor    = 0f
    @Prop("Properties", i=11) var roughnessFactor   = 1f
    @Prop("Properties", i=12) var emissiveFactor    = Color(0f, 0f, 0f)
    @Prop("Properties", i=13) var emissiveIntensity = 1f
    @Prop("Properties", i=14) var occlusionStrength = 0f
    @Prop("Properties", i=15) var normalScale       = 1f
    @Prop("Properties", i=16) var xTiling           = 1f
    @Prop("Properties", i=17) var yTiling           = 1f

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
            albedo.name = material.albedo?.name ?: ""
            normal.name = material.normal?.name ?: ""
            aoMetalRough.name = material.aoMetalRough?.name ?: ""
            emissive.name = material.emissive?.name ?: ""
            height.name = material.height?.name ?: ""
            cullMode = material.cullMode
            blendMode = material.blendMode
            alphaCutoff = material.alphaCutoff
            metallicFactor = material.metallicFactor
            roughnessFactor = material.roughnessFactor
            emissiveIntensity = 1f
            emissiveFactor.setFrom(material.emissiveFactor)
            occlusionStrength = material.occlusionStrength
            normalScale = material.normalScale
            xTiling = material.xTiling
            yTiling = material.yTiling
            setFromMaterial = false
            set(PROPERTIES_UPDATED)
        }
        else
        {
            material.baseColor.setFrom(baseColor)
            material.albedo = engine.asset.getOrNull(albedo)
            material.normal = engine.asset.getOrNull(normal)
            material.aoMetalRough = engine.asset.getOrNull(aoMetalRough)
            material.emissive = engine.asset.getOrNull(emissive)
            material.height = engine.asset.getOrNull(height)
            material.cullMode = cullMode
            material.blendMode = blendMode
            material.alphaCutoff = alphaCutoff
            material.metallicFactor = metallicFactor
            material.roughnessFactor = roughnessFactor
            material.emissiveFactor.setFrom(emissiveFactor).multiplyRgb(emissiveIntensity)
            material.occlusionStrength = occlusionStrength
            material.normalScale = normalScale
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