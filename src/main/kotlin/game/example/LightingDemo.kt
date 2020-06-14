package game.example

import engine.PulseEngine
import engine.data.Mouse
import engine.data.Texture
import engine.modules.Game
import engine.modules.graphics.postprocessing.effects.*
import engine.util.Camera2DController
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun main() = PulseEngine.run(LightingDemo())

class LightingDemo : Game()
{
    private lateinit var lightingEffect: LightingEffect

    private val camControl = Camera2DController(Mouse.MIDDLE, smoothing = 0f)
    private val boxes = mutableListOf<Box>()
    private val lights = mutableListOf<Light>()

    private var angle = 0f
    private var drawnLights = 0f
    private var drawnEdges = 0f

    override fun init()
    {
        lightingEffect = LightingEffect(engine.gfx.mainCamera)

        val lightMask = engine.gfx
            .createSurface2D("lightMask")
            .setBackgroundColor(0.02f, 0.02f, 0.02f, 1f)
            .addPostProcessingEffect(lightingEffect)
            .addPostProcessingEffect(BlurEffect(radius = 0.1f))
            .setIsVisible(false)

        engine.gfx.mainSurface
            .setBackgroundColor(1f, 1f, 1f, 1f)
            .addPostProcessingEffect(MultiplyEffect(lightMask))

        engine.gfx.createSurface2D("objects", camera = engine.gfx.mainCamera)
        engine.asset.loadTexture("/box.png","box")

        engine.data.addSource("Total Lights","") { lights.size.toFloat() }
        engine.data.addSource("Drawn lights","") { drawnLights }
        engine.data.addSource("Total Edges","")  { boxes.size.toFloat() * 4 }
        engine.data.addSource("Drawn edges","")  { drawnEdges }
        engine.data.loadAsync<GameState>("game_state.dat") {
            boxes.addAll(it.boxes)
            lights.addAll(it.lights)
        }
    }

    override fun fixedUpdate()
    {
        angle = (angle + 0.02f + 0.08f * Random.nextFloat()) % (2f * PI.toFloat())
    }

    override fun update()
    {
        camControl.update(engine)

        if (engine.input.wasClicked(Mouse.RIGHT))
        {
            boxes.add(Box(engine.input.xWorldMouse, engine.input.yWorldMouse, 50 + 200f * Random.nextFloat(), 50 + 200f * Random.nextFloat()))
            saveState()
        }

        if (engine.input.wasClicked(Mouse.LEFT))
        {
            lights.add(Light(
                x = engine.input.xWorldMouse,
                y = engine.input.yWorldMouse,
                radius = 500f + 2000f * Random.nextFloat(),
                intensity = 0.7f,
                type = 0f,
                red = 0.7f + 0.3f * Random.nextFloat(),
                green = 0.7f + 0.3f * Random.nextFloat(),
                blue = 0.7f + 0.3f * Random.nextFloat())
            )
            saveState()
        }
    }

    override fun render()
    {
        drawnLights = 0f
        drawnEdges = 0f

        val boxTexture: Texture = engine.asset.get("box")
        val cam = engine.gfx.mainCamera
        val objSurface = engine.gfx
            .getSurface2D("objects")
            .setDrawColor(0.2f, 0.2f, 0.2f)

        for(box in boxes)
        {
            if (cam.isInView(box.x, box.y, box.w, box.h, padding = 500f))
            {
                drawnEdges += 4
                lightingEffect.addEdge(box.x, box.y, box.x + box.w, box.y)
                lightingEffect.addEdge(box.x, box.y, box.x, box.y + box.h)
                lightingEffect.addEdge(box.x, box.y + box.h, box.x + box.w, box.y + box.h)
                lightingEffect.addEdge(box.x + box.w, box.y, box.x + box.w, box.y + box.h)
                objSurface.drawTexture(boxTexture, box.x, box.y, box.w, box.h)

            }
        }

        for(light in lights)
        {
            if (cam.isInView(light.x - light.radius, light.y - light.radius, light.radius * 2, light.radius * 2))
            {
                drawnLights++
                lightingEffect.addLight(
                    x = light.x + sin(angle + light.radius) * light.radius / 100f,
                    y = light.y + cos(angle + light.radius) * light.radius / 100f,
                    radius = light.radius,
                    intensity = light.intensity,
                    type = light.type,
                    red = light.red,
                    green = light.green,
                    blue = light.blue)
            }
        }
    }

    private fun saveState()
    {
        engine.data.saveAsync(GameState(boxes, lights), "game_state.dat")
    }

    override fun cleanup() { }
}

data class GameState(
    val boxes: List<Box>,
    val lights: List<Light>
)

data class Box(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

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