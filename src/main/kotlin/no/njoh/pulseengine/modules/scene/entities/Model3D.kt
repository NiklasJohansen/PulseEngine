package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderVisibility.CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderVisibility.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderVisibility.LOCAL_SHADOW
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.MaterialRef
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f

class Model3D : SceneEntity(), Scene3DRenderable, Named
{
    override var name = ""

    @ModelRef var model = ""
    @MaterialRef var material = ""

    @Prop("Position [*P]", i=1) var xPos=0f;   var yPos=0f;   var zPos=0f
    @Prop("Rotation [*R]", i=2) var xRot=0f;   var yRot=0f;   var zRot=0f
    @Prop("Scale    [*S]", i=3) var xScale=1f; var yScale=1f; var zScale=1f

    @Prop("Lighting", i=4) var castLocalShadows = true
    @Prop("Lighting", i=5) var castSunShadows   = true
    
    private val transform = Matrix4f()

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return
        val material = engine.asset.getOrNull<Material>(material)

        transform.identity()
            .translation(xPos, yPos, zPos)
            .rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .scale(xScale, yScale, zScale)

        var mask = CAMERA
        if (castLocalShadows) mask = mask or LOCAL_SHADOW
        if (castSunShadows)   mask = mask or GLOBAL_SHADOW

        context.submitModel(engine, model, transform, material, visibilityMask = mask)
    }
}