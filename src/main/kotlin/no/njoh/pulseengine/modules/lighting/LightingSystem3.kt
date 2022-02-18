package no.njoh.pulseengine.modules.lighting

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.core.shared.primitives.SwapList
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.TextureFilter.BILINEAR_INTERPOLATION
import no.njoh.pulseengine.core.graphics.api.TextureFormat.HDR_16
import no.njoh.pulseengine.core.graphics.api.AntiAliasing
import no.njoh.pulseengine.core.graphics.api.BlendFunction.ADDITIVE
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.modules.lighting.ShadowType.NONE
import no.njoh.pulseengine.core.scene.systems.EntityRenderer
import no.njoh.pulseengine.core.scene.systems.EntityRenderer.RenderPass
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.MathUtil
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Property
import kotlin.math.*

@Name("Lighting 3.0")
open class LightingSystem3 : SceneSystem()
{
    @Property(order = 1)
    var ambientColor = Color(0.01f, 0.01f, 0.02f, 0.8f)

    @Property(order = 2, min = 0.1f, max = 5.0f)
    var textureScale: Float = 1f

    @Property(order = 3)
    var textureFilter: TextureFilter = BILINEAR_INTERPOLATION

    @Property(order = 4)
    var textureFormat: TextureFormat = HDR_16

    @Property(order = 5)
    var antiAliasing = AntiAliasing.NONE

    @Property(order = 6)
    var useNormals = true

    @Property(order = 7)
    var lightSpill = true

    @Property(order = 8)
    var correctOffset = false

    @Property(order = 9)
    var drawOutlines = false

    @JsonIgnore
    private val normalMapRenderPass = RenderPass(
        surfaceName = "lighting_normal_map",
        targetType = NormalMapRenderPassTarget::class
    )

    @JsonIgnore
    private val occluderRenderPass = RenderPass(
        surfaceName = "lighting_occluder_map",
        targetType = LightOccluder::class
    )

    private var xMin = 0f
    private var yMin = 0f
    private var xMax = 0f
    private var yMax = 0f
    private var lightCount = 0
    private var shadowCasterCount = 0
    private var edgeCount = 0
    private var renderTimeMs = 0f

    private lateinit var lightSurface: Surface2D
    private lateinit var lightRenderer: LightRenderer
    private lateinit var lightBlendEffect: LightBlendEffect

    override fun onCreate(engine: PulseEngine)
    {
        lightRenderer = LightRenderer()
        lightSurface = engine.gfx.createSurface(
            name = "lighting_v3",
            camera = engine.gfx.mainCamera,
            isVisible = false,
            backgroundColor = Color(0f, 0f, 0f, 0f),
            textureFormat = textureFormat,
            textureFilter = textureFilter,
            textureScale = textureScale,
            antiAliasing = antiAliasing,
            blendFunction = ADDITIVE,
            attachments = listOf(COLOR_TEXTURE_0)
        ).addRenderer(lightRenderer)

        // Add lighting as a post processing effect to main surface
        lightBlendEffect = LightBlendEffect(lightSurface, ambientColor)
        engine.gfx.mainSurface.addPostProcessingEffect(lightBlendEffect)

        // Configure maps
        configureNormalMap(engine, isEnabled = useNormals)
        configureOccluderMap(engine, isEnabled = lightSpill)

        // Load icon if not already loaded
        val iconName = "icon_light_bulb"
        val icon = engine.asset.getSafe<Texture>(iconName)
        if (icon == null)
            engine.asset.loadTexture("/pulseengine/icons/icon_light_bulb.png", iconName)

        engine.data.addMetric("Lights", "") { lightCount.toFloat() }
        engine.data.addMetric("Shadow casters", "") { shadowCasterCount.toFloat() }
        engine.data.addMetric("Edges", "") { edgeCount.toFloat() }
        engine.data.addMetric("Lighting total", "MS") { renderTimeMs + lightRenderer.renderTimeMs }
        engine.data.addMetric("Lighting build", "MS") { renderTimeMs }
    }

