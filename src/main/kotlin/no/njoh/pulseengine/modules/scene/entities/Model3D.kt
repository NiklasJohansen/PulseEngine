package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.LOCAL_SHADOW
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.MaterialRef
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f

class Model3D : SceneEntity(), Scene3DRenderable, Named, Spatial3D
{
    override var name = ""

    @ModelRef    var model    = ""
    @MaterialRef var material = ""

    @Prop("Position [*P]", i=1) override var xPos=0f;   override var yPos=0f;   override var zPos=0f
    @Prop("Rotation [*R]", i=2) override var xRot=0f;   override var yRot=0f;   override var zRot=0f
    @Prop("Scale    [*S]", i=3) override var xScale=1f; override var yScale=1f; override var zScale=1f

    @Prop("Shadows", i=4) var castLocalShadows = true
    @Prop("Shadows", i=5) var castSunShadows   = true

    @Prop("LOD", i=6)                   var lodPixelHeightThresholds = ""
    @Prop("LOD", i=7, min=0f, max=0.9f) var lodHysteresis = 0.15f

    private val transform = Matrix4f()
    private var lastLodPixelHeightThresholds   = null as String?
    private var parsedLodPixelHeightThresholds = null as IntArray?

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return
        val material = engine.asset.getOrNull<Material>(material)

        transform.identity()
            .translation(xPos, yPos, zPos)
            .rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .scale(xScale, yScale, zScale)

        if (lodPixelHeightThresholds != lastLodPixelHeightThresholds)
        {
            parsedLodPixelHeightThresholds = lodPixelHeightThresholds.split(',').mapNotNull { it.trim().toIntOrNull()?.coerceAtLeast(0) }.takeIf { it.isNotEmpty() }?.toIntArray()
            lastLodPixelHeightThresholds = lodPixelHeightThresholds
        }

        context.submitModel(
            engine = engine,
            model = model,
            transform = transform,
            material = material,
            renderPassMask = CAMERA or LOCAL_SHADOW.takeIf(castLocalShadows) or GLOBAL_SHADOW.takeIf(castSunShadows),
            lodPixelHeightThresholds = parsedLodPixelHeightThresholds,
            lodHysteresis = lodHysteresis,
            objectId = id
        )
    }
}