package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.modules.scene.systems.WorldRenderable
import no.njoh.pulseengine.modules.scene.systems.WorldShadowCaster
import org.joml.Matrix4f

class ModelEntity : SceneEntity(), WorldRenderable, WorldShadowCaster, Named
{
    override var name = ""
    override var castShadows = true

    @ModelRef var model = ""

    @Prop("Translation", i=0) var xPos = 0f
    @Prop("Translation", i=1) var yPos = 0f
    @Prop("Translation", i=2) var zPos = 0f

    @Prop("Rotation",    i=3) var xRot = 0f
    @Prop("Rotation",    i=4) var yRot = 0f
    @Prop("Rotation",    i=5) var zRot = 0f

    @Prop("Scale",       i=6) var xScale = 1f
    @Prop("Scale",       i=7) var yScale = 1f
    @Prop("Scale",       i=8) var zScale = 1f

    private val transform = Matrix4f()

    override fun onRender(engine: PulseEngine, drawList: DrawList) 
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return

        transform.identity()
            .translation(xPos, yPos, zPos)
            .rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .scale(xScale, yScale, zScale)

        drawList.submit(engine, model, Matrix4f(transform))
    }
}