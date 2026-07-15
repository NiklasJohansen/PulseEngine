package no.njoh.pulseengine.modules.physics3d.entities

import kotlin.math.abs
import kotlin.math.max
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.CAMERA
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.GLOBAL_SHADOW
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.LOCAL_SHADOW
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.MaterialRef
import no.njoh.pulseengine.core.shared.annotations.ModelRef
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.toDegrees
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.modules.physics3d.BodyType3D
import no.njoh.pulseengine.modules.physics3d.BoxGeometry3D
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.ColliderType3D.*
import no.njoh.pulseengine.modules.physics3d.ConvexHullGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.ShapeGeometry3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import no.njoh.pulseengine.modules.physics3d.SphereGeometry3D
import no.njoh.pulseengine.modules.physics3d.TriangleMeshGeometry3D
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * A physically simulated and rendered 3D model.
 */
@Name("Physics Model (3D)")
@Icon("SHAPES")
open class PhysicsModel3D : SceneEntity(), Initiable, PhysicsEntity3D, Scene3DRenderable, Named
{
    override var name = "Physics Model"

    @Prop("Position [*P]", i=1)             override var xPos   = 0f; override var yPos   = 0f; override var zPos   = 0f
    @Prop("Rotation [*R]", i=2)             override var xRot   = 0f; override var yRot   = 0f; override var zRot   = 0f
    @Prop("Scale    [*S]", i=3, min=0.001f) override var xScale = 1f; override var yScale = 1f; override var zScale = 1f

    @Prop("Physics",   i=0)                 override var bodyType            = BodyType3D.DYNAMIC
    @Prop("Physics",   i=1, min=0f)         override var density             = 1f
    @Prop("Physics",   i=2, min=0f)         override var friction            = 0.6f
    @Prop("Physics",   i=3, min=0f, max=1f) override var restitution         = 0f
    @Prop("Physics",   i=4, min=0f)         override var linearDamping       = 0f
    @Prop("Physics",   i=5, min=0f)         override var angularDamping      = 0f
    @Prop("Physics",   i=6)                 override var gravityScale        = 1f
    @Prop("Physics",   i=7)                 override var bullet              = false
    @Prop("Physics",   i=8)                 override var fixedRotation       = false
    @Prop("Collision", i=1)                 override var layerMask           =  1
    @Prop("Collision", i=2)                 override var collisionMask       = -1
    @Prop("Collision", i=3)                 override var sensor              = false
    @Prop("Collision", i=4)                          var colliderType = BOX

    @Prop("Rendering", i=1) @ModelRef    var model = "cube"
    @Prop("Rendering", i=2) @MaterialRef var material = ""
    
    @Prop("Shadows", i=1) var castLocalShadows = true
    @Prop("Shadows", i=2) var castSunShadows   = true

    private val tmpTransform      = Matrix4f()
    private val tmpEulerRotation  = Vector3f()
    private val tmpRenderPosition = Vector3f()
    private val tmpRenderRotation = Quaternionf()

    private val currentPosition  = Vector3f()
    private val currentRotation  = Quaternionf()
    private val previousPosition = Vector3f()
    private val previousRotation = Quaternionf()
    private val synchronizedPosition = Vector3f()
    private val synchronizedRotation = Vector3f()

    override fun onRender(engine: PulseEngine, context: SceneRenderContext)
    {
        val model = engine.asset.getOrNull<Model>(model) ?: return
        val material = engine.asset.getOrNull<Material>(material)

        tmpTransform.identity()

        if (engine.scene.state == SceneState.RUNNING)
        {
            val i = engine.data.interpolation
            previousPosition.lerp(currentPosition, i, tmpRenderPosition)
            previousRotation.slerp(currentRotation, i, tmpRenderRotation)
            tmpTransform.translation(tmpRenderPosition).rotate(tmpRenderRotation)
        }
        else
        {
            tmpTransform.translation(xPos, yPos, zPos).rotateXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        }

        tmpTransform.scale(xScale, yScale, zScale)

        context.submitModel(
            engine = engine,
            model = model,
            transform = tmpTransform,
            material = material,
            renderPassMask = CAMERA or LOCAL_SHADOW.takeIf(castLocalShadows) or GLOBAL_SHADOW.takeIf(castSunShadows),
            objectId = id
        )
    }

    override fun onPhysicsBodyCreated(engine: PulseEngine, body: PhysicsBody3D)
    {
        currentPosition.set(xPos, yPos, zPos)
        currentRotation.rotationXYZ(xRot.toRadians(), yRot.toRadians(), zRot.toRadians())
        previousPosition.set(currentPosition)
        previousRotation.set(currentRotation)

        recordSynchronizedTransform()
    }