    private fun configureNormalMap(engine: PulseEngine, isEnabled: Boolean)
    {
        val currentNormalMapSurface = lightRenderer.normalMapSurface
        if (isEnabled && currentNormalMapSurface == null)
        {
            Logger.debug("Enabling normal maps for lighting")
            engine.scene.getSystemOfType<EntityRenderer>()?.addRenderPass(normalMapRenderPass)
            lightRenderer.normalMapSurface = engine.gfx.createSurface(
                name = normalMapRenderPass.surfaceName,
                camera = engine.gfx.mainCamera,
                zOrder = lightSurface.context.zOrder + 1, // Render normal map before lightmap
                backgroundColor = Color(0.5f, 0.5f, 1.0f, 1f),
                textureFilter = BILINEAR_INTERPOLATION,
                isVisible = false
            )
        }
        else if (!isEnabled && currentNormalMapSurface != null)
        {
            Logger.debug("Disabling normal maps for lighting")
            engine.scene.getSystemOfType<EntityRenderer>()?.removeRenderPass(normalMapRenderPass)
            engine.gfx.deleteSurface(currentNormalMapSurface.name)
            lightRenderer.normalMapSurface = null
        }
    }

    private fun configureOccluderMap(engine: PulseEngine, isEnabled: Boolean)
    {
        val currentOccluderMapSurface = lightRenderer.occluderMapSurface
        if (isEnabled && currentOccluderMapSurface == null)
        {
            Logger.debug("Enabling occluder map for lighting")
            engine.scene.getSystemOfType<EntityRenderer>()?.addRenderPass(occluderRenderPass)
            lightRenderer.occluderMapSurface = engine.gfx.createSurface(
                name = occluderRenderPass.surfaceName,
                camera = engine.gfx.mainCamera,
                zOrder = lightSurface.context.zOrder + 1, // Render occluder map before lightmap
                backgroundColor = Color(0f, 0f, 0f, 0f),
                isVisible = false
            )
        }
        else if (!isEnabled && currentOccluderMapSurface != null)
        {
            Logger.debug("Disabling occluder map for lighting")
            engine.scene.getSystemOfType<EntityRenderer>()?.removeRenderPass(occluderRenderPass)
            engine.gfx.deleteSurface(currentOccluderMapSurface.name)
            lightRenderer.occluderMapSurface = null
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        // Set light renderer properties
        lightRenderer.ambientColor = ambientColor

        // Set light surface properties
        lightSurface.setAntiAliasingType(antiAliasing)
        lightSurface.setTextureScale(textureScale)
        lightSurface.setTextureFilter(textureFilter)
        lightSurface.setTextureFormat(textureFormat)

        // Update normal and occluder map configurations
        configureNormalMap(engine, isEnabled = useNormals)
        configureOccluderMap(engine, isEnabled = lightSpill)
    }

    override fun onRender(engine: PulseEngine)
    {
        val startTime = System.nanoTime()
        shadowCasterCount = 0
        lightCount = 0
        edgeCount = 0

        updateLightMapPositionOffset()
        updateBoundingRect(lightSurface.width.toFloat(), lightSurface.height.toFloat())

        engine.scene.forEachEntityTypeList { entities ->
            val firstEntity = entities[0]
            if (firstEntity is LightSource)
                addLightsSources(entities, engine)
        }

        renderTimeMs = (System.nanoTime() - startTime) / 1_000_000f
    }

    private fun updateLightMapPositionOffset()
    {
        var xOffset = 0f
        var yOffset = 0f
        val pixelSize = 1f / textureScale
        if (correctOffset && textureScale != 1.0f)
        {
            val viewMatrix = lightSurface.camera.viewMatrix
            val xTranslation = viewMatrix.m30()
            val yTranslation = viewMatrix.m31()
            xOffset = xTranslation % pixelSize
            yOffset = yTranslation % pixelSize
        }
        lightBlendEffect.xSamplingOffset = -xOffset / lightSurface.width
        lightBlendEffect.ySamplingOffset = yOffset / lightSurface.height
        lightRenderer.xDrawOffset = xOffset
        lightRenderer.yDrawOffset = yOffset
    }

    private fun updateBoundingRect(screenWidth: Float, screenHeight: Float)
    {
        for (i in BOUNDING_COORDS.indices step 2)
        {
            val worldPos = lightSurface.camera.screenPosToWorldPos(
                x = screenWidth  * BOUNDING_COORDS[i],
                y = screenHeight * BOUNDING_COORDS[i + 1]
            )
            if (worldPos.x < xMin || i == 0) xMin = worldPos.x
            if (worldPos.x > xMax || i == 0) xMax = worldPos.x
            if (worldPos.y < yMin || i == 0) yMin = worldPos.y
            if (worldPos.y > yMax || i == 0) yMax = worldPos.y
        }
    }

    private fun addLightsSources(lightSources: SwapList<SceneEntity>, engine: PulseEngine)
    {
        lightSources.forEachFast { entity ->
            val light = entity as LightSource
            if (light.intensity != 0f && isInsideBoundingRectangle(light))
            {
                val size = light.radius * 1.7f
                val edgeIndex = edgeCount
                if (light.shadowType != NONE)
                {
                    engine.scene.forEachNearbyEntityOfType<LightOccluder>(light.x, light.y, size, size)
                    {
                        if (it.castShadows)
                        {
                            addOccluderEdges(it.shape)
                            shadowCasterCount++
                        }
                    }
                }

                lightRenderer.addLight(
                    x = light.x,
                    y = light.y,
                    z = light.z - 0.15f, // Make lights with 0 z-value visible
                    radius = light.radius,
                    direction = PI.toFloat() + light.rotation.toRadians(),
                    coneAngle = 0.5f * light.coneAngle.toRadians(),
                    sourceSize = light.size,
                    intensity = light.intensity,
                    red = light.color.red,
                    green = light.color.green,
                    blue = light.color.blue,
                    lightType = light.type,
                    shadowType = light.shadowType,
                    spill = light.spill,
                    edgeIndex = edgeIndex,
                    edgeCount = edgeCount - edgeIndex
                )
                lightCount++

                if (drawOutlines)
                    drawLightOutline(light)
            }
        }
    }

    private fun isInsideBoundingRectangle(light: LightSource) =
        when (light.type)
        {
            LightType.RADIAL ->
            {
                val xDelta = light.x - max(xMin, min(light.x, xMax))
                val yDelta = light.y - max(yMin, min(light.y, yMax))
                (xDelta * xDelta + yDelta * yDelta) < (light.radius * light.radius)
            }
            LightType.LINEAR ->
            {
                val xScreenCenter = (xMin + xMax) * 0.5f
                val yScreenCenter = (yMin + yMax) * 0.5f
                val angle = PI.toFloat() + light.rotation.toRadians()
                val xHalf = cos(-angle) * light.size * 0.5f
                val yHalf = sin(-angle) * light.size * 0.5f
                val closestPoint = MathUtil.closestPointOnLineSegment(
                    x = xScreenCenter,
                    y = yScreenCenter,
                    x0 = light.x - xHalf,
                    y0 = light.y - yHalf,
                    x1 = light.x + xHalf,
                    y1 = light.y + yHalf
                )
                val xDelta = closestPoint.x - max(xMin, min(closestPoint.x, xMax))
                val yDelta = closestPoint.y - max(yMin, min(closestPoint.y, yMax))
                (xDelta * xDelta + yDelta * yDelta) < (light.radius * light.radius)
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
        lightRenderer.addEdge(x0, y0, x1, y1)
        edgeCount++

        if (drawOutlines)
        {
            lightSurface.setDrawColor(1f, 0f, 0f)
            lightSurface.drawLine(x0, y0, x1, y1)
        }
    }

    private fun drawLightOutline(light: LightSource)
    {
        val nPoints = ceil(2f * light.radius / 10f).toInt().coerceAtLeast(10)
        var xLast = light.x + light.radius
        var yLast = light.y
        lightSurface.setDrawColor(1f, 1f, 0f)
        for (i in 1 until nPoints + 1)
        {
            val angle = i / nPoints.toFloat() * 2 * PI
            val x = light.x + cos(angle).toFloat() * light.radius
            val y = light.y + sin(angle).toFloat() * light.radius
            lightSurface.drawLine(x, y, xLast, yLast)
            xLast = x
            yLast = y
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        // Remove render passes
        val renderer = engine.scene.getSystemOfType<EntityRenderer>()
        renderer?.removeRenderPass(normalMapRenderPass)
        renderer?.removeRenderPass(occluderRenderPass)

        // Delete surfaces
        lightRenderer.normalMapSurface?.let { engine.gfx.deleteSurface(it.name) }
        lightRenderer.occluderMapSurface?.let { engine.gfx.deleteSurface(it.name) }
        engine.gfx.deleteSurface(lightSurface.name)

        // Remove and delete post processing effect
        engine.gfx.mainSurface.removePostProcessingEffect(lightBlendEffect)
        lightBlendEffect.cleanUp()
    }

    companion object
    {
        private val BOUNDING_COORDS = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    }
}