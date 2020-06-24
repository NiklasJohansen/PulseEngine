package game.example

import engine.PulseEngine
import engine.data.Color
import engine.data.Mouse
import engine.modules.Game
import engine.util.Camera2DController
import engine.util.Chunk
import engine.util.ChunkManager
import kotlin.random.Random

fun main() = PulseEngine.run(ChunkExample())

class ChunkExample : Game()
{
    private lateinit var keyVault: KeyVault
    private val camControl = Camera2DController(Mouse.MIDDLE)
    private val chunkManager =
        ChunkManager<ExampleChunk>(
            chunkSize = 1000,
            minSurroundingLoadedChunkBorder = 5,
            activeOfScreenChunkBorder = 1
        )

    override fun init()
    {
        keyVault = engine.data.load<KeyVault>("/chunk_keys.dat") ?: KeyVault(HashSet())
        chunkManager.setOnChunkLoad(this::onChunkLoad)
        chunkManager.setOnChunkSave(this::onChunkSave)
        chunkManager.init()

        engine.data.addSource("SAVED CHUNKS", "") { keyVault.keys.size.toFloat() }
        engine.data.addSource("LOADED CHUNKS", "") { chunkManager.getLoadedChunkCount().toFloat()}
        engine.data.addSource("VISIBLE CHUNKS", "") { chunkManager.getVisibleChunkCount().toFloat()}
        engine.data.addSource("ACTIVE CHUNKS", "") { chunkManager.getActiveChunkCount().toFloat()}
    }

    override fun update()
    {
        camControl.update(engine)
        chunkManager.update(engine.gfx.mainCamera)

        for(chunk in chunkManager.getActiveChunks())
        {
            if (chunk.insideAABB(engine.input.xWorldMouse, engine.input.yWorldMouse))
            {
                if(chunk.color == null && engine.input.isPressed(Mouse.LEFT))
                    chunk.color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
                else if (engine.input.isPressed(Mouse.RIGHT))
                    chunk.color = null
            }
        }
    }

    override fun render()
    {
        for (chunk in chunkManager.getVisibleChunks())
        {
            val size = chunkManager.chunkSize.toFloat()
            val x = chunk.x * size
            val y = chunk.y * size
            chunk.color?.let { color ->
                engine.gfx.mainSurface.setDrawColor(color.red, color.green, color.blue)
                engine.gfx.mainSurface.drawQuad(x, y, size, size)
            }
        }

        chunkManager.renderDebug(engine.gfx)
    }

    private fun onChunkLoad(xIndex: Int, yIndex: Int): ExampleChunk
    {
        val key = "$xIndex#$yIndex"
        return ExampleChunk(xIndex, yIndex)
            .also { chunk ->
                if (keyVault.has(key))
                    engine.data.loadAsync<ExampleChunk>("/chunks/$key.dat") { chunk.color = it.color }
            }
    }

    private fun onChunkSave(chunk: ExampleChunk, xIndex: Int, yIndex: Int)
    {
        val key = "$xIndex#$yIndex"
        keyVault.add(key)
        engine.data.saveAsync(chunk, "/chunks/$key.dat")
    }

    override fun cleanup()
    {
        for (chunk in chunkManager.getLoadedChunks())
        {
            if (chunk.hasData())
            {
                val key = "${chunk.x}#${chunk.y}"
                keyVault.add(key)
                engine.data.save(chunk, "/chunks/$key.dat")
            }
        }
        engine.data.save(keyVault, "/chunk_keys.dat")
    }

    private fun Chunk.insideAABB(x: Float, y: Float): Boolean
    {
        val size = chunkManager.chunkSize
        return x >= this.x * size && y >= this.y * size && x <= this.x * size + size && y < this.y * size + size
    }
}

data class KeyVault(
    val keys: HashSet<String>
) {
    fun has(key: String): Boolean = keys.contains(key)
    fun add(key: String) = keys.add(key)
}

class ExampleChunk(
    override val x: Int,
    override val y: Int,
    var color: Color? = null
) : Chunk {
    override fun hasData(): Boolean =
        color != null
}