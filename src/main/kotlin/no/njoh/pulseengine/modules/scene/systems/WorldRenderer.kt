package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.api.Attachment.*
import no.njoh.pulseengine.core.graphics.api.DrawList
import no.njoh.pulseengine.core.graphics.api.Multisampling
import no.njoh.pulseengine.core.graphics.api.TextureFilter
import no.njoh.pulseengine.core.graphics.api.mipmap.DepthPyramidGenerator
import no.njoh.pulseengine.core.graphics.renderers.DepthPrepassRenderer
import no.njoh.pulseengine.core.graphics.renderers.ModelRenderer
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.primitives.Color

@Name("World Renderer (3D)")
class WorldRenderSystem() : SceneSystem()
{
    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = "world",
            isVisible = true,
            camera = engine.gfx.mainCamera,
            multisampling = Multisampling.MSAA4,
            backgroundColor = Color(143, 231, 255),
            attachments = listOf(COLOR_TEXTURE_0, DEPTH_TEXTURE),
            mipmapGenerators = mapOf(DEPTH_TEXTURE to DepthPyramidGenerator()),
            textureFilter = TextureFilter.LINEAR
        ).apply {
            addRenderer(DepthPrepassRenderer())
            addRenderer(ModelRenderer())
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface("world") ?: return

        if (engine.input.wasClicked(Key.F12))
            surface.config.drawWireframe = !surface.config.drawWireframe
    }

    override fun onRender(engine: PulseEngine) 
    {
        val surface = engine.gfx.getSurface("world") ?: return
        val drawList = DrawList()
        
        engine.scene.forEachEntityOfType<WorldRenderable>() 
        {
            if ((it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, drawList)
        }

        surface.getRenderer<DepthPrepassRenderer>()?.draw(drawList)
        surface.getRenderer<ModelRenderer>()?.draw(drawList)
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.deleteSurface("world")
    }
}

interface WorldRenderable
{
    fun onRender(engine: PulseEngine, drawList: DrawList)
}