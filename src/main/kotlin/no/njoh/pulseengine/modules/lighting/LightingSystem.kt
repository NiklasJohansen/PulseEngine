package no.njoh.pulseengine.modules.lighting

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.api.AntiAliasing
import no.njoh.pulseengine.core.scene.SpatialGrid
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.modules.lighting.ShadowType.NONE
import no.njoh.pulseengine.core.scene.systems.EntityRenderer
import no.njoh.pulseengine.core.scene.systems.EntityRenderer.RenderPass
import no.njoh.pulseengine.core.shared.utils.MathUtil
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Property
import kotlin.math.*

@Name("Lighting 2.0")
open class LightingSystem : SceneSystem()
{
    @Property
    var ambientColor = Color(0.01f, 0.01f, 0.02f, 0.8f)

    @Property(min=0f, max=1f)
    var lightBleed = 0.97f

    @Property
    var msaa = AntiAliasing.NONE

    @JsonIgnore
    private lateinit var lightingEffect: LightingPostProcessingEffect

    @JsonIgnore
    private val normalRenderPass = RenderPass("lighting_normal_map", NormalMapRenderPassTarget::class)

    @JsonIgnore
    private val occluderRenderPass = RenderPass("lighting_occluder_map", LightOccluder::class)

    private var xMin = 0f
    private var yMin = 0f
    private var xMax = 0f
    private var yMax = 0f
    private var lightCount = 0
    private var edgeCount = 0

    override fun onCreate(engine: PulseEngine)
    {
        val normalMapSurface = engine.gfx
            .createSurface(normalRenderPass.surfaceName, camera = engine.gfx.mainCamera, antiAliasing = msaa)
            .setBackgroundColor(0.5f, 0.5f, 1.0f, 1f)
            .setIsVisible(false)

        val lightOccluderMap = engine.gfx
            .createSurface(occluderRenderPass.surfaceName, camera = engine.gfx.mainCamera)
            .setBackgroundColor(0f, 0f, 0f, 0f)
            .setIsVisible(false)

        // Add lighting as a post processing effect to main surface
        lightingEffect = LightingPostProcessingEffect(engine.gfx.mainCamera, normalMapSurface, lightOccluderMap)
        engine.gfx.mainSurface.addPostProcessingEffect(lightingEffect)

        // Add render passes for normal and occluder maps
        engine.scene.getSystemOfType<EntityRenderer>()?.apply {
            addRenderPass(normalRenderPass)
            addRenderPass(occluderRenderPass)
        }

        // Load icon if not already loaded
        val iconName = "icon_light_bulb"
        val icon = engine.asset.getSafe<Texture>(iconName)
        if (icon == null)
            engine.asset.loadTexture("/pulseengine/icons/icon_light_bulb.png", iconName)

        engine.data.addMetric("Lights", "") { lightCount.toFloat() }
        engine.data.addMetric("Edges", "") { edgeCount.toFloat() }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        lightingEffect.ambientColor = ambientColor
        lightingEffect.lightBleed = lightBleed
        engine.gfx.getSurface(normalRenderPass.surfaceName)?.setAntiAliasingType(msaa)
    }

    override fun onRender(engine: PulseEngine)
    {
        val topLeft = engine.gfx.mainCamera.topLeftWorldPosition
        val bottomRight = engine.gfx.mainCamera.bottomRightWorldPosition
        xMin = topLeft.x
        yMin = topLeft.y
        xMax = bottomRight.x
        yMax = bottomRight.y
        lightCount = 0
        edgeCount = 0

        engine.scene.forEachEntityTypeList { entities ->
            val firstEntity = entities[0]
            if (firstEntity is LightSource)
                addLightsSources(entities, engine)
        }
    }

