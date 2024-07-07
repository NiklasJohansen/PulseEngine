package no.njoh.pulseengine.modules.lighting

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Shape
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.*
import no.njoh.pulseengine.core.graphics.api.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.api.BlendFunction.ADDITIVE
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.modules.lighting.ShadowType.NONE
import no.njoh.pulseengine.core.scene.systems.EntityRenderer
import no.njoh.pulseengine.core.scene.systems.EntityRenderer.RenderPass
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.MathUtil
import no.njoh.pulseengine.core.shared.utils.Extensions.toRadians
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.modules.lighting.LightType.*
import org.joml.Vector2f
import kotlin.math.*

@Name("Lighting")
@Icon("LIGHT_BULB")
open class LightingSystem : SceneSystem()
{
    @Prop(i = 1)                        var ambientColor = Color(0.01f, 0.01f, 0.02f, 0.8f)
    @Prop(i = 2, min = 0f, max = 10f)   var dithering = 0.7f
    @Prop(i = 3, min = 0f, max = 100f)  var fogIntensity = 0f
    @Prop(i = 4, min = 0f, max = 100f)  var fogTurbulence = 1.5f
    @Prop(i = 5, min = 0f, max = 5f)    var fogScale = 0.3f
    @Prop(i = 6, min = 0.01f, max = 5f) var textureScale = 0.4f
    @Prop(i = 7)                        var textureFilter = TextureFilter.LINEAR
    @Prop(i = 8)                        var textureFormat = TextureFormat.RGBA16F
    @Prop(i = 9)                        var multisampling = Multisampling.NONE
    @Prop(i = 10)                       var enableFXAA = true
    @Prop(i = 11)                       var useNormalMap = false
    @Prop(i = 12)                       var enableLightSpill = true
    @Prop(i = 13)                       var correctOffset = true
    @Prop(i = 14)                       var targetSurfaces = "main"
    @Prop(i = 15)                       var drawDebug = false

    private var xMin = 0f
    private var yMin = 0f
    private var xMax = 0f
    private var yMax = 0f
    private var xSamplingOffset = 0f
    private var ySamplingOffset = 0f
    private var lightCount = 0
    private var shadowCasterCount = 0
    private var edgeCount = 0
    private var cpuRenderTimeMs = 0f
    private var gpuRenderTimeMs = 0f
    private var isUsingNormalMap = false
    private var isUsingOccluderMap = false
    private var lastTargetSurfaces = ""
    private var postEffectSurfaces = mutableListOf<String>()

    private val normalMapRenderPass = RenderPass(
        surfaceName = NORMAL_SURFACE_NAME,
        targetType = NormalMapped::class.java
    )

