package no.njoh.pulseengine.core.graphics.scene3d.renderers

import gnu.trove.list.array.TLongArrayList
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.gpu.FullscreenPass
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject.Companion.encodeObjectIdHigh
import no.njoh.pulseengine.core.graphics.gpu.buffer.InstanceBufferObject.Companion.encodeObjectIdLow
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgram
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.surface.SurfaceInternal
import no.njoh.pulseengine.core.graphics.surface.renderers.Renderer
import org.lwjgl.opengl.GL11.*

class ObjectOutlineRenderer(
    val objectIdSurfaceName: String,
    var outlineWidthPixels: Int = 1,
    override val order: Int = 5
) : Renderer() {

    private lateinit var program: ShaderProgram
    private lateinit var pass: FullscreenPass

    private var readObjectIds = TLongArrayList()
    private var writeObjectIds = TLongArrayList()

    override fun init(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        program = ShaderProgram.create(
            engine.asset.loadNow(VertexShader("/pulseengine/shaders/renderers/surface.vert")),
            engine.asset.loadNow(FragmentShader("/pulseengine/shaders/renderers/model_object_id_outline.frag"))
        )
        pass = FullscreenPass(program)
        pass.init()
    }

    override fun onInitFrame(engine: PulseEngineInternal, surface: SurfaceInternal)
    {
        writeObjectIds = readObjectIds.also { readObjectIds = writeObjectIds }
        writeObjectIds.resetQuick()

        if (readObjectIds.size() > 0) increaseBatchSize()
    }

    override fun onRenderBatch(engine: PulseEngineInternal, surface: SurfaceInternal, startIndex: Int, drawCount: Int)
    {
        if (startIndex != 0) return

        val objectIdTexture = engine.gfx.getSurface(objectIdSurfaceName)?.getTexture() ?: return

        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)

        program.bind()
        program.setUniformSampler("uObjectIdTexture", objectIdTexture, filter = TextureFilter.NEAREST)
        program.setUniform("uTextureSize", objectIdTexture.width.toFloat(), objectIdTexture.height.toFloat())
        program.setUniform("uOutlineWidth", outlineWidthPixels.coerceAtLeast(1))

        for (objectId in readObjectIds)
        {
            program.setUniform("uSelectedId", encodeObjectIdLow(objectId), encodeObjectIdHigh(objectId))
            pass.draw()
        }

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun destroy(engine: PulseEngineInternal)
    {
        pass.destroy()
        program.destroy()
    }

    fun setSelected(objectId: Long)
    {
        if (objectId >= 0L) writeObjectIds.add(objectId)
    }
}