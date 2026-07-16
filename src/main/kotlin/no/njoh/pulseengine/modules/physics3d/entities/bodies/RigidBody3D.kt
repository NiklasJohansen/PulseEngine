package no.njoh.pulseengine.modules.physics3d.entities.bodies

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
import no.njoh.pulseengine.modules.physics3d.PhysicsBodyType3D
import no.njoh.pulseengine.modules.physics3d.BoxGeometry3D
import no.njoh.pulseengine.modules.physics3d.CapsuleGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsColliderType3D.*
import no.njoh.pulseengine.modules.physics3d.ConvexHullGeometry3D
import no.njoh.pulseengine.modules.physics3d.PhysicsBody3D
import no.njoh.pulseengine.modules.physics3d.ShapeGeometry3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DShapeDefinition
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBodyDefinition
import no.njoh.pulseengine.modules.physics3d.SphereGeometry3D
import no.njoh.pulseengine.modules.physics3d.TriangleMeshGeometry3D
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * A physically simulated and rendered 3D rigid body entity.
 */
@Name("3D Physics Body")
@Icon("SHAPES")
open class RigidBody3D : SceneEntity(), Initiable, PhysicsBodyEntity3D, Scene3DRenderable, Named
{
    override var name = "Physics body "

    @Prop("Position [*P]", i=1)             override var xPos   = 0f; override var yPos   = 0f; override var zPos   = 0f
    @Prop("Rotation [*R]", i=2)             override var xRot   = 0f; override var yRot   = 0f; override var zRot   = 0f
    @Prop("Scale    [*S]", i=3, min=0.001f) override var xScale = 1f; override var yScale = 1f; override var zScale = 1f

    @Prop("Physics",   i=0)                 override var bodyType            = PhysicsBodyType3D.DYNAMIC
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

    private val currentPosition      = Vector3f()
    private val currentRotation      = Quaternionf()
    private val previousPosition     = Vector3f()
    private val previousRotation     = Quaternionf()
    private val synchronizedPosition = Vector3f()
    private val synchronizedRotation = Vector3f()

    private val bodyDefinition   = Box3DBodyDefinition()
    private val shapeDefinitions = ArrayList<Box3DShapeDefinition>()

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

    override fun getPhysicsBodyDefinition() = bodyDefinition.updateFrom(this)

    override fun getPhysicsShapeDefinitions(engine: PulseEngine): List<Box3DShapeDefinition>
    {
        val model = engine.asset.getOrNull<Model>(model)
        val bounds = model?.localBounds

        val xMin = bounds?.xMin ?: -0.5f
        val yMin = bounds?.yMin ?: -0.5f
        val zMin = bounds?.zMin ?: -0.5f
        val xMax = bounds?.xMax ?: 0.5f
        val yMax = bounds?.yMax ?: 0.5f
        val zMax = bounds?.zMax ?: 0.5f

        val xCenter = (xMin + xMax) * 0.5f * xScale
        val yCenter = (yMin + yMax) * 0.5f * yScale
        val zCenter = (zMin + zMax) * 0.5f * zScale
        val xSize   = (xMax - xMin) * abs(xScale)
        val ySize   = (yMax - yMin) * abs(yScale)
        val zSize   = (zMax - zMin) * abs(zScale)

        var shapeCount = 0

        when (colliderType)
        {
            BOX ->
            {
                updateShapeDefinition(shapeCount++, { BoxGeometry3D() })
                {
                    it.size.set(xSize, ySize, zSize)
                    it.center.set(xCenter, yCenter, zCenter)
                    it.rotation.identity()
                }
            }
            SPHERE ->
            {
                updateShapeDefinition(shapeCount++, { SphereGeometry3D() })
                {
                    it.radius = max(xSize, max(ySize, zSize)) * 0.5f
                    it.center.set(xCenter, yCenter, zCenter)
                }
            }
            CAPSULE ->
            {
                val radius = max(xSize, zSize) * 0.5f
                val segmentHalfHeight = max(0f, ySize * 0.5f - radius)
                updateShapeDefinition(shapeCount++, { CapsuleGeometry3D() })
                {
                    it.point1.set(xCenter, yCenter - segmentHalfHeight, zCenter)
                    it.point2.set(xCenter, yCenter + segmentHalfHeight, zCenter)
                    it.radius = radius
                }
            }
            CONVEX_HULL ->
            {
                for (mesh in model?.collisionMeshes ?: emptyList())
                {
                    updateShapeDefinition(shapeCount++, { ConvexHullGeometry3D(mesh) })
                    {
                        it.mesh = mesh
                        it.scale.set(xScale, yScale, zScale)
                    }
                }
            }
            TRIANGLE_MESH ->
            {
                for (mesh in model?.collisionMeshes ?: emptyList())
                {
                    updateShapeDefinition(shapeCount++, { TriangleMeshGeometry3D(mesh) })
                    {
                        it.mesh = mesh
                        it.scale.set(xScale, yScale, zScale)
                    }
                }
            }
        }

        while (shapeDefinitions.size > shapeCount) 
            shapeDefinitions.removeLast()

        return shapeDefinitions
    }

    private inline fun <reified T : ShapeGeometry3D> updateShapeDefinition(
        index: Int,
        createGeometry: () -> T,
        updateGeometry: (T) -> Unit
    ) {
        val definition = shapeDefinitions.getOrNull(index) ?: Box3DShapeDefinition(createGeometry()).also { shapeDefinitions += it }
        val geometry = (definition.geometry as? T) ?: createGeometry()

        updateGeometry(geometry)
        definition.geometry = geometry
        definition.density = if (bodyType == PhysicsBodyType3D.STATIC) 0f else density
        definition.friction = friction
        definition.restitution = restitution
        definition.categoryBits = layerMask.toLong() and 0xffffffffL
        definition.maskBits = collisionMask.toLong() and 0xffffffffL
        definition.sensor = sensor
    }
    
    private fun recordSynchronizedTransform()
    {
        synchronizedPosition.set(xPos, yPos, zPos)
        synchronizedRotation.set(xRot, yRot, zRot)
    }
}