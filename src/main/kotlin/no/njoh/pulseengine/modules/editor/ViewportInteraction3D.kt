package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.BlendFunction.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RG32I
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ObjectIdRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ObjectOutlineRenderer
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.util.PixelReadResult
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.modules.scene.systems.ConicalLight3D
import no.njoh.pulseengine.modules.scene.systems.Light3D
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.DynamicList
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.scene.entities.Camera3D
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.cameraFacingAxisSigns
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.closestAxisParameter
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.gizmoWorldSize
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.intersectPlane
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.pointToSegmentDistance
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.project
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.projectLine
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.rotateAroundPivot
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.scaleAroundPivot
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.signedAngleDegrees
import no.njoh.pulseengine.modules.editor.SceneEditor3DMath.triangleWinding
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 3D selection, transformation, rotation, scaling, camera and rendering for the scene editor viewport.
 */
class ViewportInteraction3D(
    initialCameraState: CameraState = CameraState.perspective3D()
) : ViewportInteraction {

    var gizmoMode = GizmoMode.MOVE

    private val defaultCameraState = initialCameraState.duplicate().also { it.sanitizePerspective3D() }
    private var cameraState = defaultCameraState.duplicate()

    private var hoveredHandle = Handle.NONE
    private var transformDrag: TransformDrag? = null
    private val pickResult = PixelReadResult()
    private var pickPending = false
    private var pendingPickMode = PickMode.REPLACE
    private var observedSelectionId = Long.MIN_VALUE
    private var cameraDragging = false
    private val orbitPivot = Vector3f()
    private var orbitPivotValid = false

    private val ray = SceneEditor3DMath.Ray()
    private val tmpV0 = Vector3f()
    private val tmpV1 = Vector3f()
    private val tmpV2 = Vector3f()
    private val tmpV3 = Vector3f()
    private val tmpP0 = Vector2f()
    private val tmpP1 = Vector2f()
    private val tmpP2 = Vector2f()
    private val tmpP3 = Vector2f()
    private val tmpMouse = Vector2f()
    private val submittedBounds = Model.Aabb()

    override fun onCreate(engine: PulseEngine, context: ViewportContext)
    {
        engine.gfx.createSurface(
            name = OBJECT_ID_SURFACE,
            zOrder = 100,
            camera = context.camera,
            isVisible = false,
            textureFormat = RG32I,
            textureFilter = NEAREST,
            multisampling = Multisampling.NONE,
            blendFunction = NONE,
            attachments = listOf(COLOR_TEXTURE_0, DEPTH_TEXTURE),
            clearColor = null
        ).addRenderer(ObjectIdRenderer())

        engine.gfx.createSurface(
            name = GIZMO_SURFACE,
            zOrder = -50
        ).addRenderer(ObjectOutlineRenderer(OBJECT_ID_SURFACE))

        updateOrbitPivotFromSelection(context)
    }

    override fun onUpdate(engine: PulseEngine, context: ViewportContext)
    {
        val objectIdRenderer = getObjectIdRenderer(engine)
        objectIdRenderer?.enabled = true

        engine.input.setCursorType(CursorType.ARROW)
        observeSelection(context)
        consumePickResult(engine, context)

        val hover = engine.input.hasHoverFocus(context.focusArea)
        val cameraConsumed = updateCamera(engine, context, hover)
        if (cameraConsumed)
        {
            hoveredHandle = Handle.NONE
            return
        }

        val selected = getSelectedTransformables(context)
        if (selected != null && !supportsMode(selected, gizmoMode))
            gizmoMode = GizmoMode.MOVE

        if (engine.input.wasClicked(Key.W)) gizmoMode = GizmoMode.MOVE
        if (engine.input.wasClicked(Key.E) && (selected == null || supportsMode(selected, GizmoMode.ROTATE))) gizmoMode = GizmoMode.ROTATE
        if (engine.input.wasClicked(Key.R) && (selected == null || supportsMode(selected, GizmoMode.SCALE))) gizmoMode = GizmoMode.SCALE

        hoveredHandle = if (hover && selected != null) hitTestGizmo(engine, context, selected.pivot) else Handle.NONE

        val drag = transformDrag
        if (drag != null)
        {
            if (engine.input.wasClicked(Key.ESCAPE))
            {
                restoreTransform(drag)
                markTransformsChanged(engine, context, drag.targets, drag.mode)
                transformDrag = null
                return
            }

            if (engine.input.isPressed(MouseButton.LEFT))
            {
                updateTransformDrag(engine, context, drag)
            }
            else
            {
                transformDrag = null
            }
            return
        }

        if (!hover || !engine.input.wasClicked(MouseButton.LEFT))
            return

        if (selected != null && hoveredHandle != Handle.NONE && beginTransformDrag(engine, context, selected, hoveredHandle))
            return

        val pickMode = currentPickMode(engine)
        findLightMarkerAtMouse(engine, context)?.let { light ->
            pickPending = false
            applyPickedEntity(engine, context, light, pickMode)
            return
        }

        findCameraMarkerAtMouse(engine, context)?.let { camera ->
            pickPending = false
            applyPickedEntity(engine, context, camera, pickMode)
            return
        }

        if (pickResult.isPending) return
        pendingPickMode = pickMode
        val objectIdSurface = engine.gfx.getSurface(OBJECT_ID_SURFACE)
        if (objectIdSurface != null)
        {
            objectIdSurface.readPixel(engine.input.xMouse.toInt(), engine.input.yMouse.toInt(), dstResult = pickResult)
            pickPending = true
        }
    }

    override fun onRender(engine: PulseEngine, context: ViewportContext)
    {
        val objectIdRenderer = getObjectIdRenderer(engine)
        val outlineRenderer = getSelectionOutlineRenderer(engine)

        if (engine.scene.state != SceneState.STOPPED)
        {
            objectIdRenderer?.enabled = false
            transformDrag = null
            if (cameraDragging)
            {
                cameraDragging = false
                engine.input.setCursorType(CursorType.ARROW)
            }
            return
        }

        val selected = getSelectedTransformables(context)
        selected?.targets?.forEachFast { outlineRenderer?.setSelected(it.entity.id) }

        val selectedIds = selected?.targets?.mapTo(HashSet()) { it.entity.id } ?: emptySet()
        renderLightMarkers(engine, context, selectedIds)
        renderCameraMarkers(engine, context, selectedIds)
        selected?.targets?.forEach { (it.entity as? Light3D)?.let { light -> renderLightInfluence(engine, context, light) } }

        if (selected != null)
            renderGizmo(engine, context, selected.pivot)
    }

    override fun reset(engine: PulseEngine, context: ViewportContext)
    {
        transformDrag = null
        hoveredHandle = Handle.NONE
        pickPending = false
        pendingPickMode = PickMode.REPLACE
        observedSelectionId = Long.MIN_VALUE
        cameraDragging = false
        orbitPivotValid = false
        engine.input.setCursorType(CursorType.ARROW)
        getObjectIdRenderer(engine)?.enabled = false
    }

    override fun onDestroy(engine: PulseEngine, context: ViewportContext)
    {
        engine.input.setCursorType(CursorType.ARROW)
        engine.gfx.deleteSurface(OBJECT_ID_SURFACE)
        engine.gfx.deleteSurface(GIZMO_SURFACE)
    }

    private fun consumePickResult(engine: PulseEngine, context: ViewportContext)
    {
        if (!pickPending || pickResult.isPending || !pickResult.isReady) return
        pickPending = false

        val low = pickResult.redInt
        val high = pickResult.greenInt
        val objectId = if (low == -1 && high == -1) -1L else InstanceBufferObject.decodeObjectId(low, high)

        val entity = engine.scene.getEntity(objectId)
        if (entity != null && entity is Translatable3D && entity.isSet(EDITABLE) && entity.isNot(HIDDEN))
        {
            applyPickedEntity(engine, context, entity, pendingPickMode)
        }
        else if (pendingPickMode == PickMode.REPLACE)
        {
            context.clearSelection()
        }
    }

    private fun getSelectedTransformables(context: ViewportContext): TransformSelection?
    {
        if (context.selection.isEmpty()) return null
        val targets = ArrayList<SelectedTarget>(context.selection.size)
        val pivot = Vector3f()
        for (entity in context.selection)
        {
            val spatial = entity as? Translatable3D ?: return null
            if (entity.isNot(EDITABLE) || entity.isSet(HIDDEN))
                return null
            targets += SelectedTarget(entity, spatial)
            pivot.add(spatial.xPos, spatial.yPos, spatial.zPos)
        }
        pivot.div(targets.size.toFloat())
        return TransformSelection(targets, pivot)
    }

    private fun currentPickMode(engine: PulseEngine) = when
    {
        engine.input.isPressed(Key.LEFT_CONTROL) -> PickMode.TOGGLE
        engine.input.isPressed(Key.LEFT_SHIFT) -> PickMode.ADD
        else -> PickMode.REPLACE
    }

    private fun applyPickedEntity(engine: PulseEngine, context: ViewportContext, entity: SceneEntity, mode: PickMode)
    {
        val selection = when (mode)
        {
            PickMode.REPLACE -> listOf(entity)
            PickMode.ADD -> if (entity in context.selection) context.selection.toList() else context.selection + entity
            PickMode.TOGGLE -> if (entity in context.selection) context.selection - entity else context.selection + entity
        }
        context.selectMultiple(engine, selection)
        observedSelectionId = Long.MIN_VALUE
        getSelectedTransformables(context)?.let {
            orbitPivot.set(it.pivot)
            orbitPivotValid = true
            if (!supportsMode(it, gizmoMode))
                gizmoMode = GizmoMode.MOVE
        }
        if (selection.isEmpty()) orbitPivotValid = false
    }

    private fun findLightMarkerAtMouse(engine: PulseEngine, context: ViewportContext): SceneEntity?
    {
        tmpMouse.set(engine.input.xMouse, engine.input.yMouse)
        var closest: SceneEntity? = null
        var closestDistance = LIGHT_MARKER_HIT_RADIUS
        engine.scene.forEachEntity { entity ->
            val light = entity as? Light3D ?: return@forEachEntity
            if (entity.isNot(EDITABLE) || entity.isSet(HIDDEN))
                return@forEachEntity

            tmpV0.set(light.xPos, light.yPos, light.zPos)
            if (!project(context.camera, tmpV0, engine.window.width, engine.window.height, tmpP0))
                return@forEachEntity

            val distance = tmpMouse.distance(tmpP0)
            if (distance < closestDistance)
            {
                closestDistance = distance
                closest = entity
            }
        }
        return closest
    }

    override fun onEditorActivated(engine: PulseEngine, context: ViewportContext)
    {
        cameraState.sanitizePerspective3D()
        cameraState.loadInto(context.camera, engine.window.width, engine.window.height)
        reset(engine, context)
        updateOrbitPivotFromSelection(context)
    }

    override fun onEditorDeactivated(engine: PulseEngine, context: ViewportContext)
    {
        cameraState.saveFrom(context.camera)
        cameraState.sanitizePerspective3D()
        reset(engine, context)
    }

    override fun resetCamera(engine: PulseEngine, context: ViewportContext)
    {
        cameraState = defaultCameraState.duplicate()
        cameraState.loadInto(context.camera, engine.window.width, engine.window.height)
        reset(engine, context)
        updateOrbitPivotFromSelection(context)
    }

    private fun findCameraMarkerAtMouse(engine: PulseEngine, context: ViewportContext): Camera3D?
    {
        tmpMouse.set(engine.input.xMouse, engine.input.yMouse)
        var closest: Camera3D? = null
        var closestDistance = CAMERA_MARKER_HIT_RADIUS
        engine.scene.forEachEntity { entity ->
            val camera = entity as? Camera3D ?: return@forEachEntity
            if (camera.isNot(EDITABLE) || camera.isSet(HIDDEN))
                return@forEachEntity

            tmpV0.set(camera.xPos, camera.yPos, camera.zPos)
            if (!project(context.camera, tmpV0, engine.window.width, engine.window.height, tmpP0))
                return@forEachEntity

            val distance = tmpMouse.distance(tmpP0)
            if (distance < closestDistance)
            {
                closestDistance = distance
                closest = camera
            }
        }
        return closest
    }

    private fun observeSelection(context: ViewportContext)
    {
        val selected = getSelectedTransformables(context)
        val id = context.selection.fold(1L) { signature, entity -> signature * 31L + entity.id }
        if (id == observedSelectionId) return

        observedSelectionId = id
        pickPending = false
        transformDrag = null
        if (selected != null)
        {
            orbitPivot.set(selected.pivot)
            orbitPivotValid = true
        }
        else orbitPivotValid = false
    }

    private fun updateCamera(engine: PulseEngine, context: ViewportContext, hover: Boolean): Boolean
    {
        val input = engine.input
        val camera = context.camera
        val fly = input.isPressed(MouseButton.RIGHT) && (hover || cameraDragging)
        val orbit = input.isPressed(Key.LEFT_ALT) && input.isPressed(MouseButton.MIDDLE) && (hover || cameraDragging)
        val pan = input.isPressed(MouseButton.MIDDLE) && (hover || cameraDragging) && !orbit
        val dragging = fly || orbit || pan

        if (dragging)
        {
            if (!cameraDragging)
            {
                ensureOrbitPivot(context, camera)
                cameraDragging = true
            }
            input.setCursorType(CursorType.HAND_GRAB)

            if (fly || orbit)
            {
                camera.rotation.y -= input.xdMouse * LOOK_SENSITIVITY
                camera.rotation.x = (camera.rotation.x - input.ydMouse * LOOK_SENSITIVITY).coerceIn(-MAX_PITCH, MAX_PITCH)
            }

            cameraDirections(camera, tmpV0, tmpV1, tmpV2)

            if (fly)
            {
                tmpV3.zero()
                if (input.isPressed(Key.W)) tmpV3.add(tmpV2)
                if (input.isPressed(Key.S)) tmpV3.sub(tmpV2)
                if (input.isPressed(Key.D)) tmpV3.add(tmpV0)
                if (input.isPressed(Key.A)) tmpV3.sub(tmpV0)
                if (input.isPressed(Key.E)) tmpV3.add(0f, 1f, 0f)
                if (input.isPressed(Key.Q)) tmpV3.sub(0f, 1f, 0f)
                if (tmpV3.lengthSquared() > 0f)
                {
                    val multiplier = if (input.isPressed(Key.LEFT_SHIFT)) FLY_FAST_MULTIPLIER else 1f
                    tmpV3.normalize(FLY_SPEED * multiplier * engine.data.deltaTime)
                    camera.position.add(tmpV3)
                    if (orbitPivotValid) orbitPivot.add(tmpV3)
                }
            }
            else if (orbit)
            {
                val distance = max(camera.position.distance(orbitPivot), MIN_ORBIT_DISTANCE)
                camera.position.set(tmpV2).mul(-distance).add(orbitPivot)
            }
            else // Pan
            {
                val distance = max(camera.position.distance(orbitPivot), 1f)
                val unitsPerPixel = 2f * distance * tan(camera.fov.toRadians() * 0.5f) / max(engine.window.height, 1)
                tmpV3.set(tmpV0).mul(-input.xdMouse * unitsPerPixel).add(Vector3f(tmpV1).mul(input.ydMouse * unitsPerPixel))
                camera.position.add(tmpV3)
                orbitPivot.add(tmpV3)
            }

            return true
        }

        if (cameraDragging)
        {
            cameraDragging = false
            input.setCursorType(CursorType.ARROW)
        }

        if (!hover) return false

        if (input.wasClicked(Key.F) && getSelectedTransformables(context) != null)
            frameSelection(engine, context)

        if (input.yScroll != 0f)
        {
            cameraDirections(camera, tmpV0, tmpV1, tmpV2)
            SceneEditor3DMath.scrollTranslation(tmpV2, input.yScroll, SCROLL_MOVE_PER_NOTCH, tmpV3)
            camera.position.add(tmpV3)
            return true
        }

        return false
    }

    private fun ensureOrbitPivot(context: ViewportContext, camera: Camera)
    {
        if (orbitPivotValid) return
        val selected = getSelectedTransformables(context)
        if (selected != null)
        {
            orbitPivot.set(selected.pivot)
        }
        else
        {
            cameraDirections(camera, tmpV0, tmpV1, tmpV2)
            orbitPivot.set(tmpV2).mul(DEFAULT_ORBIT_DISTANCE).add(camera.position)
        }
        orbitPivotValid = true
    }

    private fun updateOrbitPivotFromSelection(context: ViewportContext)
    {
        val selected = getSelectedTransformables(context) ?: return
        orbitPivot.set(selected.pivot)
        orbitPivotValid = true
    }

    private fun frameSelection(engine: PulseEngine, context: ViewportContext)
    {
        val selected = getSelectedTransformables(context) ?: return
        val center = Vector3f(selected.pivot)
        var radius = 1f
        selected.targets.forEach { target ->
            val (targetCenter, targetRadius) = getSelectionBounds(engine, target.entity, target.spatial)
            radius = max(radius, center.distance(targetCenter) + targetRadius)
        }
        orbitPivot.set(center)
        orbitPivotValid = true
        cameraDirections(context.camera, tmpV0, tmpV1, tmpV2)
        val halfFov = max(context.camera.fov.toRadians() * 0.5f, 0.01f)
        val distance = max(radius * 1.35f / tan(halfFov), MIN_ORBIT_DISTANCE)
        context.camera.position.set(tmpV2).mul(-distance).add(center)
    }

    private fun getSelectionBounds(engine: PulseEngine, entity: SceneEntity, spatial: Translatable3D): Pair<Vector3f, Float>
    {
        val scene = engine.gfx.sceneContext.getSubmittedScene()
        var found = false
        
        found = found or includeObjectBounds(scene.opaqueItems,  entity.id, submittedBounds)
        found = found or includeObjectBounds(scene.maskedItems,  entity.id, submittedBounds)
        found = found or includeObjectBounds(scene.blendedItems, entity.id, submittedBounds)

        if (found)
        {
            val center = Vector3f(
                (submittedBounds.xMin + submittedBounds.xMax) * 0.5f,
                (submittedBounds.yMin + submittedBounds.yMax) * 0.5f,
                (submittedBounds.zMin + submittedBounds.zMax) * 0.5f
            )
            val halfWidth = (submittedBounds.xMax - submittedBounds.xMin) * 0.5f
            val halfHeight = (submittedBounds.yMax - submittedBounds.yMin) * 0.5f
            val halfDepth = (submittedBounds.zMax - submittedBounds.zMin) * 0.5f
            return center to max(sqrt(halfWidth * halfWidth + halfHeight * halfHeight + halfDepth * halfDepth), 0.25f)
        }

        if (entity is Light3D)
            return Vector3f(entity.xPos, entity.yPos, entity.zPos) to max(entity.radius, 1f)

        return Vector3f(spatial.xPos, spatial.yPos, spatial.zPos) to 1f
    }

    private fun includeObjectBounds(items: DynamicList<RenderItem>, objectId: Long, outBounds: Model.Aabb): Boolean
    {
        var found = false
        items.forEach { item ->
  
            if (item.objectId != objectId) return@forEach

            val bounds = item.cullingBounds ?: item.mesh.localBounds
            val transform = item.transform

            val xCenter = (bounds.xMin + bounds.xMax) * 0.5f
            val yCenter = (bounds.yMin + bounds.yMax) * 0.5f
            val zCenter = (bounds.zMin + bounds.zMax) * 0.5f
            val xHalf   = (bounds.xMax - bounds.xMin) * 0.5f
            val yHalf   = (bounds.yMax - bounds.yMin) * 0.5f
            val zHalf   = (bounds.zMax - bounds.zMin) * 0.5f

            val xWorldCenter = transform.m00 * xCenter + transform.m10 * yCenter + transform.m20 * zCenter + transform.m30
            val yWorldCenter = transform.m01 * xCenter + transform.m11 * yCenter + transform.m21 * zCenter + transform.m31
            val zWorldCenter = transform.m02 * xCenter + transform.m12 * yCenter + transform.m22 * zCenter + transform.m32
            val xWorldHalf   = abs(transform.m00) * xHalf + abs(transform.m10) * yHalf + abs(transform.m20) * zHalf
            val yWorldHalf   = abs(transform.m01) * xHalf + abs(transform.m11) * yHalf + abs(transform.m21) * zHalf
            val zWorldHalf   = abs(transform.m02) * xHalf + abs(transform.m12) * yHalf + abs(transform.m22) * zHalf
            
            val xMin = xWorldCenter - xWorldHalf
            val yMin = yWorldCenter - yWorldHalf
            val zMin = zWorldCenter - zWorldHalf
            val xMax = xWorldCenter + xWorldHalf
            val yMax = yWorldCenter + yWorldHalf
            val zMax = zWorldCenter + zWorldHalf

            if (!found)
            {
                outBounds.set(xMin, yMin, zMin, xMax, yMax, zMax)
                found = true
            }
            else
            {
                outBounds.set(
                    min(outBounds.xMin, xMin),
                    min(outBounds.yMin, yMin),
                    min(outBounds.zMin, zMin),
                    max(outBounds.xMax, xMax),
                    max(outBounds.yMax, yMax),
                    max(outBounds.zMax, zMax)
                )
            }
        }

        return found
    }

    private fun beginTransformDrag(engine: PulseEngine, context: ViewportContext, selection: TransformSelection, handle: Handle): Boolean
    {
        if (!supportsMode(selection, gizmoMode)) return false
        val pivot = Vector3f(selection.pivot)
        val axisSigns = cameraFacingAxisSigns(context.camera.invViewMatrix.getTranslation(Vector3f()), pivot, Vector3f())
        val size = gizmoWorldSize(context.camera, pivot, engine.window.height)
        val drag = TransformDrag(
            targets = selection.targets.map { TransformTarget(it.entity, it.spatial, snapshot(it.spatial)) },
            mode = gizmoMode,
            handle = handle,
            pivot = pivot,
            axisSigns = axisSigns,
            gizmoSize = size,
            startMouse = Vector2f(engine.input.xMouse, engine.input.yMouse)
        )

        if (!SceneEditor3DMath.createRay(context.camera, engine.input.xMouse, engine.input.yMouse, engine.window.width, engine.window.height, ray))
            return false

        when
        {
            handle == Handle.UNIFORM -> Unit
            isPlaneHandle(handle) ->
            {
                if (!intersectPlane(ray, pivot, planeNormal(handle, tmpV0), drag.startPlanePoint)) return false
            }
            gizmoMode == GizmoMode.ROTATE ->
            {
                val axis = axis(handle, tmpV0)
                if (!intersectPlane(ray, pivot, axis, drag.startVector)) return false
                drag.startVector.sub(pivot)
                if (drag.startVector.lengthSquared() < 1e-6f) return false
                drag.startVector.normalize()
            }
            else ->
            {
                drag.startParameter = closestAxisParameter(ray, pivot, facingAxis(handle, axisSigns, tmpV0)) ?: return false
            }
        }

        transformDrag = drag
        return true
    }

    private fun updateTransformDrag(engine: PulseEngine, context: ViewportContext, drag: TransformDrag)
    {
        if (!SceneEditor3DMath.createRay(context.camera, engine.input.xMouse, engine.input.yMouse, engine.window.width, engine.window.height, ray))
            return

        when (drag.mode)
        {
            GizmoMode.MOVE ->
            {
                val translation = Vector3f()
                if (isPlaneHandle(drag.handle))
                {
                    if (!intersectPlane(ray, drag.pivot, planeNormal(drag.handle, tmpV0), tmpV1)) return
                    tmpV1.sub(drag.startPlanePoint)
                    if (planeContainsX(drag.handle)) translation.x = tmpV1.x
                    if (planeContainsY(drag.handle)) translation.y = tmpV1.y
                    if (planeContainsZ(drag.handle)) translation.z = tmpV1.z
                }
                else
                {
                    val axis = facingAxis(drag.handle, drag.axisSigns, tmpV0)
                    val parameter = closestAxisParameter(ray, drag.pivot, axis) ?: return
                    val delta = parameter - drag.startParameter
                    translation.set(axis).mul(delta)
                }
                drag.targets.forEach { target ->
                    target.spatial.xPos = target.snapshot.position.x + translation.x
                    target.spatial.yPos = target.snapshot.position.y + translation.y
                    target.spatial.zPos = target.snapshot.position.z + translation.z
                }
            }
            GizmoMode.ROTATE ->
            {
                val axis = axis(drag.handle, tmpV0)
                if (!intersectPlane(ray, drag.pivot, axis, tmpV1))
                    return
                tmpV1.sub(drag.pivot)
                if (tmpV1.lengthSquared() < 1e-6f)
                    return
                tmpV1.normalize()

                val angle = signedAngleDegrees(drag.startVector, tmpV1, axis)
                val deltaRotation = Quaternionf().fromAxisAngleRad(axis, angle.toRadians())

                drag.targets.forEach { target ->
                    val position = rotateAroundPivot(target.snapshot.position, drag.pivot, axis, angle, Vector3f())
                    target.spatial.xPos = position.x
                    target.spatial.yPos = position.y
                    target.spatial.zPos = position.z

                    (target.spatial as? Rotatable3D)?.let { rotatable ->
                        val start = Quaternionf().rotationXYZ(
                            target.snapshot.rotation.x.toRadians(),
                            target.snapshot.rotation.y.toRadians(),
                            target.snapshot.rotation.z.toRadians()
                        )
                        val euler = Quaternionf(deltaRotation).mul(start).getEulerAnglesXYZ(Vector3f())
                        rotatable.xRot = Math.toDegrees(euler.x.toDouble()).toFloat()
                        rotatable.yRot = Math.toDegrees(euler.y.toDouble()).toFloat()
                        rotatable.zRot = Math.toDegrees(euler.z.toDouble()).toFloat()
                    }
                }
            }
            GizmoMode.SCALE ->
            {
                val factors = Vector3f(1f)
                if (drag.handle == Handle.UNIFORM)
                {
                    val factor = exp((drag.startMouse.y - engine.input.yMouse) * 0.01f)
                    factors.set(factor)
                }
                else if (isPlaneHandle(drag.handle))
                {
                    if (!intersectPlane(ray, drag.pivot, planeNormal(drag.handle, tmpV0), tmpV1)) return
                    tmpV1.sub(drag.startPlanePoint)
                    val inverseSize = 1f / max(drag.gizmoSize, 0.001f)
                    if (planeContainsX(drag.handle)) factors.x += tmpV1.x * drag.axisSigns.x * inverseSize
                    if (planeContainsY(drag.handle)) factors.y += tmpV1.y * drag.axisSigns.y * inverseSize
                    if (planeContainsZ(drag.handle)) factors.z += tmpV1.z * drag.axisSigns.z * inverseSize
                }
                else
                {
                    val axis = facingAxis(drag.handle, drag.axisSigns, tmpV0)
                    val parameter = closestAxisParameter(ray, drag.pivot, axis) ?: return
                    val factor = 1f + (parameter - drag.startParameter) / max(drag.gizmoSize, 0.001f)
                    when (drag.handle)
                    {
                        Handle.X -> factors.x = factor
                        Handle.Y -> factors.y = factor
                        Handle.Z -> factors.z = factor
                        else -> Unit
                    }
                }
                drag.targets.forEach { target ->
                    val position = scaleAroundPivot(target.snapshot.position, drag.pivot, factors, Vector3f())
                    target.spatial.xPos = position.x
                    target.spatial.yPos = position.y
                    target.spatial.zPos = position.z
                    val scalable = target.spatial as Spatial3D
                    scalable.xScale = SceneEditor3DMath.clampScale(target.snapshot.scale.x * factors.x)
                    scalable.yScale = SceneEditor3DMath.clampScale(target.snapshot.scale.y * factors.y)
                    scalable.zScale = SceneEditor3DMath.clampScale(target.snapshot.scale.z * factors.z)
                }
            }
        }

        markTransformsChanged(engine, context, drag.targets, drag.mode)
    }

    private fun markTransformsChanged(engine: PulseEngine, context: ViewportContext, targets: List<TransformTarget>, mode: GizmoMode)
    {
        targets.forEach { target ->
            val entity = target.entity
            val spatial = target.spatial
            when (mode)
            {
                GizmoMode.MOVE ->
                {
                    entity.set(POSITION_UPDATED)
                    context.notifyTransformChanged(engine, entity, spatial::xPos.name, spatial::yPos.name, spatial::zPos.name)
                }
                GizmoMode.ROTATE ->
                {
                    entity.set(POSITION_UPDATED)
                    val rotatable = spatial as? Rotatable3D
                    if (rotatable != null)
                    {
                        entity.set(ROTATION_UPDATED)
                        context.notifyTransformChanged(
                            engine, entity,
                            spatial::xPos.name, spatial::yPos.name, spatial::zPos.name,
                            rotatable::xRot.name, rotatable::yRot.name, rotatable::zRot.name
                        )
                    }
                    else context.notifyTransformChanged(engine, entity, spatial::xPos.name, spatial::yPos.name, spatial::zPos.name)
                }
                GizmoMode.SCALE ->
                {
                    val scalable = spatial as Spatial3D
                    entity.set(POSITION_UPDATED)
                    entity.set(SIZE_UPDATED)
                    context.notifyTransformChanged(
                        engine, entity,
                        spatial::xPos.name, spatial::yPos.name, spatial::zPos.name,
                        scalable::xScale.name, scalable::yScale.name, scalable::zScale.name
                    )
                }
            }
        }
    }

    private fun hitTestGizmo(engine: PulseEngine, context: ViewportContext, pivot: Vector3f): Handle
    {
        val size = gizmoWorldSize(context.camera, pivot, engine.window.height)
        val axisSigns = cameraFacingAxisSigns(context.camera.invViewMatrix.getTranslation(Vector3f()), pivot, Vector3f())
        tmpMouse.set(engine.input.xMouse, engine.input.yMouse)

        if (gizmoMode == GizmoMode.SCALE && project(context.camera, pivot, engine.window.width, engine.window.height, tmpP0))
        {
            if (tmpMouse.distance(tmpP0) <= CENTER_HANDLE_RADIUS)
                return Handle.UNIFORM
        }

        if (gizmoMode != GizmoMode.ROTATE)
        {
            var bestPlane = Handle.NONE
            var bestArea = -1f
            for (handle in PLANE_HANDLES)
            {
                val area = planeHandleHitArea(context.camera, pivot, size, handle, axisSigns, engine.window.width, engine.window.height, tmpMouse, gizmoMode)
                if (area > bestArea)
                {
                    bestArea = area
                    bestPlane = handle
                }
            }
            if (bestPlane != Handle.NONE)
                return bestPlane
        }

        var closest = Handle.NONE
        var closestDistance = HIT_TOLERANCE
        for (handle in AXIS_HANDLES)
        {
            val distance = if (gizmoMode == GizmoMode.ROTATE)
                ringDistance(context.camera, pivot, size, handle, engine.window.width, engine.window.height, tmpMouse)
            else
                axisDistance(context.camera, pivot, size, handle, axisSigns, engine.window.width, engine.window.height, tmpMouse)
            if (distance < closestDistance)
            {
                closestDistance = distance
                closest = handle
            }
        }
        return closest
    }

    private fun axisDistance(camera: Camera, pivot: Vector3f, size: Float, handle: Handle, axisSigns: Vector3f, width: Int, height: Int, mouse: Vector2f): Float
    {
        if (!project(camera, pivot, width, height, tmpP0))
            return Float.MAX_VALUE

        tmpV1.set(facingAxis(handle, axisSigns, tmpV2)).mul(size).add(pivot)

        if (!project(camera, tmpV1, width, height, tmpP1))
            return Float.MAX_VALUE

        return pointToSegmentDistance(mouse, tmpP0, tmpP1)
    }

    private fun ringDistance(camera: Camera, pivot: Vector3f, size: Float, handle: Handle, width: Int, height: Int, mouse: Vector2f): Float
    {
        var minimum = Float.MAX_VALUE
        var previousValid = false
        val previous = Vector2f()
        val current = Vector2f()
        for (i in 0..RING_SEGMENTS)
        {
            ringPoint(handle, i.toFloat() / RING_SEGMENTS * Math.PI.toFloat() * 2f, size, tmpV1).add(pivot)
            val valid = project(camera, tmpV1, width, height, current)
            if (valid && previousValid)
                minimum = minOf(minimum, pointToSegmentDistance(mouse, previous, current))
            previous.set(current)
            previousValid = valid
        }
        return minimum
    }

    private fun renderLightMarkers(engine: PulseEngine, context: ViewportContext, selectedIds: Set<Long>)
    {
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        val width = surface.config.width
        val height = surface.config.height
        engine.scene.forEachEntity { entity ->
            val light = entity as? Light3D ?: return@forEachEntity
            if (entity.isNot(EDITABLE) || entity.isSet(HIDDEN))
                return@forEachEntity

            tmpV0.set(light.xPos, light.yPos, light.zPos)
            if (!project(context.camera, tmpV0, width, height, tmpP0))
                return@forEachEntity

            surface.setDrawColor(if (entity.id in selectedIds) ACTIVE_COLOR else LIGHT_MARKER_COLOR)
            drawScreenCircle(surface, tmpP0.x, tmpP0.y, LIGHT_MARKER_RADIUS)
            surface.drawLine(tmpP0.x - LIGHT_MARKER_CROSS_SIZE, tmpP0.y, tmpP0.x + LIGHT_MARKER_CROSS_SIZE, tmpP0.y)
            surface.drawLine(tmpP0.x, tmpP0.y - LIGHT_MARKER_CROSS_SIZE, tmpP0.x, tmpP0.y + LIGHT_MARKER_CROSS_SIZE)

            if (light is ConicalLight3D)
            {
                val markerLength = gizmoWorldSize(context.camera, tmpV0, height) * 0.45f
                light.getDirection(tmpV1).mul(markerLength).add(tmpV0)
                if (project(context.camera, tmpV1, width, height, tmpP1))
                    surface.drawLine(tmpP0.x, tmpP0.y, tmpP1.x, tmpP1.y)
            }
        }
    }

    private fun renderCameraMarkers(engine: PulseEngine, context: ViewportContext, selectedIds: Set<Long>)
    {
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        val width = surface.config.width
        val height = surface.config.height
        engine.scene.forEachEntity { entity ->
            val camera = entity as? Camera3D ?: return@forEachEntity
            if (camera.isNot(EDITABLE) || camera.isSet(HIDDEN))
                return@forEachEntity

            tmpV0.set(camera.xPos, camera.yPos, camera.zPos)
            val isSelected = camera.id in selectedIds
            surface.setDrawColor(if (isSelected) ACTIVE_COLOR else CAMERA_MARKER_COLOR)
            cameraDirections(camera, tmpV1, tmpV2, tmpV3)
            renderCameraFrustum(surface, context.camera, camera, tmpV0, tmpV1, tmpV2, tmpV3, width, height, isSelected)

            if (project(context.camera, tmpV0, width, height, tmpP0))
            {
                drawScreenCircle(surface, tmpP0.x, tmpP0.y, CAMERA_MARKER_RADIUS)
                tmpV3.mul(gizmoWorldSize(context.camera, tmpV0, height) * 0.6f).add(tmpV0)
                if (projectLine(context.camera, tmpV0, tmpV3, width, height, tmpP0, tmpP1))
                    surface.drawLine(tmpP0.x, tmpP0.y, tmpP1.x, tmpP1.y)
            }
        }
    }

    private fun renderCameraFrustum(
        surface: no.njoh.pulseengine.core.graphics.surface.Surface,
        editorCamera: Camera,
        camera: Camera3D,
        position: Vector3f,
        right: Vector3f,
        up: Vector3f,
        forward: Vector3f,
        width: Int,
        height: Int,
        isSelected: Boolean
    ) {
        if (width <= 0 || height <= 0) return

        val nearDistance = camera.nearPlane.coerceAtLeast(0.001f)
        val configuredFarDistance = camera.farPlane.coerceAtLeast(nearDistance + 0.001f)
        val previewDistance = gizmoWorldSize(editorCamera, position, height, CAMERA_PREVIEW_PIXEL_LENGTH)
            .coerceIn(nearDistance + 0.001f, configuredFarDistance)
        val farDistance = if (isSelected) configuredFarDistance else previewDistance
        val halfFov = camera.fov.coerceIn(1f, 179f).toRadians() * 0.5f
        val aspectRatio = width.toFloat() / height.toFloat()
        val nearCorners = Array(4) { Vector3f() }
        val farCorners = Array(4) { Vector3f() }

        setFrustumCorners(position, right, up, forward, nearDistance, halfFov, aspectRatio, nearCorners)
        setFrustumCorners(position, right, up, forward, farDistance, halfFov, aspectRatio, farCorners)

        for (i in 0..3)
        {
            val next = (i + 1) % 4
            drawProjectedLine(surface, editorCamera, nearCorners[i], nearCorners[next], width, height, clipDepth = false)
            drawProjectedLine(surface, editorCamera, position, farCorners[i], width, height, clipDepth = false)
        }

        for (i in 0..3)
            drawProjectedLine(surface, editorCamera, farCorners[i], farCorners[(i + 1) % 4], width, height, clipDepth = false)
    }

    private fun setFrustumCorners(
        position: Vector3f,
        right: Vector3f,
        up: Vector3f,
        forward: Vector3f,
        distance: Float,
        halfFov: Float,
        aspectRatio: Float,
        corners: Array<Vector3f>
    ) {
        val halfHeight = tan(halfFov) * distance
        val halfWidth = halfHeight * aspectRatio
        setFrustumCorner(corners[0], position, right, up, forward, distance, -halfWidth, halfHeight)
        setFrustumCorner(corners[1], position, right, up, forward, distance, halfWidth, halfHeight)
        setFrustumCorner(corners[2], position, right, up, forward, distance, halfWidth, -halfHeight)
        setFrustumCorner(corners[3], position, right, up, forward, distance, -halfWidth, -halfHeight)
    }

    private fun setFrustumCorner(
        out: Vector3f,
        position: Vector3f,
        right: Vector3f,
        up: Vector3f,
        forward: Vector3f,
        distance: Float,
        horizontalOffset: Float,
        verticalOffset: Float
    ) {
        out.set(
            position.x + forward.x * distance + right.x * horizontalOffset + up.x * verticalOffset,
            position.y + forward.y * distance + right.y * horizontalOffset + up.y * verticalOffset,
            position.z + forward.z * distance + right.z * horizontalOffset + up.z * verticalOffset
        )
    }

    private fun renderLightInfluence(engine: PulseEngine, context: ViewportContext, light: Light3D)
    {
        val radius = light.radius.coerceAtLeast(0f)
        if (radius <= 0f) return

        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        val camera = context.camera
        val width = surface.config.width
        val height = surface.config.height
        val origin = Vector3f(light.xPos, light.yPos, light.zPos)
        surface.setDrawColor(LIGHT_VOLUME_COLOR)

        if (light !is ConicalLight3D)
        {
            drawProjectedCircle(surface, camera, origin, WORLD_X, WORLD_Y, radius, width, height)
            drawProjectedCircle(surface, camera, origin, WORLD_X, WORLD_Z, radius, width, height)
            drawProjectedCircle(surface, camera, origin, WORLD_Y, WORLD_Z, radius, width, height)
            return
        }

        val forward = light.getDirection(Vector3f())
        val helper = if (abs(forward.y) < 0.99f) Vector3f(WORLD_Y) else Vector3f(WORLD_X)
        val right = forward.cross(helper, Vector3f()).normalize()
        val up = right.cross(forward, Vector3f()).normalize()
        val baseCenter = Vector3f(forward).mul(radius).add(origin)
        val baseRadius = tan(light.outerConeAngle.coerceIn(0f, MAX_CONE_ANGLE).toRadians()) * radius

        drawProjectedCircle(surface, camera, baseCenter, right, up, baseRadius, width, height)
        drawProjectedLine(surface, camera, origin, baseCenter, width, height)
        for (i in 0 until LIGHT_CONE_SIDE_COUNT)
        {
            val angle = i.toFloat() / LIGHT_CONE_SIDE_COUNT * Math.PI.toFloat() * 2f
            val endpoint = Vector3f(baseCenter)
                .add(Vector3f(right).mul(cos(angle) * baseRadius))
                .add(Vector3f(up).mul(sin(angle) * baseRadius))
            drawProjectedLine(surface, camera, origin, endpoint, width, height)
        }
    }

    private fun drawScreenCircle(surface: no.njoh.pulseengine.core.graphics.surface.Surface, x: Float, y: Float, radius: Float)
    {
        var xPrevious = x + radius
        var yPrevious = y
        for (i in 1..LIGHT_MARKER_SEGMENTS)
        {
            val angle = i.toFloat() / LIGHT_MARKER_SEGMENTS * Math.PI.toFloat() * 2f
            val xCurrent = x + cos(angle) * radius
            val yCurrent = y + sin(angle) * radius
            surface.drawLine(xPrevious, yPrevious, xCurrent, yCurrent)
            xPrevious = xCurrent
            yPrevious = yCurrent
        }
    }

    private fun drawProjectedCircle(
        surface: no.njoh.pulseengine.core.graphics.surface.Surface,
        camera: Camera,
        center: Vector3f,
        axisA: Vector3f,
        axisB: Vector3f,
        radius: Float,
        width: Int,
        height: Int
    ) {
        val world = Vector3f()
        val offsetA = Vector3f()
        val offsetB = Vector3f()
        val previous = Vector2f()
        val current = Vector2f()
        var previousValid = false
        for (i in 0..LIGHT_VOLUME_SEGMENTS)
        {
            val angle = i.toFloat() / LIGHT_VOLUME_SEGMENTS * Math.PI.toFloat() * 2f
            world.set(center)
                .add(offsetA.set(axisA).mul(cos(angle) * radius))
                .add(offsetB.set(axisB).mul(sin(angle) * radius))
            val valid = project(camera, world, width, height, current)
            if (valid && previousValid)
                surface.drawLine(previous.x, previous.y, current.x, current.y)
            previous.set(current)
            previousValid = valid
        }
    }

    private fun drawProjectedLine(
        surface: no.njoh.pulseengine.core.graphics.surface.Surface,
        camera: Camera,
        start: Vector3f,
        end: Vector3f,
        width: Int,
        height: Int,
        clipDepth: Boolean = true
    ) {
        if (projectLine(camera, start, end, width, height, tmpP0, tmpP1, clipDepth))
            surface.drawLine(tmpP0.x, tmpP0.y, tmpP1.x, tmpP1.y)
    }

    private fun renderGizmo(engine: PulseEngine, context: ViewportContext, pivot: Vector3f)
    {
        val camera = context.camera
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        val width = surface.config.width
        val height = surface.config.height
        val size = gizmoWorldSize(camera, pivot, height)
        val axisSigns = cameraFacingAxisSigns(camera.invViewMatrix.getTranslation(Vector3f()), pivot, Vector3f())

        if (gizmoMode != GizmoMode.ROTATE)
        {
            for (handle in PLANE_HANDLES)
            {
                val color = if (handle == activeHandle() || handle == hoveredHandle) ACTIVE_PLANE_COLOR else planeColor(handle)
                surface.setDrawColor(color)
                if (gizmoMode == GizmoMode.MOVE)
                    renderPlaneSquare(surface, camera, pivot, size, handle, axisSigns, width, height)
                else
                    renderPlaneTriangle(surface, camera, pivot, size, handle, axisSigns, width, height)
            }
        }

        if (gizmoMode == GizmoMode.SCALE && project(camera, pivot, width, height, tmpP0))
        {
            surface.setDrawColor(if (activeHandle() == Handle.UNIFORM || hoveredHandle == Handle.UNIFORM) ACTIVE_COLOR else CENTER_COLOR)
            surface.drawQuad(tmpP0.x - 5f, tmpP0.y - 5f, 10f, 10f)
        }

        for (handle in AXIS_HANDLES)
        {
            val color = if (handle == activeHandle() || handle == hoveredHandle) ACTIVE_COLOR else color(handle)
            surface.setDrawColor(color)
            if (gizmoMode == GizmoMode.ROTATE)
                renderRing(surface, camera, pivot, size, handle, width, height)
            else
                renderAxis(surface, camera, pivot, size, handle, axisSigns, width, height)
        }
    }

    private fun renderAxis(surface: no.njoh.pulseengine.core.graphics.surface.Surface, camera: Camera, pivot: Vector3f, size: Float, handle: Handle, axisSigns: Vector3f, width: Int, height: Int)
    {
        if (!project(camera, pivot, width, height, tmpP0)) return
        tmpV1.set(facingAxis(handle, axisSigns, tmpV2)).mul(size).add(pivot)
        if (!project(camera, tmpV1, width, height, tmpP1)) return
        surface.drawLine(tmpP0.x, tmpP0.y, tmpP1.x, tmpP1.y)
        val endpointSize = if (gizmoMode == GizmoMode.SCALE) 8f else 6f
        surface.drawQuad(tmpP1.x - endpointSize * 0.5f, tmpP1.y - endpointSize * 0.5f, endpointSize, endpointSize)
    }

    private fun renderRing(surface: no.njoh.pulseengine.core.graphics.surface.Surface, camera: Camera, pivot: Vector3f, size: Float, handle: Handle, width: Int, height: Int)
    {
        var previousValid = false
        val previous = Vector2f()
        val current = Vector2f()
        for (i in 0..RING_SEGMENTS)
        {
            ringPoint(handle, i.toFloat() / RING_SEGMENTS * Math.PI.toFloat() * 2f, size, tmpV1).add(pivot)
            val valid = project(camera, tmpV1, width, height, current)
            if (valid && previousValid)
                surface.drawLine(previous.x, previous.y, current.x, current.y)
            previous.set(current)
            previousValid = valid
        }
    }

    private fun planeHandleHitArea(
        camera: Camera,
        pivot: Vector3f,
        size: Float,
        handle: Handle,
        axisSigns: Vector3f,
        width: Int,
        height: Int,
        mouse: Vector2f,
        mode: GizmoMode
    ): Float {
        if (mode == GizmoMode.MOVE)
        {
            if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MIN, PLANE_SQUARE_MIN, width, height, tmpP0))
                return -1f
            if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MAX, PLANE_SQUARE_MIN, width, height, tmpP1))
                return -1f
            if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MAX, PLANE_SQUARE_MAX, width, height, tmpP2))
                return -1f
            if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MIN, PLANE_SQUARE_MAX, width, height, tmpP3))
                return -1f
            val isInside = SceneEditor3DMath.pointInQuad(mouse, tmpP0, tmpP1, tmpP2, tmpP3)
            if (!isInside && SceneEditor3DMath.distanceToQuadEdges(mouse, tmpP0, tmpP1, tmpP2, tmpP3) > PLANE_HIT_PADDING)
                return -1f
            val area = SceneEditor3DMath.triangleArea(tmpP0, tmpP1, tmpP2) + SceneEditor3DMath.triangleArea(tmpP0, tmpP2, tmpP3)
            return max(area, MIN_PLANE_HIT_SCORE)
        }

        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_SIZE, PLANE_TRIANGLE_INNER, width, height, tmpP0))
            return -1f
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_INNER, PLANE_TRIANGLE_SIZE, width, height, tmpP1))
            return -1f
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_INNER, PLANE_TRIANGLE_INNER, width, height, tmpP2))
            return -1f
        val isInside = SceneEditor3DMath.pointInTriangle(mouse, tmpP0, tmpP1, tmpP2)
        if (!isInside && SceneEditor3DMath.distanceToTriangleEdges(mouse, tmpP0, tmpP1, tmpP2) > PLANE_HIT_PADDING)
            return -1f
        return max(SceneEditor3DMath.triangleArea(tmpP0, tmpP1, tmpP2), MIN_PLANE_HIT_SCORE)
    }

    private fun renderPlaneSquare(surface: no.njoh.pulseengine.core.graphics.surface.Surface, camera: Camera, pivot: Vector3f, size: Float, handle: Handle, axisSigns: Vector3f, width: Int, height: Int)
    {
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MIN, PLANE_SQUARE_MIN, width, height, tmpP0))
            return
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MAX, PLANE_SQUARE_MIN, width, height, tmpP1))
            return
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MAX, PLANE_SQUARE_MAX, width, height, tmpP2))
            return
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_SQUARE_MIN, PLANE_SQUARE_MAX, width, height, tmpP3))
            return
        if (triangleWinding(tmpP0, tmpP1, tmpP2) >= 0f)
        {
            surface.drawQuadVertex(tmpP0.x, tmpP0.y)
            surface.drawQuadVertex(tmpP1.x, tmpP1.y)
            surface.drawQuadVertex(tmpP2.x, tmpP2.y)
            surface.drawQuadVertex(tmpP3.x, tmpP3.y)
        }
        else
        {
            surface.drawQuadVertex(tmpP0.x, tmpP0.y)
            surface.drawQuadVertex(tmpP3.x, tmpP3.y)
            surface.drawQuadVertex(tmpP2.x, tmpP2.y)
            surface.drawQuadVertex(tmpP1.x, tmpP1.y)
        }
    }

    private fun renderPlaneTriangle(surface: no.njoh.pulseengine.core.graphics.surface.Surface, camera: Camera, pivot: Vector3f, size: Float, handle: Handle, axisSigns: Vector3f, width: Int, height: Int)
    {
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_SIZE, PLANE_TRIANGLE_INNER, width, height, tmpP0))
            return
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_INNER, PLANE_TRIANGLE_SIZE, width, height, tmpP1))
            return
        if (!projectPlanePoint(camera, pivot, size, handle, axisSigns, PLANE_TRIANGLE_INNER, PLANE_TRIANGLE_INNER, width, height, tmpP2))
            return
        surface.drawQuadVertex(tmpP0.x, tmpP0.y)
        if (triangleWinding(tmpP0, tmpP1, tmpP2) >= 0f)
        {
            surface.drawQuadVertex(tmpP1.x, tmpP1.y)
            surface.drawQuadVertex(tmpP2.x, tmpP2.y)
        }
        else
        {
            surface.drawQuadVertex(tmpP2.x, tmpP2.y)
            surface.drawQuadVertex(tmpP1.x, tmpP1.y)
        }
        surface.drawQuadVertex(tmpP0.x, tmpP0.y)
    }

    private fun projectPlanePoint(camera: Camera, pivot: Vector3f, size: Float, handle: Handle, axisSigns: Vector3f, aAmount: Float, bAmount: Float, width: Int, height: Int, out: Vector2f): Boolean
    {
        val a = planeAxisA(handle, axisSigns, tmpV1)
        val b = planeAxisB(handle, axisSigns, tmpV2)
        tmpV3.set(pivot).add(a.mul(size * aAmount)).add(b.mul(size * bAmount))
        return project(camera, tmpV3, width, height, out)
    }

    private fun snapshot(spatial: Translatable3D): TransformSnapshot
    {
        val rotatable = spatial as? Rotatable3D
        val scalable = spatial as? Spatial3D
        return TransformSnapshot(
            Vector3f(spatial.xPos, spatial.yPos, spatial.zPos),
            Vector3f(rotatable?.xRot ?: 0f, rotatable?.yRot ?: 0f, rotatable?.zRot ?: 0f),
            Vector3f(scalable?.xScale ?: 1f, scalable?.yScale ?: 1f, scalable?.zScale ?: 1f)
        )
    }

    private fun restoreTransform(drag: TransformDrag)
    {
        drag.targets.forEach { target ->
            target.spatial.xPos = target.snapshot.position.x
            target.spatial.yPos = target.snapshot.position.y
            target.spatial.zPos = target.snapshot.position.z
            (target.spatial as? Rotatable3D)?.let {
                it.xRot = target.snapshot.rotation.x
                it.yRot = target.snapshot.rotation.y
                it.zRot = target.snapshot.rotation.z
            }
            (target.spatial as? Spatial3D)?.let {
                it.xScale = target.snapshot.scale.x
                it.yScale = target.snapshot.scale.y
                it.zScale = target.snapshot.scale.z
            }
        }
    }

    private fun supportsMode(selection: TransformSelection, mode: GizmoMode) = when (mode)
    {
        GizmoMode.MOVE -> true
        GizmoMode.ROTATE -> selection.targets.size > 1 || selection.targets.single().spatial is Rotatable3D
        GizmoMode.SCALE -> selection.targets.all { it.spatial is Spatial3D }
    }

    private fun activeHandle() = transformDrag?.handle ?: Handle.NONE

    private fun axis(handle: Handle, out: Vector3f): Vector3f = when (handle)
    {
        Handle.X -> out.set(1f, 0f, 0f)
        Handle.Y -> out.set(0f, 1f, 0f)
        Handle.Z -> out.set(0f, 0f, 1f)
        else -> out.zero()
    }

    private fun facingAxis(handle: Handle, signs: Vector3f, out: Vector3f): Vector3f = when (handle)
    {
        Handle.X -> out.set(signs.x, 0f, 0f)
        Handle.Y -> out.set(0f, signs.y, 0f)
        Handle.Z -> out.set(0f, 0f, signs.z)
        else -> out.zero()
    }

    private fun planeAxisA(handle: Handle, signs: Vector3f, out: Vector3f): Vector3f = when (handle)
    {
        Handle.XY, Handle.XZ -> out.set(signs.x, 0f, 0f)
        Handle.YZ -> out.set(0f, signs.y, 0f)
        else -> out.zero()
    }

    private fun planeAxisB(handle: Handle, signs: Vector3f, out: Vector3f): Vector3f = when (handle)
    {
        Handle.XY -> out.set(0f, signs.y, 0f)
        Handle.XZ, Handle.YZ -> out.set(0f, 0f, signs.z)
        else -> out.zero()
    }

    private fun planeNormal(handle: Handle, out: Vector3f): Vector3f = when (handle)
    {
        Handle.XY -> out.set(0f, 0f, 1f)
        Handle.XZ -> out.set(0f, 1f, 0f)
        Handle.YZ -> out.set(1f, 0f, 0f)
        else -> out.zero()
    }

    private fun isPlaneHandle(handle: Handle) = handle == Handle.XY || handle == Handle.XZ || handle == Handle.YZ

    private fun planeContainsX(handle: Handle) = handle == Handle.XY || handle == Handle.XZ
    private fun planeContainsY(handle: Handle) = handle == Handle.XY || handle == Handle.YZ
    private fun planeContainsZ(handle: Handle) = handle == Handle.XZ || handle == Handle.YZ

    private fun ringPoint(handle: Handle, angle: Float, size: Float, out: Vector3f): Vector3f
    {
        val c = cos(angle) * size
        val s = sin(angle) * size
        return when (handle)
        {
            Handle.X -> out.set(0f, c, s)
            Handle.Y -> out.set(c, 0f, s)
            Handle.Z -> out.set(c, s, 0f)
            else -> out.zero()
        }
    }

    private fun color(handle: Handle) = when (handle)
    {
        Handle.X -> X_COLOR
        Handle.Y -> Y_COLOR
        Handle.Z -> Z_COLOR
        else -> CENTER_COLOR
    }

    private fun planeColor(handle: Handle) = when (handle)
    {
        Handle.XY -> Z_COLOR
        Handle.XZ -> Y_COLOR
        Handle.YZ -> X_COLOR
        else -> CENTER_COLOR
    }

    private fun cameraDirections(camera: Camera, right: Vector3f, up: Vector3f, forward: Vector3f)
    {
        val rotation = Matrix4f().rotateY(camera.rotation.y).rotateX(camera.rotation.x).rotateZ(camera.rotation.z)
        rotation.transformDirection(right.set(1f, 0f, 0f)).normalize()
        rotation.transformDirection(up.set(0f, 1f, 0f)).normalize()
        rotation.transformDirection(forward.set(0f, 0f, -1f)).normalize()
    }

    private fun cameraDirections(camera: Camera3D, right: Vector3f, up: Vector3f, forward: Vector3f)
    {
        val rotation = Matrix4f().rotateXYZ(camera.xRot.toRadians(), camera.yRot.toRadians(), camera.zRot.toRadians())
        rotation.transformDirection(right.set(1f, 0f, 0f)).normalize()
        rotation.transformDirection(up.set(0f, 1f, 0f)).normalize()
        rotation.transformDirection(forward.set(0f, 0f, -1f)).normalize()
    }

    private fun getObjectIdRenderer(engine: PulseEngine) = engine.gfx.getSurface(OBJECT_ID_SURFACE)?.getRenderer<ObjectIdRenderer>()

    private fun getSelectionOutlineRenderer(engine: PulseEngine) = engine.gfx.getSurface(GIZMO_SURFACE)?.getRenderer<ObjectOutlineRenderer>()

    enum class GizmoMode { MOVE, ROTATE, SCALE }

    private enum class Handle { NONE, X, Y, Z, XY, XZ, YZ, UNIFORM }

    private enum class PickMode { REPLACE, ADD, TOGGLE }

    private data class TransformSnapshot(
        val position: Vector3f,
        val rotation: Vector3f,
        val scale: Vector3f
    )

    private data class SelectedTarget(
        val entity: SceneEntity,
        val spatial: Translatable3D
    )

    private data class TransformSelection(
        val targets: List<SelectedTarget>,
        val pivot: Vector3f
    )

    private data class TransformTarget(
        val entity: SceneEntity,
        val spatial: Translatable3D,
        val snapshot: TransformSnapshot
    )

    private data class TransformDrag(
        val targets: List<TransformTarget>,
        val mode: GizmoMode,
        val handle: Handle,
        val pivot: Vector3f,
        val axisSigns: Vector3f,
        val gizmoSize: Float,
        var startParameter: Float = 0f,
        val startVector: Vector3f = Vector3f(),
        val startPlanePoint: Vector3f = Vector3f(),
        val startMouse: Vector2f = Vector2f()
    )

    companion object
    {
        private const val OBJECT_ID_SURFACE = "scene_editor_object_ids"
        private const val GIZMO_SURFACE = "scene_editor_3d_gizmo"
        private const val LOOK_SENSITIVITY = 0.003f
        private const val FLY_SPEED = 5f
        private const val FLY_FAST_MULTIPLIER = 4f
        private const val SCROLL_MOVE_PER_NOTCH = 0.75f
        private const val DEFAULT_ORBIT_DISTANCE = 5f
        private const val MIN_ORBIT_DISTANCE = 0.1f
        private const val MAX_PITCH = 1.55334f
        private const val HIT_TOLERANCE = 10f
        private const val CENTER_HANDLE_RADIUS = 12f
        private const val RING_SEGMENTS = 64
        private const val PLANE_SQUARE_MIN = 0.18f
        private const val PLANE_SQUARE_MAX = 0.48f
        private const val PLANE_TRIANGLE_INNER = 0.18f
        private const val PLANE_TRIANGLE_SIZE = 0.48f
        private const val PLANE_HIT_PADDING = 10f
        private const val MIN_PLANE_HIT_SCORE = 0.001f
        private const val LIGHT_MARKER_RADIUS = 7f
        private const val LIGHT_MARKER_CROSS_SIZE = 4f
        private const val LIGHT_MARKER_HIT_RADIUS = 13f
        private const val LIGHT_MARKER_SEGMENTS = 16
        private const val CAMERA_MARKER_RADIUS = 8f
        private const val CAMERA_MARKER_HIT_RADIUS = 14f
        private const val CAMERA_PREVIEW_PIXEL_LENGTH = 55f
        private const val LIGHT_VOLUME_SEGMENTS = 48
        private const val LIGHT_CONE_SIDE_COUNT = 8
        private const val MAX_CONE_ANGLE = 89f

        private val AXIS_HANDLES = arrayOf(Handle.X, Handle.Y, Handle.Z)
        private val PLANE_HANDLES = arrayOf(Handle.XY, Handle.XZ, Handle.YZ)
        private val WORLD_X = Vector3f(1f, 0f, 0f)
        private val WORLD_Y = Vector3f(0f, 1f, 0f)
        private val WORLD_Z = Vector3f(0f, 0f, 1f)
        private val X_COLOR = Color(0.92f, 0.18f, 0.18f, 1f)
        private val Y_COLOR = Color(0.25f, 0.9f, 0.25f, 1f)
        private val Z_COLOR = Color(0.2f, 0.45f, 1f, 1f)
        private val ACTIVE_COLOR = Color(1f, 0.8f, 0.12f, 1f)
        private val CENTER_COLOR = Color(0.85f, 0.85f, 0.85f, 1f)
        private val ACTIVE_PLANE_COLOR = Color(1f, 0.75f, 0.08f, 0.9f)
        private val LIGHT_MARKER_COLOR = Color(1f, 0.72f, 0.16f, 1f)
        private val LIGHT_VOLUME_COLOR = Color(1f, 0.72f, 0.16f, 0.8f)
        private val CAMERA_MARKER_COLOR = Color(0.25f, 0.82f, 1f, 1f)
    }
}