package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.LOCAL_SHADOW
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Vector3f

@Name("3D Model")
class Model3D : SceneEntity(), Initiable, Scene3DRenderable, Named, Spatial3D
{
    override var name = ""

    var model    = AssetHandle<Model>("cube")
    var material = AssetHandle<Material>()

    @Prop("Transform", i=1) override var position = Vector3f(0f)
    @Prop("Transform", i=2) override var rotation = Vector3f(0f)
    @Prop("Transform", i=3) override var scale    = Vector3f(1f)

    @Prop("Shadows", i=4) var castLocalShadows = true
    @Prop("Shadows", i=5) var castSunShadows   = true

    @Prop("LOD", i=6)                   var lodPixelHeightThresholds = ""
    @Prop("LOD", i=7, min=0f, max=0.9f) var lodHysteresis = 0.15f

    private val transform = Matrix4f()
    private var parsedLodPixelHeightThresholds = null as IntArray?

    override fun onStart(engine: PulseEngine)
    {
        parsedLodPixelHeightThresholds = lodPixelHeightThresholds.split(',').mapNotNull { it.trim().toIntOrNull()?.coerceAtLeast(0) }.takeIf { it.isNotEmpty() }?.toIntArray()
    }

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        val model = engine.asset.getOrNull(model) ?: return
        val material = engine.asset.getOrNull(material)

        transform.identity()
            .translation(position)
            .rotateXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
            .scale(scale)

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