    override fun onPhysicsTransformUpdated(position: Vector3fc, rotation: Quaternionfc)
    {
        previousPosition.set(currentPosition)
        previousRotation.set(currentRotation)
        
        currentPosition.set(position)
        currentRotation.set(rotation)
        currentRotation.getEulerAnglesXYZ(tmpEulerRotation)

        xPos = currentPosition.x
        yPos = currentPosition.y
        zPos = currentPosition.z
        xRot = tmpEulerRotation.x.toDegrees()
        yRot = tmpEulerRotation.y.toDegrees()
        zRot = tmpEulerRotation.z.toDegrees()

        recordSynchronizedTransform() 
    }

    override fun onExternalTransformApplied(position: Vector3fc, rotation: Quaternionfc)
    {
        previousPosition.set(position)
        currentPosition.set(position)
        previousRotation.set(rotation)
        currentRotation.set(rotation)

        recordSynchronizedTransform()
    }

    override fun hasPendingTransformChange() =
        xPos != synchronizedPosition.x || yPos != synchronizedPosition.y || zPos != synchronizedPosition.z || 
        xRot != synchronizedRotation.x || yRot != synchronizedRotation.y || zRot != synchronizedRotation.z

    override fun getShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>
    {
        val model = engine.asset.getOrNull<Model>(model)
        val bounds = model?.localBounds
        val xMin = bounds?.xMin ?: -0.5f
        val yMin = bounds?.yMin ?: -0.5f
        val zMin = bounds?.zMin ?: -0.5f
        val xMax = bounds?.xMax ?: 0.5f
        val yMax = bounds?.yMax ?: 0.5f
        val zMax = bounds?.zMax ?: 0.5f

        val center = Vector3f(
            (xMin + xMax) * 0.5f * xScale,
            (yMin + yMax) * 0.5f * yScale,
            (zMin + zMax) * 0.5f * zScale
        )

        val xSize = (xMax - xMin) * abs(xScale)
        val ySize = (yMax - yMin) * abs(yScale)
        val zSize = (zMax - zMin) * abs(zScale)

        val shapeDefinitions = mutableListOf<Box3DShapeDefinition>()
        
        when (colliderType)
        {
            BOX ->
            {
                shapeDefinitions += createShapeDefinition(BoxGeometry3D(Vector3f(xSize, ySize, zSize), center))
            }
            SPHERE ->
            {
                shapeDefinitions += createShapeDefinition(SphereGeometry3D(max(xSize, max(ySize, zSize)) * 0.5f, center))
            }
            CAPSULE ->
            {
                val radius = max(xSize, zSize) * 0.5f
                val segmentHalfHeight = max(0f, ySize * 0.5f - radius)
                shapeDefinitions += createShapeDefinition(
                    geometry = CapsuleGeometry3D(
                        point1 = Vector3f(center.x, center.y - segmentHalfHeight, center.z),
                        point2 = Vector3f(center.x, center.y + segmentHalfHeight, center.z),
                        radius = radius
                    )
                )
            }
            CONVEX_HULL ->
            {
                val scale = Vector3f(xScale, yScale, zScale)
                for (mesh in model?.collisionMeshes ?: emptyList())
                    shapeDefinitions += createShapeDefinition(ConvexHullGeometry3D(mesh, scale))
            }
            TRIANGLE_MESH ->
            {
                val scale = Vector3f(xScale, yScale, zScale)
                for (mesh in model?.collisionMeshes ?: emptyList())
                    shapeDefinitions += createShapeDefinition(TriangleMeshGeometry3D(mesh, scale))
            }
        }
        
        return shapeDefinitions
    }

    override fun getPhysicsPropertyHash(engine: PulseEngine): Int
    {
        val model = engine.asset.getOrNull<Model>(model)
        val bounds = model?.localBounds
        var hash = super.getPhysicsPropertyHash(engine)

        hash = 31 * hash + colliderType.ordinal
        hash = 31 * hash + xScale.toBits()
        hash = 31 * hash + yScale.toBits()
        hash = 31 * hash + zScale.toBits()
        hash = 31 * hash + (bounds?.xMin?.toBits() ?: 0)
        hash = 31 * hash + (bounds?.yMin?.toBits() ?: 0)
        hash = 31 * hash + (bounds?.zMin?.toBits() ?: 0)
        hash = 31 * hash + (bounds?.xMax?.toBits() ?: 0)
        hash = 31 * hash + (bounds?.yMax?.toBits() ?: 0)
        hash = 31 * hash + (bounds?.zMax?.toBits() ?: 0)
        hash = 31 * hash + System.identityHashCode(model?.collisionMeshes)

        return hash
    }

    private fun createShapeDefinition(geometry: ShapeGeometry3D) = Box3DShapeDefinition(
        geometry = geometry,
        density = if (bodyType == BodyType3D.STATIC) 0f else density,
        friction = friction,
        restitution = restitution,
        categoryBits = layerMask.toLong() and 0xffffffffL,
        maskBits = collisionMask.toLong() and 0xffffffffL,
        sensor = sensor
    )
    
    private fun recordSynchronizedTransform()
    {
        synchronizedPosition.set(xPos, yPos, zPos)
        synchronizedRotation.set(xRot, yRot, zRot)
    }
}