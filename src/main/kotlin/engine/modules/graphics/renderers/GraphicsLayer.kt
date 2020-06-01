package engine.modules.graphics.renderers

import engine.modules.graphics.CameraEngineInterface
import engine.modules.graphics.GraphicsState
import engine.modules.graphics.RenderTarget

data class GraphicsLayer(
    val name: String,
    val layerType: LayerType,
    val renderTarget: RenderTarget,
    val textRenderer: TextRenderer,
    val uniColorLineRenderer: UniColorLineBatchRenderer,
    val quadRenderer: QuadBatchRenderer,
    val lineRenderer: LineBatchRenderer,
    val textureRenderer: TextureBatchRenderer
){
    private val renderers = listOf(
        uniColorLineRenderer,
        lineRenderer,
        textureRenderer,
        quadRenderer
    )

    fun initRenderers() =
        renderers.forEach { it.init() }

    fun initRenderTargets(width: Int, height: Int) =
        renderTarget.init(width, height)

    fun render(camera: CameraEngineInterface)
    {
        if (layerType == LayerType.WORLD) camera.enable() else camera.disable()

        renderTarget.begin()
        camera.updateViewMatrix()
        renderers.forEach { it.render(camera) }
        renderTarget.end()
    }

    fun cleanup()
    {
        renderers.forEach { it.cleanup() }
        renderTarget.cleanUp()
    }

    companion object
    {
        fun create(name: String, layerType: LayerType, initCapacity: Int, gfxState: GraphicsState) =
            GraphicsLayer(
                name,
                layerType,
                RenderTarget(gfxState),
                TextRenderer(),
                UniColorLineBatchRenderer(initCapacity, gfxState),
                QuadBatchRenderer(initCapacity, gfxState),
                LineBatchRenderer(initCapacity, gfxState),
                TextureBatchRenderer(initCapacity, gfxState)
            )
    }
}

enum class LayerType
{
    WORLD, UI, OVERLAY
}