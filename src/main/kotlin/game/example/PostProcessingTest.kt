package game.example

import engine.PulseEngine
import engine.data.Mouse
import engine.data.Texture
import engine.modules.Game
import engine.modules.graphics.BlendFunction
import engine.modules.graphics.postprocessing.effects.BloomEffect
import engine.modules.graphics.postprocessing.effects.BlurEffect
import engine.modules.graphics.postprocessing.effects.MultiplyEffect
import engine.modules.graphics.postprocessing.effects.LightingEffect
import engine.util.Camera2DController
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun main() = PulseEngine.run(object : Game()
{
    private val camControl = Camera2DController(Mouse.MIDDLE, smoothing = 0f)

    private lateinit var lightingEffect: LightingEffect

    private var boxes = mutableListOf<Box>()
    private var lights = mutableListOf<Light>()
    private var angle = 0f

    override fun init()
    {
        engine.data.addSource("Lights","") { lights.size.toFloat() }
        engine.data.addSource("Edges","") { boxes.size.toFloat() * 4 }

        lightingEffect = LightingEffect(engine.gfx.mainCamera)

        val lightMask = engine.gfx
            .createSurface2D("lightMask", camera = engine.gfx.mainCamera)
            .setBlendFunction(BlendFunction.SCREEN)
            .setBackgroundColor(0.01f, 0.01f, 0.01f, 1f)
            .addPostProcessingEffect(lightingEffect)
            .addPostProcessingEffect(BlurEffect(radius = 0.1f))
            .setIsVisible(false)

        engine.gfx.mainSurface
            .setBackgroundColor(0.0f, 0.0f, 0.0f, 0f)
            .addPostProcessingEffect(MultiplyEffect(lightMask))
            //.addPostProcessingEffect(BloomEffect(threshold = 0.2f, exposure = 2f, blurPasses = 3, blurRadius = 0.1f))

        engine.asset.loadTexture("/flashlight.png",  "flashlight")
        engine.asset.loadTexture("/box.png",         "box")
        engine.asset.loadTexture("/paper.png",       "paper")
    }

    override fun fixedUpdate()
    {
        angle = (angle + 0.1f) % (2f * PI.toFloat())
    }

    override fun update()
    {
        camControl.update(engine)

        if (engine.input.wasClicked(Mouse.RIGHT))
            boxes.add(Box(engine.input.xWorldMouse, engine.input.yWorldMouse, 50 + 200f * Random.nextFloat(), 50 + 200f * Random.nextFloat()))

        if (engine.input.wasClicked(Mouse.LEFT))
            lights.add(Light(
                engine.input.xWorldMouse,
                engine.input.yWorldMouse,
                1000f,
                0.8f,
                0f,
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat())
            )
    }

    override fun render()
    {
        val box: Texture = engine.asset.get("box")
        val paper: Texture = Texture.BLANK //engine.asset.get("paper")
        val surface = engine.gfx.mainSurface

        surface.setDrawColor(1f, 1f, 1f)
        surface.drawTexture(paper, 0f, 0f, 2024f, 2024f)
        surface.drawTexture(paper, 3000f, 0f, 2024f, 2024f)

        boxes.forEach {
            surface.drawTexture(box,it.x, it.y, it.w, it.h)
            lightingEffect.addEdge(it.x, it.y, it.x+it.w, it.y)
            lightingEffect.addEdge(it.x, it.y, it.x, it.y+it.h)
            lightingEffect.addEdge(it.x, it.y+it.h, it.x+it.w, it.y+it.h)
            lightingEffect.addEdge(it.x+it.w, it.y, it.x+it.w, it.y+it.h)
        }

        lights.forEach {
            lightingEffect.addLight(it.x + sin(angle)*100f, it.y + cos(angle)*100f, it.radius, it.intensity, it.type, it.red, it.green, it.blue)
        }
    }

    override fun cleanup() { }
})

data class Box(val x: Float, val y: Float, val w: Float, val h: Float)
data class Light(
    val x: Float,
    val y: Float,
    val radius: Float,
    val intensity: Float,
    val type: Float,
    val red: Float,
    val green: Float,
    val blue: Float
)