    private val occluderRenderPass = RenderPass(
        surfaceName = OCCLUDER_SURFACE_NAME,
        targetType = LightOccluder::class.java,
        drawCondition = { (it as? LightOccluder)?.castShadows ?: true }
    )

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = LIGHT_SURFACE_NAME,
            camera = engine.gfx.mainCamera,
            isVisible = false,
            backgroundColor = Color.BLANK,
            textureFormat = textureFormat,
            textureFilter = textureFilter,
            textureScale = textureScale,
            multisampling = multisampling,
            blendFunction = ADDITIVE,
            attachments = listOf(COLOR_TEXTURE_0)
        ).also {
            it.addRenderer(LightRenderer())
            configureNormalMap(engine, it, useNormalMap)
            configureOccluderMap(engine, it, enableLightSpill)
        }

        // Add metrics
        engine.data.addMetric("Lights")              { sample(lightCount.toFloat())        }
        engine.data.addMetric("Edges")               { sample(edgeCount.toFloat())         }
        engine.data.addMetric("Shadow casters")      { sample(shadowCasterCount.toFloat()) }
        engine.data.addMetric("Lighting CPU (MS)", ) { sample(cpuRenderTimeMs)             }
        engine.data.addMetric("Lighting GPU (MS)", ) { sample(gpuRenderTimeMs)             }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val lightSurface = engine.gfx.getSurface(LIGHT_SURFACE_NAME) ?: return
        val lightRenderer = lightSurface.getRenderer<LightRenderer>() ?: return

        lightSurface.setMultisampling(multisampling)
        lightSurface.setTextureScale(textureScale)
        lightSurface.setTextureFilter(textureFilter)
        lightSurface.setTextureFormat(textureFormat)

        lightRenderer.ambientColor = ambientColor
        lightRenderer.normalMapTextureHandle = engine.gfx.getSurface(NORMAL_SURFACE_NAME)?.getTexture()?.handle
        lightRenderer.occluderMapTextureHandle = engine.gfx.getSurface(OCCLUDER_SURFACE_NAME)?.getTexture()?.handle

        updatePostEffect(engine, lightSurface)
        configureNormalMap(engine, lightSurface, isEnabled = useNormalMap)
        configureOccluderMap(engine, lightSurface, isEnabled = enableLightSpill)
    }

    override fun onRender(engine: PulseEngine)
    {
        val lightSurface = engine.gfx.getSurface(LIGHT_SURFACE_NAME) ?: return
        val lightRenderer = lightSurface.getRenderer<LightRenderer>() ?: return
        val startTime = System.nanoTime()

        shadowCasterCount = 0
        lightCount = 0
        edgeCount = 0

        updateLightMapPositionOffset(engine, lightSurface, lightRenderer)
        updateBoundingRect(lightSurface)
        engine.scene.forEachEntityOfType<LightSource>()
        {
            addLightsSources(it, lightRenderer, lightSurface, engine)
        }

        cpuRenderTimeMs = (System.nanoTime() - startTime) / 1_000_000f
        gpuRenderTimeMs = lightRenderer.gpuRenderTimeMs
    }

    override fun onDestroy(engine: PulseEngine)
    {
        // Remove render passes
        val renderer = engine.scene.getSystemOfType<EntityRenderer>()
        renderer?.removeRenderPass(normalMapRenderPass)
        renderer?.removeRenderPass(occluderRenderPass)

        // Delete surfaces
        engine.gfx.deleteSurface(LIGHT_SURFACE_NAME)
        engine.gfx.deleteSurface(NORMAL_SURFACE_NAME)
        engine.gfx.deleteSurface(OCCLUDER_SURFACE_NAME)

        // Remove and delete post-processing effect
        postEffectSurfaces.forEachFast { engine.gfx.getSurface(it)?.deletePostProcessingEffect(POST_EFFECT_NAME) }
        postEffectSurfaces.clear()
        lastTargetSurfaces = ""
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    private fun configureNormalMap(engine: PulseEngine, lightSurface: Surface, isEnabled: Boolean)
    {
        if (isEnabled && !isUsingNormalMap)
        {
            Logger.debug("LightingSystem: enabling normal maps")
            engine.scene.getSystemOfType<EntityRenderer>()?.addRenderPass(normalMapRenderPass)
            val surface = engine.gfx.createSurface(
                name = NORMAL_SURFACE_NAME,
                camera = engine.gfx.mainCamera,
                zOrder = lightSurface.config.zOrder + 1, // Render normal map before lightmap
                backgroundColor = Color(0.5f, 0.5f, 1.0f, 1f),
                textureFormat = TextureFormat.RGBA16F,
                isVisible = false
            )
            val surfaceConfig = (surface as SurfaceInternal).config
            val textureBank = (engine.gfx as GraphicsInternal).textureBank
            surface.addRenderer(NormalMapRenderer(surfaceConfig, textureBank))
            isUsingNormalMap = true
        }
        else if (!isEnabled && isUsingNormalMap)
        {
            Logger.debug("LightingSystem: disabling normal maps")
            engine.scene.getSystemOfType<EntityRenderer>()?.removeRenderPass(normalMapRenderPass)
            engine.gfx.deleteSurface(NORMAL_SURFACE_NAME)
            isUsingNormalMap = false
        }
    }

    private fun configureOccluderMap(engine: PulseEngine, lightSurface: Surface, isEnabled: Boolean)
    {
        if (isEnabled && !isUsingOccluderMap)
        {
            Logger.debug("LightingSystem: enabling occluder map")
            engine.scene.getSystemOfType<EntityRenderer>()?.addRenderPass(occluderRenderPass)
            engine.gfx.createSurface(
                name = OCCLUDER_SURFACE_NAME,
                camera = engine.gfx.mainCamera,
                zOrder = lightSurface.config.zOrder + 1, // Render occluder map before lightmap
                backgroundColor = Color(0f, 0f, 0f, 0f),
                isVisible = false
            )
            isUsingOccluderMap = true
        }
        else if (!isEnabled && isUsingOccluderMap)
        {
            Logger.debug("LightingSystem: disabling occluder map")
            engine.scene.getSystemOfType<EntityRenderer>()?.removeRenderPass(occluderRenderPass)
            engine.gfx.deleteSurface(OCCLUDER_SURFACE_NAME)
            isUsingOccluderMap = false
        }
    }

    private fun updatePostEffect(engine: PulseEngine, lightSurface: Surface)
    {
        if (targetSurfaces != lastTargetSurfaces)
        {
            postEffectSurfaces.clear()
            for (surface in engine.gfx.getSurfaces(lastTargetSurfaces))
            {
                surface.deletePostProcessingEffect(POST_EFFECT_NAME)
            }
            for (surface in engine.gfx.getSurfaces(targetSurfaces))
            {
                surface.addPostProcessingEffect(LightBlendEffect(POST_EFFECT_NAME, ambientColor, engine.gfx.mainCamera))
                postEffectSurfaces.add(surface.config.name)
            }
             lastTargetSurfaces = targetSurfaces
        }

        postEffectSurfaces.forEachPostEffect(engine)
        {
            it.lightMapTextureHandle = lightSurface.getTexture().handle
            it.enableFxaa = enableFXAA
            it.dithering = dithering
            it.fogIntensity = fogIntensity
            it.fogTurbulence = fogTurbulence
            it.fogScale = fogScale
        }
    }

    private fun updateLightMapPositionOffset(engine: PulseEngine, lightSurface: Surface, lightRenderer: LightRenderer)
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

        lightRenderer.xDrawOffset = xOffset
        lightRenderer.yDrawOffset = yOffset
        xSamplingOffset = xOffset / lightSurface.config.width
        ySamplingOffset = yOffset / lightSurface.config.height

        postEffectSurfaces.forEachPostEffect(engine)
        {
            it.xSamplingOffset = xSamplingOffset
            it.ySamplingOffset = ySamplingOffset
        }
    }

    private fun updateBoundingRect(lightSurface: Surface)
    {
        val screenWidth = lightSurface.config.width.toFloat()
        val screenHeight = lightSurface.config.height.toFloat()
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

    private fun addLightsSources(light: LightSource, lightRenderer: LightRenderer, lightSurface: Surface, engine: PulseEngine)
    {
        if ((light as SceneEntity).isSet(HIDDEN) || light.intensity == 0f || !isInsideBoundingRectangle(light))
            return

        val edgeIndex = edgeCount
        if (light.shadowType != NONE)
        {
            engine.scene.forEachEntityNearbyOfType<LightOccluder>(
                x = light.x,
                y = light.y,
                width = light.radius * 1.7f + if (light.type == LINEAR) light.size * 2 else 0f,
                height = light.radius * 1.7f,
                rotation = light.rotation
            ) {
                if (it.castShadows && (it as SceneEntity).isNot(HIDDEN))
                {
                    addOccluderEdges(it.shape, lightRenderer, lightSurface)
                    shadowCasterCount++
                }
            }
        }

        lightCount++
        lightRenderer.addLight(
            x = light.x,
            y = light.y,
            z = light.z,
            radius = light.radius,
            direction = light.rotation.toRadians(),
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

        if (drawDebug)
            drawDebugOutline(light, lightSurface)
    }

    private fun isInsideBoundingRectangle(light: LightSource) =
        when (light.type)
        {
            RADIAL ->
            {
                val xDelta = light.x - max(xMin, min(light.x, xMax))
                val yDelta = light.y - max(yMin, min(light.y, yMax))
                (xDelta * xDelta + yDelta * yDelta) < (light.radius * light.radius)
            }
            LINEAR ->
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

    private fun addOccluderEdges(shape: Shape, lightRenderer: LightRenderer, lightSurface: Surface)
    {
        val pointCount = shape.getPointCount()
        if (pointCount == 1)
        {
            val radius = shape.getRadius() ?: return
            val points = max(7, min(15, ceil(2f * radius / 20f).toInt()))
            val center = shape.getPoint(0)
            var xLast = center.x + radius
            var yLast = center.y
            for (i in 1 until points + 1)
            {
                val angle = i / points.toFloat() * 2 * PI
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius
                addEdge(xLast, yLast, x, y, lightRenderer, lightSurface)
                xLast = x
                yLast = y
            }
        }
        else if (pointCount == 2)
        {
            var p = shape.getPoint(0)
            val x0 = p.x
            val y0 = p.y
            p = shape.getPoint(1)
            addEdge(x0, y0, p.x, p.y, lightRenderer, lightSurface)
        }
        else
        {
            val lastPoint = shape.getPoint(pointCount - 1)
            var xLast = lastPoint.x
            var yLast = lastPoint.y
            for (i in 0 until pointCount)
            {
                val point = shape.getPoint(i)
                addEdge(xLast, yLast, point.x, point.y, lightRenderer, lightSurface)
                xLast = point.x
                yLast = point.y
            }
        }
    }

    private fun addEdge(x0: Float, y0: Float, x1: Float, y1: Float, lightRenderer: LightRenderer, lightSurface: Surface)
    {
        lightRenderer.addEdge(x0, y0, x1, y1)
        edgeCount++

        if (drawDebug)
        {
            lightSurface.setDrawColor(1f, 0f, 0f, 0.2f)
            lightSurface.drawLine(x0, y0, x1, y1)
        }
    }

    private fun drawDebugOutline(light: LightSource, lightSurface: Surface)
    {
        lightSurface.setDrawColor(light.color.red, light.color.green, light.color.blue, 0.3f)

        // Draw outline
        val size = if (light.type == LINEAR) light.size else 0f
        val rotation = -light.rotation.toRadians()
        val xOffset = size * cos(rotation)
        val yOffset = size * sin(rotation)
        var xLast = light.x + cos(0f + rotation) * light.radius + xOffset
        var yLast = light.y + sin(0f + rotation) * light.radius + yOffset
        val pi = PI.toFloat()
        val nPoints = max(10, ceil(2f * light.radius / 10f).toInt())
        for (i in 1 until nPoints + 1)
        {
            val angle = i / nPoints.toFloat() * 2f * pi
            val dir = if (angle > pi * 0.5 && angle < pi * 1.5) -1 else 1
            val x = light.x + light.radius * cos(rotation - angle) + xOffset * dir
            val y = light.y + light.radius * sin(rotation - angle) + yOffset * dir
            lightSurface.drawLine(x, y, xLast, yLast)
            xLast = x
            yLast = y
        }

        // Draw fill
        val width = light.radius * 2 + if (light.type == LINEAR) light.size * 2 else 0f
        val height = light.radius * 2
        val cornerRadius = min(width, height)
        lightSurface.setDrawColor(light.color.red, light.color.green, light.color.blue, 0.02f)
        lightSurface.drawTexture(Texture.BLANK, light.x, light.y, width, height, light.rotation, 0.5f, 0.5f, cornerRadius = cornerRadius)
    }

    private fun Graphics.getSurfaces(surfaceNames: String) =
        surfaceNames.split(",").mapNotNull { getSurface(it.trim()) }

    private inline fun MutableList<String>.forEachPostEffect(engine: PulseEngine, action: (LightBlendEffect) -> Unit) =
        this.forEachFast { (engine.gfx.getSurface(it)?.getPostProcessingEffect(POST_EFFECT_NAME) as? LightBlendEffect)?.let(action) }

    fun getSamplingOffset() = SAMPLING_OFFSET.set(xSamplingOffset, ySamplingOffset)

    companion object
    {
        const val POST_EFFECT_NAME = "light_post_effect"
        const val LIGHT_SURFACE_NAME = "light_surface"
        const val NORMAL_SURFACE_NAME = "light_normal_map"
        const val OCCLUDER_SURFACE_NAME = "light_occluder_map"

        private val BOUNDING_COORDS = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        private val SAMPLING_OFFSET = Vector2f()
    }
}