    private fun addLightsSources(lightSources: SwapList<SceneEntity>, engine: PulseEngine)
    {
        val extraPadding = 0
        val xTopLeft = xMin - extraPadding
        val yTopLeft = yMin - extraPadding
        val xBottomRight = xMax + extraPadding
        val yBottomRight = yMax + extraPadding
        val edgeQueryId = SpatialGrid.nextQueryId()

        lightSources.forEachFast { entity ->
            val l = entity as LightSource
            if (l.intensity != 0f && l.isIntersectingRectangle(xTopLeft, yTopLeft, xBottomRight, yBottomRight))
            {
                val direction = PI.toFloat() + l.rotation.toRadians()
                lightingEffect.addLight(
                    x = l.x,
                    y = l.y,
                    z = l.z,
                    radius = l.radius,
                    direction = direction,
                    coneAngle = 0.5f * l.coneAngle.toRadians(),
                    size = l.size,
                    intensity = l.intensity,
                    red = l.color.red,
                    green = l.color.green,
                    blue = l.color.blue,
                    lightType = l.type,
                    shadowType = l.shadowType
                )
                lightCount++

                if (l.shadowType != NONE)
                {
                    engine.scene.forEachNearbyEntityOfType<LightOccluder>(l.x, l.y, l.radius, l.radius, edgeQueryId)
                    {
                        if (it.castShadows)
                            addOccluderEdges(it.shape)
                    }
                }
            }
        }
    }

    private fun LightSource.isIntersectingRectangle(xTopLeft: Float, yTopLeft: Float, xBottomRight: Float, yBottomRight: Float) =
        when (type)
        {
            LightType.RADIAL ->
            {
                val xDelta = x - max(xTopLeft, min(x, xBottomRight))
                val yDelta = y - max(yTopLeft, min(y, yBottomRight))
                (xDelta * xDelta + yDelta * yDelta) < (radius * radius)
            }
            LightType.LINEAR ->
            {
                val xScreenCenter = (xTopLeft + xBottomRight) * 0.5f
                val yScreenCenter = (yTopLeft + yBottomRight) * 0.5f
                val angle = PI.toFloat() + rotation.toRadians()
                val xHalf = cos(-angle) * size * 0.5f
                val yHalf = sin(-angle) * size * 0.5f
                val x0 = x - xHalf
                val y0 = y - yHalf
                val x1 = x + xHalf
                val y1 = y + yHalf
                val closestPoint = MathUtil.closestPointOnLineSegment(xScreenCenter, yScreenCenter, x0, y0, x1, y1)
                val xDelta = closestPoint.x - max(xTopLeft, min(closestPoint.x, xBottomRight))
                val yDelta = closestPoint.y - max(yTopLeft, min(closestPoint.y, yBottomRight))
                (xDelta * xDelta + yDelta * yDelta) < (radius * radius)
            }
        }

    private fun addOccluderEdges(shape: Shape)
    {
        val pointCount = shape.getPointCount()
        if (pointCount == 1)
        {
            shape.getRadius()?.let { radius ->
                val points = ceil(2f * radius / 100f).toInt().coerceAtLeast(10)
                val center = shape.getPoint(0) ?: return
                var xLast = center.x + radius
                var yLast = center.y
                for (i in 1 until points + 1)
                {
                    val angle = i / points.toFloat() * 2 * PI
                    val x = center.x + cos(angle).toFloat() * radius
                    val y = center.y + sin(angle).toFloat() * radius
                    addEdge(xLast, yLast, x, y)
                    xLast = x
                    yLast = y
                }
            }
        }
        else if (pointCount == 2)
        {
            var p = shape.getPoint(0) ?: return
            val x0 = p.x
            val y0 = p.y
            p = shape.getPoint(1) ?: return
            addEdge(x0, y0, p.x, p.y)
        }
        else
        {
            val lastPoint = shape.getPoint(pointCount - 1) ?: return
            var xLast = lastPoint.x
            var yLast = lastPoint.y
            for (i in 0 until pointCount)
            {
                val point = shape.getPoint(i) ?: break
                addEdge(xLast, yLast, point.x, point.y)
                xLast = point.x
                yLast = point.y
            }
        }
    }

    private fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float)
    {
        lightingEffect.addEdge(x0, y0, x1, y1)
        edgeCount++
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.scene.getSystemOfType<EntityRenderer>()?.apply {
            removeRenderPass(normalRenderPass)
            removeRenderPass(occluderRenderPass)
        }
        engine.gfx.deleteSurface(normalRenderPass.surfaceName)
        engine.gfx.deleteSurface(occluderRenderPass.surfaceName)
        engine.gfx.mainSurface.removePostProcessingEffect(lightingEffect)
        lightingEffect.cleanUp()
    }
}