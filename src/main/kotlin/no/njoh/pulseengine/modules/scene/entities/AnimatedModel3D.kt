package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Animation
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.scene.interfaces.Updatable
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.utils.Extensions.interpolateFrom
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Vector3f

@Name("3D Animated Model")
class AnimatedModel3D : SceneEntity(), Updatable, Scene3DRenderable, Named, Spatial3D
{
    override var name = ""

    var model = AssetHandle<Model>()

    @Prop("Transform", i=1) override var position = Vector3f(0f)
    @Prop("Transform", i=2) override var rotation = Vector3f(0f)
    @Prop("Transform", i=3) override var scale    = Vector3f(1f)

    @Prop("Animation", i=4) var animation = AssetHandle<Animation>()
    @Prop("Animation", i=5) var animationSpeed = 1f
    @Prop("Animation", i=6) var animationOffsetTime = 0f

    @Prop("Blend Animation", i=7) var blendAnimation = AssetHandle<Animation>()
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
        val model = engine.asset.getOrNull(model) ?: return
        val animation = engine.asset.getOrNull(animation)
        val blendAnimation = engine.asset.getOrNull(blendAnimation)
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
            .translation(position)
            .rotateXYZ(rotation.x.toRadians(), rotation.y.toRadians(), rotation.z.toRadians())
            .scale(scale)

        context.submitModel(engine, model, transform, null, animationPose, objectId = id)
    }

    override fun onUpdate(engine: PulseEngine) {}
}