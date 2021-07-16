package no.njoh.pulseengine.modules.scene.systems.lighting

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.Color
import no.njoh.pulseengine.data.SwapList
import no.njoh.pulseengine.modules.graphics.postprocessing.effects.BlurEffect
import no.njoh.pulseengine.modules.graphics.postprocessing.effects.LightingEffect
import no.njoh.pulseengine.modules.graphics.postprocessing.effects.MultiplyEffect
import no.njoh.pulseengine.modules.scene.entities.SceneEntity
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.widgets.sceneEditor.Name
import no.njoh.pulseengine.widgets.sceneEditor.ValueRange
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

@Name("Lighting")
open class LightingSystem : SceneSystem()
{
    var ambientColor = Color(0.2f, 0.2f, 0.22f, 1f)

    @ValueRange(0f, 10f)
    var shadowSoftness = 0.1f

    @JsonIgnore
    private lateinit var lightingEffect: LightingEffect

    @JsonIgnore
    private lateinit var multiplyEffect: MultiplyEffect

    @JsonIgnore
    private lateinit var blurEffect: BlurEffect

    override fun onCreate(engine: PulseEngine)
    {
        blurEffect = BlurEffect(radius = shadowSoftness)
        lightingEffect = LightingEffect(engine.gfx.mainCamera)

        val lightMask = engine.gfx
            .createSurface("lightMask")
            .setBackgroundColor(ambientColor)
            .addPostProcessingEffect(lightingEffect)
            .addPostProcessingEffect(blurEffect)
            .setIsVisible(false)

        multiplyEffect = MultiplyEffect(lightMask)

        engine.gfx.mainSurface
            .addPostProcessingEffect(multiplyEffect)
    }

    override fun onUpdate(engine: PulseEngine)
    {
        blurEffect.radius = shadowSoftness
        engine.gfx
            .getSurfaceOrDefault("lightMask")
            .setBackgroundColor(ambientColor)
    }

    override fun onRender(engine: PulseEngine)
    {
        engine.scene.forEachEntityTypeList { typeList ->
            if (typeList.isNotEmpty())
            {
                val firstEntity = typeList[0]
                if (firstEntity is LightSource)
                    updateLights(typeList)
                else if (firstEntity is LightOccluder)
                    updateLightOccluders(typeList)
            }
        }
    }

    private fun updateLights(lightEntities: SwapList<SceneEntity>)
    {
        lightEntities.forEachFast { entity ->
            val l = entity as LightSource
            lightingEffect.addLight(l.x, l.y, l.radius, l.intensity, 0f, l.color.red, l.color.green, l.color.blue)
        }
    }

    private fun updateLightOccluders(lightOccluderEntities: SwapList<SceneEntity>)
    {
        lightOccluderEntities.forEachFast { entity ->
            val shape = (entity as LightOccluder).shape
            val pointCount = shape.getPointCount()
            if (pointCount == 1) {
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
                        lightingEffect.addEdge(xLast, yLast, x, y)
                        xLast = x
                        yLast = y
                    }
                }
            }
            else
            {
                val lastPoint = shape.getPoint(pointCount - 1) ?: return
                var xLast = lastPoint.x
                var yLast = lastPoint.y
                for (i in 0 until pointCount)
                {
                    val point = shape.getPoint(i) ?: break
                    lightingEffect.addEdge(xLast, yLast, point.x, point.y)
                    xLast = point.x
                    yLast = point.y
                }
            }
        }
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.getSurfaceOrDefault("lightMask")
            .removePostProcessingEffect(lightingEffect)
            .removePostProcessingEffect(blurEffect)
        engine.gfx.removeSurface("lightMask")
        engine.gfx.mainSurface.removePostProcessingEffect(multiplyEffect)

        lightingEffect.cleanUp()
        multiplyEffect.cleanUp()
        blurEffect.cleanUp()
    }
}