package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Animation
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import no.njoh.pulseengine.core.shared.annotations.AnimationRef
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f

class AnimatedModel3D : SceneEntity(), Updatable, Scene3DRenderable, Named
{
    override var name = ""

    @ModelRef var model = ""

    @Prop("Position [*P]", i=1) var xPos=0f;   var yPos=0f;   var zPos=0f
    @Prop("Rotation [*R]", i=2) var xRot=0f;   var yRot=0f;   var zRot=0f
    @Prop("Scale    [*S]", i=3) var xScale=1f; var yScale=1f; var zScale=1f

    @AnimationRef
    @Prop("Animation", i=4) var animation = ""
    @Prop("Animation", i=5) var animationSpeed = 1f
    @Prop("Animation", i=6) var animationOffsetTime = 0f

    @AnimationRef
    @Prop("Blend Animation", i=7) var blendAnimation = ""
    @Prop("Blend Animation", i=8) var blendAnimationOffsetTime = 0f
    @Prop("Blend Animation", i=9, min=0f, max=1f) var blendFactor = 0f

    private val transform = Matrix4f()
    private var animationTime = 0.0f
    private var lastAnimationTime = 0.0f

    override fun onFixedUpdate(engine: PulseEngine) 
    { 
        lastAnimationTime = animationTime
        animationTime += animationSpeed * engine.data.fixedDeltaTime
    }

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return
        val animation = engine.asset.getOrNull<Animation>(animation)
        val blendAnimation = engine.asset.getOrNull<Animation>(blendAnimation)
        val time = animationTime.interpolateFrom(lastAnimationTime)
        val animationPose = model.getAnimationPose(
            animation = animation,
            animationTimeSeconds = time + animationOffsetTime,
            frameNumber = engine.data.frameNumber,
            blendAnimation = blendAnimation,
            blendAnimationTimeSeconds = time + blendAnimationOffsetTime,
            blendFactor = blendFactor
        )

        transform.identity()
            .translation(xPos, yPos, zPos)
            .rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
            .scale(xScale, yScale, zScale)

        context.submitModel(engine, model, transform, null, animationPose, objectId = id)
    }

    override fun onUpdate(engine: PulseEngine) {}
}