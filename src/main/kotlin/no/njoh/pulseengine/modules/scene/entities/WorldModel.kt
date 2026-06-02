package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.world.WorldRenderContext
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.MaterialRef
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.modules.scene.systems.WorldRenderable
import org.joml.Matrix4f

class WorldModel : SceneEntity(), WorldRenderable, Named
{
    override var name = ""

    @ModelRef var model = ""
    @MaterialRef var material = ""

    @Prop("Position [*P]", i=1) var xPos=0f;   var yPos=0f;   var zPos=0f
    @Prop("Rotation [*R]", i=2) var xRot=0f;   var yRot=0f;   var zRot=0f
    @Prop("Scale    [*S]", i=3) var xScale=1f; var yScale=1f; var zScale=1f

    private val transform = Matrix4f()

    override fun onRender(engine: PulseEngine, context: WorldRenderContext)
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return
        val material = engine.asset.getOrNull<Material>(material)

        transform.identity()
            .translation(xPos, yPos, zPos)
            .rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .scale(xScale, yScale, zScale)

        context.submit(engine, model, transform, material)
    }
}