package engine.modules.graphics.renderers

import engine.modules.graphics.CameraEngineInterface
import engine.modules.graphics.GraphicsState

data class Renderer2D(
    val textureRenderer: TextureBatchRenderer,
    val quadRenderer: QuadBatchRenderer,
    val lineRenderer: LineBatchRenderer,
    val uniColorLineRenderer: UniColorLineBatchRenderer,
    val textRenderer: TextRenderer
){
    private val renderers = listOf(
        uniColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )

    fun init() = renderers.forEach { it.init() }
    fun cleanup() = renderers.forEach { it.cleanup() }
    fun render(camera: CameraEngineInterface) = renderers.forEach { it.render(camera) }

    companion object
    {
        fun createDefault(initCapacity: Int, gfxState: GraphicsState) =
            Renderer2D(
                TextureBatchRenderer(initCapacity, gfxState),
                QuadBatchRenderer(initCapacity, gfxState),
                LineBatchRenderer(initCapacity, gfxState),
                UniColorLineBatchRenderer(initCapacity, gfxState),
                TextRenderer()
            )
    }
}