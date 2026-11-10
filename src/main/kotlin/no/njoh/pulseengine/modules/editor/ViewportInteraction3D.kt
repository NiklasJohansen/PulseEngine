package no.njoh.pulseengine.modules.editor

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject
import no.njoh.pulseengine.core.graphics.scene3d.renderers.GridRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ViewMode
import no.njoh.pulseengine.core.graphics.scene3d.renderers.RenderIdOutlineRenderer
import no.njoh.pulseengine.core.graphics.scene3d.submission.ModelItem
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderItem
import no.njoh.pulseengine.core.graphics.util.PixelReadResult
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.input.CursorType
import no.njoh.pulseengine.core.input.CursorMode
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.input.MouseButton
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneState
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.EDITABLE
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.POSITION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.ROTATION_UPDATED
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.SIZE_UPDATED
import no.njoh.pulseengine.core.scene.interfaces.Rotatable3D
import no.njoh.pulseengine.core.scene.interfaces.Spatial3D
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D
import no.njoh.pulseengine.modules.scene.systems.Light3D
import no.njoh.pulseengine.modules.scene.systems.Scene3DRenderSystem
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.AxisColors
import no.njoh.pulseengine.core.shared.primitives.Mat4f
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.editor.EditorMode.*
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
) : ViewportInteraction(MODE_3D) {

    var gizmoMode = GizmoMode.MOVE

    private val defaultCameraState = initialCameraState.duplicate().also { it.sanitizePerspective3D() }

    private var hoveredHandle = Handle.NONE
    private var transformDrag: TransformDrag? = null
    private var isControlPressed = false
    private val pickResult = PixelReadResult()
    private var pickPending = false
    private var pendingPickMode = PickMode.REPLACE
    private var cameraDragging = false
    private var flyCursorGrabbed = false
    private val flyVelocity = Vector3f()
    private var pendingScrollDistance = 0f
    private var selectionDrag: SelectionDrag? = null
    private val selectedTransformables = ArrayList<SceneEntity>()
    private val gizmoContext = GizmoRenderContext()
    private val selectedPivot = Vector3f()
    private val cachedTransformSelection = TransformSelection(selectedTransformables, selectedPivot)
    private var transformSelectionChanged = true
    private var outlineSelectionDirty = true
    private val orbitPivot = Vector3f()
    private var orbitPivotValid = false
    private var orbitPivotFollowsCamera = true
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
    private val cameraRotation = Matrix4f()
    private val projectedBoundsMin = Vector2f()
    private val projectedBoundsMax = Vector2f()
    private val boundsCorners = Array(8) { Vector3f() }
    private val projectedEdgePoints = Array(24) { Vector2f() }
    private val projectedHullPoints = Array(24) { Vector2f() }
    private val submittedBounds = Model.Aabb()
    private val boundedMarqueeRenderIds = LongOpenHashSet()
    private val matchedMarqueeRenderIds = LongOpenHashSet()

    override fun onCreate(engine: PulseEngine, context: ViewportContext)
    {
        engine.gfx.createSurface(
            name = GIZMO_SURFACE,
            output = SurfaceOutputSpec(multisampling = Multisampling.MSAA8),
            clearColor = Color(0.5f, 0.5f, 0.5f, 0f),
            zOrder = -50
        ).apply {
            val surfaceConfig = (this as SurfaceInternal).config
            addRenderer(GizmoRenderer(Scene3DRenderSystem.SCENE_3D_SURFACE, context.camera, surfaceConfig))
        }

        updateOrbitPivotFromSelection(context)
    }

    override fun onUpdate(engine: PulseEngine, context: ViewportContext)
    {
        isControlPressed = engine.input.isPressed(Key.LEFT_CONTROL) || engine.input.isPressed(Key.RIGHT_CONTROL)
        updateViewMode(engine, context.viewMode, context.isWireframeVisible)

        engine.input.setCursorType(CursorType.ARROW)
        consumePickResult(engine, context)
        val selected = getSelectedTransformables(context)
        observeSelection(selected)

        val hover = engine.input.hasHoverFocus(context.focusArea)
        val cameraConsumed = updateCamera(engine, context, hover, selected)
        if (cameraConsumed)
        {
            cancelSelectionDrag(context)
            hoveredHandle = Handle.NONE
            return
        }

        if (selected != null && !supportsMode(selected, gizmoMode))
            gizmoMode = GizmoMode.MOVE

        if (engine.input.wasClicked(Key.W)) gizmoMode = GizmoMode.MOVE
        if (engine.input.wasClicked(Key.E) && (selected == null || supportsMode(selected, GizmoMode.ROTATE))) gizmoMode = GizmoMode.ROTATE
        if (engine.input.wasClicked(Key.R) && (selected == null || supportsMode(selected, GizmoMode.SCALE))) gizmoMode = GizmoMode.SCALE

        hoveredHandle = if (hover && selected != null && isGizmoVisible()) hitTestGizmo(engine, context, selected.pivot) else Handle.NONE

        val drag = transformDrag
        if (drag != null)
        {
            if (drag.targets.anyMatches { it.entity.isNot(EDITABLE) || it.entity.isSet(HIDDEN) })
            {
                transformDrag = null
                return
            }
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

        selectionDrag?.let() 
        {
            updateSelectionDrag(engine, context, it)
            return
        }

        if (!hover || !engine.input.wasClicked(MouseButton.LEFT))
            return

        if (selected != null && hoveredHandle != Handle.NONE && beginTransformDrag(engine, context, selected, hoveredHandle))
            return

        val pickMode = currentPickMode(engine)
        findGizmoMarkerAtMouse(engine, context)?.let { entity ->
            pickPending = false
            applyPickedEntity(engine, context, entity, pickMode)
            return
        }

        selectionDrag = SelectionDrag(
            start = Vector2f(engine.input.xMouse, engine.input.yMouse),
            current = Vector2f(engine.input.xMouse, engine.input.yMouse),
            mode = pickMode,
            initialSelection = context.selection.toList()
        )
    }

    override fun onRender(engine: PulseEngine, context: ViewportContext)
    {
        updateGrid(engine, context)

        if (engine.scene.state != SceneState.STOPPED)
        {
            setFlyCursorGrabbed(engine, false)
            flyVelocity.zero()
            pendingScrollDistance = 0f
            transformDrag = null
            selectionDrag = null
            if (cameraDragging)
            {
                cameraDragging = false
                engine.input.setCursorType(CursorType.ARROW)
            }
            return
        }

        val outlineRenderer = getOrCreateSelectionOutlineRenderer(engine)
        val selected = getSelectedTransformables(context)
        if (outlineRenderer != null && outlineSelectionDirty)
        {
            outlineRenderer.beginSelectionUpdate()
            selectedTransformables.forEachFast { outlineRenderer.setSelected(it.id) }
            outlineRenderer.finishSelectionUpdate()
            outlineSelectionDirty = false
        }

        renderEntityGizmos(engine, context)

        if (selected != null && isGizmoVisible())
            renderGizmo(engine, context, selected.pivot)

        renderSelectionRectangle(engine)
    }

    private fun isGizmoVisible() = transformDrag != null || !isControlPressed

    override fun reset(engine: PulseEngine, context: ViewportContext)
    {
        setFlyCursorGrabbed(engine, false)
        flyVelocity.zero()
        transformDrag = null
        selectionDrag = null
        hoveredHandle = Handle.NONE
        pickPending = false
        pendingPickMode = PickMode.REPLACE
        transformSelectionChanged = true
        outlineSelectionDirty = true
        cameraDragging = false
        pendingScrollDistance = 0f
        orbitPivotValid = false
        orbitPivotFollowsCamera = true
        engine.input.setCursorType(CursorType.ARROW)
    }

    override fun onEntitiesDuplicated(engine: PulseEngine, context: ViewportContext, duplicatesBySource: Map<SceneEntity, SceneEntity>)
    {
        val drag = transformDrag ?: return
        val duplicateTargets = drag.targets.map { target ->
            val duplicate = duplicatesBySource[target.entity] ?: return
            val spatial = duplicate as? Translatable3D ?: return
            TransformTarget(duplicate, spatial, target.snapshot)
        }

        // Rebase the active drag at the copies' current transforms. This keeps the movement seamless,
        // while Escape restores the copies to where duplication occurred instead of the drag's old start.
        gizmoMode = drag.mode
        transformDrag = null
        val duplicateSelection = getSelectedTransformables(context)
        if (duplicateSelection == null || !beginTransformDrag(engine, context, duplicateSelection, drag.handle))
            transformDrag = drag.copy(targets = duplicateTargets)

        transformSelectionChanged = false
        pickPending = false
        updateOrbitPivotFromSelection(context)
    }

    override fun onDestroy(engine: PulseEngine, context: ViewportContext)
    {
        flyVelocity.zero()
        setFlyCursorGrabbed(engine, false)
        updateViewMode(engine, ViewMode.FINAL)
        engine.input.setCursorType(CursorType.ARROW)
        val surface = engine.gfx.getSurface(Scene3DRenderSystem.SCENE_3D_SURFACE)
        val renderer = surface?.getRenderer<GridRenderer>()
        if (renderer != null)
            surface.deleteRenderer(renderer)
        engine.gfx.deleteSurface(GIZMO_SURFACE)
    }

    private fun updateGrid(engine: PulseEngine, context: ViewportContext)
    {
        val visible = engine.scene.state == SceneState.STOPPED && context.isGridVisible
        val surface = engine.gfx.getSurface(Scene3DRenderSystem.SCENE_3D_SURFACE) ?: return
        val renderer = surface.getRenderer<GridRenderer>()

        if (visible)
        {
            if (renderer == null) surface.addRenderer(GridRenderer())
        }
        else if (renderer != null)
        {
            surface.deleteRenderer(renderer)
        }
    }

    private fun updateViewMode(engine: PulseEngine, viewMode: ViewMode, showWireframe: Boolean = false)
    {
        val surface = engine.gfx.getSurface(Scene3DRenderSystem.SCENE_3D_SURFACE) ?: return
        surface.getRenderer<ModelRenderer>()?.let {
            it.viewMode = viewMode
            it.drawWireframeOverlay = showWireframe
        }
        surface.config.drawPostEffects = viewMode == ViewMode.FINAL
    }

    private fun consumePickResult(engine: PulseEngine, context: ViewportContext)
    {
        if (!pickPending || pickResult.isPending || !pickResult.isReady) return
        pickPending = false

        val renderId = InstanceBufferObject.decodeRenderId(pickResult.redInt)

        val entity = engine.scene.getEntity(renderId)
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
        var targetCount = 0
        var selectionChanged = false
        var allScalable = true
        selectedPivot.zero()
        
        context.selection.forEachFast { entity ->
 
            val spatial = entity as? Translatable3D ?: return@forEachFast
            if (entity.isNot(EDITABLE) || entity.isSet(HIDDEN))
                return@forEachFast

            if (targetCount == selectedTransformables.size)
            {
                selectedTransformables.add(entity)
                selectionChanged = true
            }
            else if (selectedTransformables[targetCount] !== entity)
            {
                selectedTransformables[targetCount] = entity
                selectionChanged = true
            }
            
            targetCount++
            allScalable = allScalable && spatial is Spatial3D
            selectedPivot.add(spatial.position)
        }

        if (selectedTransformables.size > targetCount)
        {
            selectedTransformables.subList(targetCount, selectedTransformables.size).clear()
            selectionChanged = true
        }

        if (selectionChanged)
        {
            transformSelectionChanged = true
            outlineSelectionDirty = true
        }

        if (targetCount == 0) return null

        cachedTransformSelection.allScalable = allScalable
        selectedPivot.div(targetCount.toFloat())
        return cachedTransformSelection
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
        context.selectEntities(engine, selection)
        transformSelectionChanged = true

        getSelectedTransformables(context)?.let()
        {
            orbitPivot.set(it.pivot)
            orbitPivotFollowsCamera = false
            orbitPivotValid = true
            if (!supportsMode(it, gizmoMode))
                gizmoMode = GizmoMode.MOVE
        }

        if (selection.isEmpty()) orbitPivotValid = false
    }

    private fun updateSelectionDrag(engine: PulseEngine, context: ViewportContext, drag: SelectionDrag)
    {
        drag.current.set(engine.input.xMouse, engine.input.yMouse)

        if (engine.input.wasClicked(Key.ESCAPE))
        {
            cancelSelectionDrag(context)
            return
        }

        if (!drag.active && drag.start.distanceSquared(drag.current) >= SELECTION_DRAG_THRESHOLD_SQUARED)
        {
            drag.active = true
            pickPending = false
        }

        if (drag.active)
            applyRectangleSelection(engine, context, drag)

        if (!engine.input.isPressed(MouseButton.LEFT))
        {
            if (!drag.active)
            {
                requestObjectPick(engine, drag.start, drag.mode)
            }
            else if (context.selection != drag.initialSelection)
            {
                context.commitSelection(engine)
            }
            selectionDrag = null
        }
    }

    private fun cancelSelectionDrag(context: ViewportContext)
    {
        val drag = selectionDrag ?: return
        if (drag.active && context.selection != drag.initialSelection)
            context.previewSelection(drag.initialSelection)
        selectionDrag = null
    }

    private fun requestObjectPick(engine: PulseEngine, position: Vector2f, mode: PickMode)
    {
        if (pickResult.isPending) return

        val renderIdSurface = getRenderIdSurface(engine) ?: return
        renderIdSurface.readPixel(
            x = position.x.toInt(),
            y = position.y.toInt(),
            attachmentPoint = Scene3DRenderSystem.RENDER_ID_ATTACHMENT_POINT,
            dstResult = pickResult
        )
        pendingPickMode = mode
        pickPending = true
    }

    private fun applyRectangleSelection(engine: PulseEngine, context: ViewportContext, drag: SelectionDrag)
    {
        val xMin = min(drag.start.x, drag.current.x)
        val yMin = min(drag.start.y, drag.current.y)
        val xMax = max(drag.start.x, drag.current.x)
        val yMax = max(drag.start.y, drag.current.y)
        val matches = ArrayList<SceneEntity>()
        collectMarqueeMatches(engine, context.camera, xMin, yMin, xMax, yMax)

        engine.scene.forEachEntityOfType<Translatable3D>
        {
            val entity = it as SceneEntity
            if (entity.isSet(EDITABLE) && entity.isNot(HIDDEN) && projectedBoundsOverlap(engine, context, entity, it, xMin, yMin, xMax, yMax))
                matches.add(entity)
        }

        val initialIds = drag.initialSelection.mapTo(HashSet()) { it.id }
        val matchIds = matches.mapTo(HashSet()) { it.id }
        val selection = when (drag.mode)
        {
            PickMode.REPLACE ->
            {
                matches
            }
            PickMode.ADD ->
            {
                ArrayList<SceneEntity>(drag.initialSelection.size + matches.size).apply()
                {
                    addAll(drag.initialSelection)
                    matches.filterTo(this) { it.id !in initialIds }
                }
            }
            PickMode.TOGGLE ->
            {
                // Ctrl-click toggles one entity, while Ctrl-drag only removes marquee matches.
                ArrayList<SceneEntity>(drag.initialSelection.size).apply()
                {
                    drag.initialSelection.filterTo(this) { it.id !in matchIds }
                }
            }
        }

        if (selection == context.selection)
            return

        context.previewSelection(selection)
        transformSelectionChanged = true

        getSelectedTransformables(context)?.let()
        {
            orbitPivot.set(it.pivot)
            orbitPivotFollowsCamera = false
            orbitPivotValid = true
            if (!supportsMode(it, gizmoMode))
                gizmoMode = GizmoMode.MOVE
        }

        if (selection.isEmpty())
            orbitPivotValid = false
    }

    private fun findGizmoMarkerAtMouse(engine: PulseEngine, context: ViewportContext): SceneEntity?
    {
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return null
        
        gizmoContext.setFor(context.camera, surface.config.width, surface.config.height)
        tmpMouse.set(engine.input.xMouse, engine.input.yMouse)
        var closest: SceneEntity? = null
        var closestDistance = Float.MAX_VALUE

        engine.scene.forEachEntityOfType<GizmoRenderable>
        {
            val entity = it as SceneEntity
            if (entity.isNot(EDITABLE) || entity.isAnySet(HIDDEN or DEAD))
                return@forEachEntityOfType
            
            if (!gizmoContext.project(it.position, tmpP0))
                return@forEachEntityOfType

            val distance = tmpMouse.distance(tmpP0)
            if (distance < it.gizmoMarkerRadius + GizmoRenderContext.MARKER_PICK_PADDING && distance < closestDistance)
            {
                closestDistance = distance
                closest = entity
            }
        }

        return closest
    }

    override fun onEditorActivated(engine: PulseEngine, context: ViewportContext)
    {
        updateViewMode(engine, context.viewMode, context.isWireframeVisible)
        reset(engine, context)
        updateOrbitPivotFromSelection(context)
    }

    override fun onEditorDeactivated(engine: PulseEngine, context: ViewportContext)
    {
        updateViewMode(engine, ViewMode.FINAL)
        val outlineSurface = engine.gfx.getSurface(GIZMO_SURFACE)
        val outlineRenderer = outlineSurface?.getRenderer<RenderIdOutlineRenderer>()
        if (outlineRenderer != null)
            outlineSurface.deleteRenderer(outlineRenderer)
        val surface = engine.gfx.getSurface(Scene3DRenderSystem.SCENE_3D_SURFACE)
        val renderer = surface?.getRenderer<GridRenderer>()
        if (renderer != null)
            surface.deleteRenderer(renderer)
        reset(engine, context)
    }

    override fun captureCameraState(context: ViewportContext) =
        CameraState.from(context.camera).also { it.sanitizePerspective3D() }

    override fun restoreCameraState(engine: PulseEngine, context: ViewportContext, state: CameraState?)
    {
        (state ?: defaultCameraState)
            .duplicate()
            .also { it.sanitizePerspective3D() }
            .loadInto(context.camera, engine.window.width, engine.window.height)
        reset(engine, context)
        updateOrbitPivotFromSelection(context)
    }

    override fun resetCamera(engine: PulseEngine, context: ViewportContext)
    {
        restoreCameraState(engine, context, defaultCameraState)
    }

    private fun observeSelection(selected: TransformSelection?)
    {
        if (!transformSelectionChanged) return

        transformSelectionChanged = false
        pickPending = false
        transformDrag = null
        if (selected != null)
        {
            orbitPivot.set(selected.pivot)
            orbitPivotFollowsCamera = false
            orbitPivotValid = true
        }
        else orbitPivotValid = false
    }

    private fun updateCamera(engine: PulseEngine, context: ViewportContext, hover: Boolean, selected: TransformSelection?): Boolean
    {
        val input = engine.input
        val camera = context.camera
        val spacePressed = input.isPressed(Key.SPACE)
        val altPressed = input.isPressed(Key.LEFT_ALT)
        val shiftPressed = input.isPressed(Key.LEFT_SHIFT)

        val spacePan = spacePressed && input.isPressed(MouseButton.LEFT) && (hover || cameraDragging)
        val fly = input.isPressed(MouseButton.RIGHT) && (hover || cameraDragging)
        val orbit = altPressed && input.isPressed(MouseButton.MIDDLE) && (hover || cameraDragging)
        val pan = (input.isPressed(MouseButton.MIDDLE) || spacePan) && (hover || cameraDragging) && !orbit
        val dragging = fly || orbit || pan
        if (!fly && dragging) flyVelocity.zero()

        val flyMovement = input.isPressed(Key.W) || input.isPressed(Key.A) || input.isPressed(Key.S) || input.isPressed(Key.D)
        setFlyCursorGrabbed(engine, fly && (flyCursorGrabbed || flyMovement))

        if (dragging)
        {
            pendingScrollDistance = 0f
            if (!cameraDragging)
            {
                ensureOrbitPivot(camera, selected)
                cameraDragging = true
            }
            input.setCursorType(CursorType.HAND_GRAB)

            val previousYaw = camera.rotation.y
            val previousPitch = camera.rotation.x
            if (fly || orbit)
            {
                camera.rotation.y -= input.xdMouse * LOOK_SENSITIVITY
                camera.rotation.x = (camera.rotation.x - input.ydMouse * LOOK_SENSITIVITY).coerceIn(-MAX_PITCH, MAX_PITCH)
            }

            cameraDirections(camera, tmpV0, tmpV1, tmpV2)

            if (fly)
            {
                val orbitDistance = max(camera.position.distance(orbitPivot), MIN_ORBIT_DISTANCE)
                tmpV3.zero()
                if (input.isPressed(Key.W)) tmpV3.add(tmpV2)
                if (input.isPressed(Key.S)) tmpV3.sub(tmpV2)
                if (input.isPressed(Key.D)) tmpV3.add(tmpV0)
                if (input.isPressed(Key.A)) tmpV3.sub(tmpV0)
                if (input.isPressed(Key.E)) tmpV3.add(0f, 1f, 0f)
                if (input.isPressed(Key.Q)) tmpV3.sub(0f, 1f, 0f)
                if (tmpV3.lengthSquared() > 0f)
                {
                    val multiplier = if (shiftPressed) 4f else if (altPressed) 0.2f else 1f
                    tmpV3.normalize(FLY_SPEED * multiplier)
                }

                // Ease toward the requested velocity and integrate the exponential over this frame.
                val deltaTime = engine.data.deltaTime
                val fraction = 1f - exp(-FLY_SMOOTHING * deltaTime)
                tmpV1.set(flyVelocity).sub(tmpV3)
                tmpV0.set(tmpV3).mul(deltaTime).fma(fraction / FLY_SMOOTHING, tmpV1)
                flyVelocity.lerp(tmpV3, fraction)
                if (tmpV3.lengthSquared() == 0f && flyVelocity.lengthSquared() < 0.000001f)
                    flyVelocity.zero()
                camera.position.add(tmpV0)
                // Free flight establishes a new view-centered pivot, including when looking around.
                orbitPivot.set(tmpV2).mul(orbitDistance).add(camera.position)
                orbitPivotFollowsCamera = true
            }
            else if (orbit)
            {
                // Rotate the actual offset, including when scrolling has moved past the pivot.
                // Rebuilding it from forward * distance would snap back to the opposite side.
                tmpV3.set(camera.position).sub(orbitPivot)
                    .rotateY(-previousYaw)
                    .rotateX(camera.rotation.x - previousPitch)
                    .rotateY(camera.rotation.y)
                camera.position.set(tmpV3).add(orbitPivot)
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

        if (flyVelocity.lengthSquared() > 0f)
        {
            // Keep the same deceleration after releasing right mouse, independently of cursor capture.
            val fraction = 1f - exp(-FLY_SMOOTHING * engine.data.deltaTime)
            tmpV0.set(flyVelocity).mul(fraction / FLY_SMOOTHING)
            camera.position.add(tmpV0)
            if (orbitPivotValid) orbitPivot.add(tmpV0)
            flyVelocity.mul(1f - fraction)
            if (flyVelocity.lengthSquared() < 0.000001f) flyVelocity.zero()
        }

        if (!hover)
        {
            pendingScrollDistance = 0f
            return false
        }

        if (spacePressed)
        {
            pendingScrollDistance = 0f
            input.setCursorType(CursorType.HAND_OPEN)
            return true
        }

        if (input.wasClicked(Key.F) && selected != null)
        {
            flyVelocity.zero()
            pendingScrollDistance = 0f
            frameSelection(engine, context, selected)
        }

        // InputImpl redirects the vertical wheel to xScroll while left Shift is held.
        val scroll = if (shiftPressed) input.xScroll else input.yScroll
        pendingScrollDistance += scroll * SCROLL_MOVE_PER_NOTCH * (if (shiftPressed) 2f else if (altPressed) 0.2f else 1f)

        if (pendingScrollDistance != 0f)
        {
            ensureOrbitPivot(camera, selected)
            val orbitDistance = max(camera.position.distance(orbitPivot), MIN_ORBIT_DISTANCE)
            // Exponential easing preserves the distance per notch across frame rates.
            val fraction = 1f - exp(-SCROLL_SMOOTHING * engine.data.deltaTime)
            val distance = if (abs(pendingScrollDistance) < 0.0001f) pendingScrollDistance else pendingScrollDistance * fraction
            pendingScrollDistance -= distance
            cameraDirections(camera, tmpV0, tmpV1, tmpV2)
            SceneEditor3DMath.scrollTranslation(tmpV2, distance, 1f, tmpV3)
            camera.position.add(tmpV3)
            if (orbitPivotFollowsCamera)
                orbitPivot.set(tmpV2).mul(orbitDistance).add(camera.position)
            else if (tmpV0.set(orbitPivot).sub(camera.position).dot(tmpV2) <= MIN_ORBIT_DISTANCE)
            {
                // Keep zoom anchored until we reach/pass the pivot, then continue as free flight.
                orbitPivotFollowsCamera = true
                orbitPivot.set(tmpV2).mul(DEFAULT_ORBIT_DISTANCE).add(camera.position)
            }
            return true
        }

        return false
    }

    private fun setFlyCursorGrabbed(engine: PulseEngine, grabbed: Boolean)
    {
        if (flyCursorGrabbed == grabbed) 
            return
        flyCursorGrabbed = grabbed
        engine.input.setCursorMode(if (grabbed) CursorMode.GRABBED else CursorMode.NORMAL)
    }

    private fun ensureOrbitPivot(camera: Camera, selected: TransformSelection?)
    {
        if (orbitPivotValid) return
        orbitPivotFollowsCamera = selected == null
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
        orbitPivotFollowsCamera = false
        orbitPivotValid = true
    }

    private fun frameSelection(engine: PulseEngine, context: ViewportContext, selected: TransformSelection)
    {
        val center = Vector3f(selected.pivot)
        var radius = 1f

        selected.targets.forEachFast { entity ->
            val spatial = entity as Translatable3D
            val (targetCenter, targetRadius) = getSelectionBounds(engine, entity, spatial)
            radius = max(radius, center.distance(targetCenter) + targetRadius)
        }

        orbitPivot.set(center)
        orbitPivotFollowsCamera = false
        orbitPivotValid = true

        cameraDirections(context.camera, tmpV0, tmpV1, tmpV2)
        val halfFov = max(context.camera.fov.toRadians() * 0.5f, 0.01f)
        val distance = max(radius * 1.35f / tan(halfFov), MIN_ORBIT_DISTANCE)

        context.camera.position.set(tmpV2).mul(-distance).add(center)
    }

    private fun getSelectionBounds(engine: PulseEngine, entity: SceneEntity, spatial: Translatable3D): Pair<Vector3f, Float>
    {
        if (getSubmittedRenderIdBounds(engine, entity.id, submittedBounds))
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
            return Vector3f(entity.position) to max(entity.range, 1f)

        return Vector3f(spatial.position) to 1f
    }

    private fun projectedBoundsOverlap(
        engine: PulseEngine,
        context: ViewportContext,
        entity: SceneEntity,
        spatial: Translatable3D,
        selectionXMin: Float,
        selectionYMin: Float,
        selectionXMax: Float,
        selectionYMax: Float
    ): Boolean {
        if (entity.id in boundedMarqueeRenderIds)
            return entity.id in matchedMarqueeRenderIds

        tmpV0.set(spatial.position)
        if (!project(context.camera, tmpV0, engine.window.width, engine.window.height, projectedBoundsMin))
            return false

        val radius = if (entity is GizmoRenderable)
            entity.gizmoMarkerRadius + GizmoRenderContext.MARKER_PICK_PADDING
        else 
            POINT_SELECTION_RADIUS

        projectedBoundsMax.set(projectedBoundsMin).add(radius, radius)
        projectedBoundsMin.sub(radius, radius)

        return projectedBoundsMax.x >= selectionXMin && projectedBoundsMin.x <= selectionXMax &&
            projectedBoundsMax.y >= selectionYMin && projectedBoundsMin.y <= selectionYMax
    }

    private fun projectRenderItemOverlaps(
        camera: Camera,
        item: RenderItem,
        width: Int,
        height: Int,
        selectionXMin: Float,
        selectionYMin: Float,
        selectionXMax: Float,
        selectionYMax: Float
    ): Boolean {
        val bounds = item.cullingBounds ?: item.mesh.localBounds
        setTransformedPoint(boundsCorners[0], bounds.xMin, bounds.yMin, bounds.zMin, item.transform)
        setTransformedPoint(boundsCorners[1], bounds.xMax, bounds.yMin, bounds.zMin, item.transform)
        setTransformedPoint(boundsCorners[2], bounds.xMin, bounds.yMax, bounds.zMin, item.transform)
        setTransformedPoint(boundsCorners[3], bounds.xMax, bounds.yMax, bounds.zMin, item.transform)
        setTransformedPoint(boundsCorners[4], bounds.xMin, bounds.yMin, bounds.zMax, item.transform)
        setTransformedPoint(boundsCorners[5], bounds.xMax, bounds.yMin, bounds.zMax, item.transform)
        setTransformedPoint(boundsCorners[6], bounds.xMin, bounds.yMax, bounds.zMax, item.transform)
        setTransformedPoint(boundsCorners[7], bounds.xMax, bounds.yMax, bounds.zMax, item.transform)

        return projectedTransformedBoundsOverlap(camera, width, height, selectionXMin, selectionYMin, selectionXMax, selectionYMax)
    }

    private fun projectModelSubmissionOverlaps(
        camera: Camera,
        submission: ModelItem,
        width: Int,
        height: Int,
        selectionXMin: Float,
        selectionYMin: Float,
        selectionXMax: Float,
        selectionYMax: Float
    ): Boolean {
        val bounds = submission.model.localBounds ?: return false
        setTransformedPoint(boundsCorners[0], bounds.xMin, bounds.yMin, bounds.zMin, submission.transform)
        setTransformedPoint(boundsCorners[1], bounds.xMax, bounds.yMin, bounds.zMin, submission.transform)
        setTransformedPoint(boundsCorners[2], bounds.xMin, bounds.yMax, bounds.zMin, submission.transform)
        setTransformedPoint(boundsCorners[3], bounds.xMax, bounds.yMax, bounds.zMin, submission.transform)
        setTransformedPoint(boundsCorners[4], bounds.xMin, bounds.yMin, bounds.zMax, submission.transform)
        setTransformedPoint(boundsCorners[5], bounds.xMax, bounds.yMin, bounds.zMax, submission.transform)
        setTransformedPoint(boundsCorners[6], bounds.xMin, bounds.yMax, bounds.zMax, submission.transform)
        setTransformedPoint(boundsCorners[7], bounds.xMax, bounds.yMax, bounds.zMax, submission.transform)

        return projectedTransformedBoundsOverlap(camera, width, height, selectionXMin, selectionYMin, selectionXMax, selectionYMax)
    }

    private fun projectedTransformedBoundsOverlap(
        camera: Camera,
        width: Int,
        height: Int,
        selectionXMin: Float,
        selectionYMin: Float,
        selectionXMax: Float,
        selectionYMax: Float
    ): Boolean {

        projectedBoundsMin.set(Float.MAX_VALUE, Float.MAX_VALUE)
        projectedBoundsMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE)
        
        var pointCount = 0
        for (i in AABB_EDGES.indices step 2)
        {
            val start = boundsCorners[AABB_EDGES[i]]
            val end = boundsCorners[AABB_EDGES[i + 1]]
            if (!projectLine(camera, start, end, width, height, tmpP0, tmpP1, clipDepth = true))
                continue

            pointCount = addProjectedPoint(tmpP0, pointCount)
            pointCount = addProjectedPoint(tmpP1, pointCount)
        }

        if (pointCount == 0 ||
            projectedBoundsMax.x < selectionXMin || projectedBoundsMin.x > selectionXMax ||
            projectedBoundsMax.y < selectionYMin || projectedBoundsMin.y > selectionYMax)
            return false

        val hullCount = buildProjectedHull(pointCount)
        return projectedHullOverlapsRectangle(hullCount, selectionXMin, selectionYMin, selectionXMax, selectionYMax)
    }

    private fun addProjectedPoint(point: Vector2f, count: Int): Int
    {
        for (i in 0 until count)
        {
            if (projectedEdgePoints[i].distanceSquared(point) <= PROJECTED_POINT_EPSILON_SQUARED)
                return count
        }

        projectedEdgePoints[count].set(point)
        projectedBoundsMin.x = min(projectedBoundsMin.x, point.x)
        projectedBoundsMin.y = min(projectedBoundsMin.y, point.y)
        projectedBoundsMax.x = max(projectedBoundsMax.x, point.x)
        projectedBoundsMax.y = max(projectedBoundsMax.y, point.y)
        return count + 1
    }

    private fun buildProjectedHull(pointCount: Int): Int
    {
        for (i in 1 until pointCount)
        {
            val point = projectedEdgePoints[i]
            var j = i - 1
            while (j >= 0 && compareProjectedPoints(projectedEdgePoints[j], point) > 0)
            {
                projectedEdgePoints[j + 1] = projectedEdgePoints[j]
                j--
            }
            projectedEdgePoints[j + 1] = point
        }

        if (pointCount == 1)
        {
            projectedHullPoints[0].set(projectedEdgePoints[0])
            return 1
        }

        var hullCount = 0
        for (i in 0 until pointCount)
        {
            while (hullCount >= 2 && cross(projectedHullPoints[hullCount - 2], projectedHullPoints[hullCount - 1], projectedEdgePoints[i]) <= 0f) 
            {
                hullCount-- 
            }
            projectedHullPoints[hullCount++].set(projectedEdgePoints[i])
        }

        val upperStart = hullCount + 1
        for (i in pointCount - 2 downTo 0)
        {
            while (hullCount >= upperStart && cross(
                    projectedHullPoints[hullCount - 2],
                    projectedHullPoints[hullCount - 1],
                    projectedEdgePoints[i]
                ) <= 0f)
                hullCount--
            projectedHullPoints[hullCount++].set(projectedEdgePoints[i])
        }

        return hullCount - 1
    }

    private fun compareProjectedPoints(a: Vector2f, b: Vector2f): Int
    {
        val xComparison = a.x.compareTo(b.x)
        return if (xComparison != 0) xComparison else a.y.compareTo(b.y)
    }

    private fun cross(a: Vector2f, b: Vector2f, c: Vector2f) =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun projectedHullOverlapsRectangle(hullCount: Int, xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean 
    {
        if (hullCount == 1)
            return pointInsideRectangle(projectedHullPoints[0], xMin, yMin, xMax, yMax)
        
        if (hullCount == 2)
            return segmentIntersectsRectangle(projectedHullPoints[0], projectedHullPoints[1], xMin, yMin, xMax, yMax)

        for (i in 0 until hullCount)
        {
            if (pointInsideRectangle(projectedHullPoints[i], xMin, yMin, xMax, yMax)) return true
        }

        if (pointInsideProjectedHull(xMin, yMin, hullCount) ||
            pointInsideProjectedHull(xMax, yMin, hullCount) ||
            pointInsideProjectedHull(xMin, yMax, hullCount) ||
            pointInsideProjectedHull(xMax, yMax, hullCount)
        ) {
            return true
        }

        for (i in 0 until hullCount)
        {
            val next = (i + 1) % hullCount
            if (segmentIntersectsRectangle(projectedHullPoints[i], projectedHullPoints[next], xMin, yMin, xMax, yMax))
                return true
        }

        return false
    }

    private fun pointInsideRectangle(point: Vector2f, xMin: Float, yMin: Float, xMax: Float, yMax: Float) =
        point.x in xMin..xMax && point.y in yMin..yMax

    private fun pointInsideProjectedHull(x: Float, y: Float, hullCount: Int): Boolean
    {
        var sign = 0
        for (i in 0 until hullCount)
        {
            val a = projectedHullPoints[i]
            val b = projectedHullPoints[(i + 1) % hullCount]
            val value = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            if (abs(value) <= PROJECTED_POINT_EPSILON)
                continue

            val currentSign = if (value > 0f) 1 else -1
            if (sign != 0 && sign != currentSign)
                return false

            sign = currentSign
        }
        return true
    }

    private fun segmentIntersectsRectangle(a: Vector2f, b: Vector2f, xMin: Float, yMin: Float, xMax: Float, yMax: Float): Boolean 
    {
        if (pointInsideRectangle(a, xMin, yMin, xMax, yMax) || pointInsideRectangle(b, xMin, yMin, xMax, yMax))
            return true
        
        if (max(a.x, b.x) < xMin || min(a.x, b.x) > xMax || max(a.y, b.y) < yMin || min(a.y, b.y) > yMax)
            return false

        val dx = b.x - a.x
        val dy = b.y - a.y
        val segmentXMin = min(a.x, b.x)
        val segmentXMax = max(a.x, b.x)
        val segmentYMin = min(a.y, b.y)
        val segmentYMax = max(a.y, b.y)

        if (abs(dx) > PROJECTED_POINT_EPSILON)
        {
            val crossesLeft  = xMin in segmentXMin..segmentXMax && a.y + (xMin - a.x) / dx * dy in yMin..yMax
            val crossesRight = xMax in segmentXMin..segmentXMax && a.y + (xMax - a.x) / dx * dy in yMin..yMax
            if (crossesLeft || crossesRight)
                return true
        }

        if (abs(dy) > PROJECTED_POINT_EPSILON)
        {
            val crossesTop    = yMin in segmentYMin..segmentYMax && a.x + (yMin - a.y) / dy * dx in xMin..xMax
            val crossesBottom = yMax in segmentYMin..segmentYMax && a.x + (yMax - a.y) / dy * dx in xMin..xMax
            if (crossesTop || crossesBottom)
                return true
        }
        return false
    }

    private fun setTransformedPoint(out: Vector3f, x: Float, y: Float, z: Float, transform: Mat4f)
    {
        val matrix = transform.data
        val offset = transform.offset
        val m00 = matrix[offset     ]; val m01 = matrix[offset +  1]; val m02 = matrix[offset +  2]
        val m10 = matrix[offset +  4]; val m11 = matrix[offset +  5]; val m12 = matrix[offset +  6]
        val m20 = matrix[offset +  8]; val m21 = matrix[offset +  9]; val m22 = matrix[offset + 10]
        val m30 = matrix[offset + 12]; val m31 = matrix[offset + 13]; val m32 = matrix[offset + 14]
        out.set(
            m00 * x + m10 * y + m20 * z + m30,
            m01 * x + m11 * y + m21 * z + m31,
            m02 * x + m12 * y + m22 * z + m32
        )
    }

    private fun setTransformedPoint(out: Vector3f, x: Float, y: Float, z: Float, transform: Matrix4f)
    {
        out.set(
            transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30(),
            transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31(),
            transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32()
        )
    }

    private fun collectMarqueeMatches(
        engine: PulseEngine,
        camera: Camera,
        selectionXMin: Float,
        selectionYMin: Float,
        selectionXMax: Float,
        selectionYMax: Float
    ) {
        boundedMarqueeRenderIds.clear()
        matchedMarqueeRenderIds.clear()
        val snapshot = engine.gfx.sceneContext.getSubmittedSceneSnapshot()
        val width = engine.window.width
        val height = engine.window.height

        for (i in 0 until snapshot.renderItemCount)
        {
            val item = snapshot.getRenderItem(i)
            if (item.renderId < 0L)
                continue

            boundedMarqueeRenderIds.add(item.renderId)
            if (item.renderId !in matchedMarqueeRenderIds && projectRenderItemOverlaps(camera, item, width, height, selectionXMin, selectionYMin, selectionXMax, selectionYMax))
                matchedMarqueeRenderIds.add(item.renderId)
        }

        for (i in 0 until snapshot.modelItemCount)
        {
            val item = snapshot.getModelItem(i)
            if (item.renderId < 0L || item.model.localBounds == null)
                continue

            boundedMarqueeRenderIds.add(item.renderId)
            if (item.renderId !in matchedMarqueeRenderIds && projectModelSubmissionOverlaps(camera, item, width, height, selectionXMin, selectionYMin, selectionXMax, selectionYMax))
                matchedMarqueeRenderIds.add(item.renderId)
        }
    }

    private fun getSubmittedRenderIdBounds(engine: PulseEngine, renderId: Long, outBounds: Model.Aabb): Boolean
    {
        val snapshot = engine.gfx.sceneContext.getSubmittedSceneSnapshot()
        var found = false

        for (i in 0 until snapshot.renderItemCount)
        {
            val item = snapshot.getRenderItem(i)
            if (item.renderId != renderId)
                continue

            includeRenderItemBounds(item, outBounds, found)
            found = true
        }

        for (i in 0 until snapshot.modelItemCount)
        {
            val item = snapshot.getModelItem(i)
            val bounds = item.model.localBounds ?: continue
            if (item.renderId != renderId)
                continue

            includeModelItemBounds(bounds, item.transform, outBounds, found)
            found = true
        }
        return found
    }

    private fun includeRenderItemBounds(item: RenderItem, outBounds: Model.Aabb, hasExistingBounds: Boolean)
    {
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

        if (!hasExistingBounds)
        {
            outBounds.set(xMin, yMin, zMin, xMax, yMax, zMax)
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

    private fun includeModelItemBounds(bounds: Model.Aabb, transform: Matrix4f, outBounds: Model.Aabb, hasExistingBounds: Boolean)
    {
        val xCenter = (bounds.xMin + bounds.xMax) * 0.5f
        val yCenter = (bounds.yMin + bounds.yMax) * 0.5f
        val zCenter = (bounds.zMin + bounds.zMax) * 0.5f
        val xHalf   = (bounds.xMax - bounds.xMin) * 0.5f
        val yHalf   = (bounds.yMax - bounds.yMin) * 0.5f
        val zHalf   = (bounds.zMax - bounds.zMin) * 0.5f

        val xWorldCenter = transform.m00() * xCenter + transform.m10() * yCenter + transform.m20() * zCenter + transform.m30()
        val yWorldCenter = transform.m01() * xCenter + transform.m11() * yCenter + transform.m21() * zCenter + transform.m31()
        val zWorldCenter = transform.m02() * xCenter + transform.m12() * yCenter + transform.m22() * zCenter + transform.m32()
        val xWorldHalf   = abs(transform.m00()) * xHalf + abs(transform.m10()) * yHalf + abs(transform.m20()) * zHalf
        val yWorldHalf   = abs(transform.m01()) * xHalf + abs(transform.m11()) * yHalf + abs(transform.m21()) * zHalf
        val zWorldHalf   = abs(transform.m02()) * xHalf + abs(transform.m12()) * yHalf + abs(transform.m22()) * zHalf

        val xMin = xWorldCenter - xWorldHalf
        val yMin = yWorldCenter - yWorldHalf
        val zMin = zWorldCenter - zWorldHalf
        val xMax = xWorldCenter + xWorldHalf
        val yMax = yWorldCenter + yWorldHalf
        val zMax = zWorldCenter + zWorldHalf

        if (!hasExistingBounds)
        {
            outBounds.set(xMin, yMin, zMin, xMax, yMax, zMax)
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

    private fun beginTransformDrag(engine: PulseEngine, context: ViewportContext, selection: TransformSelection, handle: Handle): Boolean
    {
        if (!supportsMode(selection, gizmoMode)) return false
        val pivot = Vector3f(selection.pivot)
        val axisSigns = cameraFacingAxisSigns(context.camera.invViewMatrix.getTranslation(Vector3f()), pivot, Vector3f())
        val size = gizmoWorldSize(context.camera, pivot, engine.window.height)
        val drag = TransformDrag(
            targets = selection.targets.map { entity ->
                val spatial = entity as Translatable3D
                TransformTarget(entity, spatial, snapshot(spatial))
            },
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
                    target.spatial.position.set(target.snapshot.position).add(translation)
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
                    target.spatial.position.set(position)

                    (target.spatial as? Rotatable3D)?.let { rotatable ->
                        val start = Quaternionf().rotationXYZ(
                            target.snapshot.rotation.x.toRadians(),
                            target.snapshot.rotation.y.toRadians(),
                            target.snapshot.rotation.z.toRadians()
                        )
                        val euler = Quaternionf(deltaRotation).mul(start).getEulerAnglesXYZ(Vector3f())
                        rotatable.rotation.set(
                            Math.toDegrees(euler.x.toDouble()).toFloat(),
                            Math.toDegrees(euler.y.toDouble()).toFloat(),
                            Math.toDegrees(euler.z.toDouble()).toFloat()
                        )
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
                    target.spatial.position.set(position)
                    val scalable = target.spatial as Spatial3D
                    scalable.scale.set(
                        SceneEditor3DMath.clampScale(target.snapshot.scale.x * factors.x),
                        SceneEditor3DMath.clampScale(target.snapshot.scale.y * factors.y),
                        SceneEditor3DMath.clampScale(target.snapshot.scale.z * factors.z)
                    )
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
                    context.notifyTransformChanged(engine, entity, spatial::position.name)
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
                            spatial::position.name, rotatable::rotation.name
                        )
                    }
                    else context.notifyTransformChanged(engine, entity, spatial::position.name)
                }
                GizmoMode.SCALE ->
                {
                    val scalable = spatial as Spatial3D
                    entity.set(POSITION_UPDATED)
                    entity.set(SIZE_UPDATED)
                    context.notifyTransformChanged(
                        engine, entity,
                        spatial::position.name, scalable::scale.name
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

    private fun renderSelectionRectangle(engine: PulseEngine)
    {
        val drag = selectionDrag?.takeIf { it.active } ?: return
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        val xMin = min(drag.start.x, drag.current.x)
        val yMin = min(drag.start.y, drag.current.y)
        val xMax = max(drag.start.x, drag.current.x)
        val yMax = max(drag.start.y, drag.current.y)

        surface.setDrawColor(SELECTION_RECT_BORDER_COLOR)
        surface.drawLine(xMin, yMin, xMax, yMin)
        surface.drawLine(xMin, yMax, xMax, yMax)
        surface.drawLine(xMin, yMin, xMin, yMax)
        surface.drawLine(xMax, yMin, xMax, yMax)
    }

    private fun renderEntityGizmos(engine: PulseEngine, context: ViewportContext)
    {
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return
        gizmoContext.setFor(context.camera, surface.config.width, surface.config.height)
        engine.scene.forEachEntityOfType<GizmoRenderable>
        {
            val entity = it as SceneEntity
            if (entity.isNot(EDITABLE) || entity.isAnySet(HIDDEN or DEAD))
                return@forEachEntityOfType

            it.renderGizmo(surface, gizmoContext)
        }
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
            Vector3f(spatial.position),
            Vector3f(rotatable?.rotation ?: Vector3f()),
            Vector3f(scalable?.scale ?: Vector3f(1f))
        )
    }

    private fun restoreTransform(drag: TransformDrag)
    {
        drag.targets.forEach { target ->
            target.spatial.position.set(target.snapshot.position)
            (target.spatial as? Rotatable3D)?.let {
                it.rotation.set(target.snapshot.rotation)
            }
            (target.spatial as? Spatial3D)?.let {
                it.scale.set(target.snapshot.scale)
            }
        }
    }

    private fun supportsMode(selection: TransformSelection, mode: GizmoMode) = when (mode)
    {
        GizmoMode.MOVE -> true
        GizmoMode.ROTATE -> selection.targets.size > 1 || selection.targets.single() is Rotatable3D
        GizmoMode.SCALE -> selection.allScalable
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
        Handle.X -> AxisColors.X
        Handle.Y -> AxisColors.Y
        Handle.Z -> AxisColors.Z
        else -> CENTER_COLOR
    }

    private fun planeColor(handle: Handle) = when (handle)
    {
        Handle.XY -> AxisColors.Z
        Handle.XZ -> AxisColors.Y
        Handle.YZ -> AxisColors.X
        else -> CENTER_COLOR
    }

    private fun cameraDirections(camera: Camera, right: Vector3f, up: Vector3f, forward: Vector3f)
    {
        val rotation = cameraRotation.identity().rotateY(camera.rotation.y).rotateX(camera.rotation.x).rotateZ(camera.rotation.z)
        rotation.transformDirection(right.set(1f, 0f, 0f)).normalize()
        rotation.transformDirection(up.set(0f, 1f, 0f)).normalize()
        rotation.transformDirection(forward.set(0f, 0f, -1f)).normalize()
    }

    private fun getRenderIdSurface(engine: PulseEngine) = engine.gfx
        .getSurface(Scene3DRenderSystem.SCENE_3D_SURFACE)
        ?.takeIf { it.config.hasAttachment(Scene3DRenderSystem.RENDER_ID_ATTACHMENT_POINT) }

    private fun getOrCreateSelectionOutlineRenderer(engine: PulseEngine): RenderIdOutlineRenderer?
    {
        val surface = engine.gfx.getSurface(GIZMO_SURFACE) ?: return null
        surface.getRenderer<RenderIdOutlineRenderer>()?.let { return it }
        return RenderIdOutlineRenderer(
            renderIdSurfaceName = Scene3DRenderSystem.SCENE_3D_SURFACE,
            renderIdAttachmentPoint = Scene3DRenderSystem.RENDER_ID_ATTACHMENT_POINT
        ).also {
            surface.addRenderer(it)
            outlineSelectionDirty = true
        }
    }

    enum class GizmoMode { MOVE, ROTATE, SCALE }

    private enum class Handle { NONE, X, Y, Z, XY, XZ, YZ, UNIFORM }

    private enum class PickMode { REPLACE, ADD, TOGGLE }

    private data class TransformSnapshot(
        val position: Vector3f,
        val rotation: Vector3f,
        val scale: Vector3f
    )

    private class TransformSelection(
        val targets: List<SceneEntity>,
        val pivot: Vector3f
    ) {
        var allScalable = false
    }

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

    private data class SelectionDrag(
        val start: Vector2f,
        val current: Vector2f,
        val mode: PickMode,
        val initialSelection: List<SceneEntity>,
        var active: Boolean = false
    )

    companion object
    {
        private const val GIZMO_SURFACE = "scene_editor_3d_gizmo"
        private const val LOOK_SENSITIVITY = 0.003f
        private const val FLY_SPEED = 5f
        private const val FLY_SMOOTHING = 20f
        private const val SCROLL_MOVE_PER_NOTCH = 0.5f
        private const val SCROLL_SMOOTHING = 18f
        private const val DEFAULT_ORBIT_DISTANCE = 5f
        private const val MIN_ORBIT_DISTANCE = 0.1f
        private const val MAX_PITCH = 1.55334f
        private const val SELECTION_DRAG_THRESHOLD_SQUARED = 25f
        private const val POINT_SELECTION_RADIUS = 6f
        private const val PROJECTED_POINT_EPSILON = 0.001f
        private const val PROJECTED_POINT_EPSILON_SQUARED = 0.000001f
        private const val HIT_TOLERANCE = 10f
        private const val CENTER_HANDLE_RADIUS = 12f
        private const val RING_SEGMENTS = 64
        private const val PLANE_SQUARE_MIN = 0.18f
        private const val PLANE_SQUARE_MAX = 0.48f
        private const val PLANE_TRIANGLE_INNER = 0.18f
        private const val PLANE_TRIANGLE_SIZE = 0.48f
        private const val PLANE_HIT_PADDING = 10f
        private const val MIN_PLANE_HIT_SCORE = 0.001f

        private val AXIS_HANDLES = arrayOf(Handle.X, Handle.Y, Handle.Z)
        private val PLANE_HANDLES = arrayOf(Handle.XY, Handle.XZ, Handle.YZ)
        private val AABB_EDGES = intArrayOf(
            0, 1, 0, 2, 1, 3, 2, 3,
            4, 5, 4, 6, 5, 7, 6, 7,
            0, 4, 1, 5, 2, 6, 3, 7
        )
        private val ACTIVE_COLOR = Color(1f, 0.8f, 0.12f, 1f)
        private val CENTER_COLOR = Color(0.85f, 0.85f, 0.85f, 1f)
        private val ACTIVE_PLANE_COLOR = Color(1f, 0.75f, 0.08f, 0.9f)
        private val SELECTION_RECT_BORDER_COLOR = Color(1f, 1f, 1f, 1f)
    }
}