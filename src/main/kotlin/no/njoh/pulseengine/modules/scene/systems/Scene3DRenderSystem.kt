package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.Attachment.DEPTH_TEXTURE
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.DepthPyramidGenerator
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.scene3d.renderers.DepthPrepassRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color

@Icon("MONITOR")
@Name("3D Scene Renderer")
class Scene3DRenderSystem() : SceneSystem()
{
    @Prop(i=0)                 var useDepthPrepass = true
    @Prop(i=1)                 var transparencyMode = WEIGHTED_BLENDED_OIT
    @Prop(i=2, min=0f, max=1f) var wightedBlendAlphaCutoff = 0.04f

    override fun onCreate(engine: PulseEngine)
    {
        engine.gfx.createSurface(
            name = SCENE_3D_SURFACE,
            isVisible = true,
            camera = engine.gfx.mainCamera,
            zOrder = -1,
            multisampling = Multisampling.MSAA4,
            clearColor = Color(143, 231, 255),
            attachments = listOf(COLOR_TEXTURE_0, DEPTH_TEXTURE),
            mipmapGenerators = mapOf(DEPTH_TEXTURE to DepthPyramidGenerator()),
        ).apply {
            if (useDepthPrepass)
                addRenderer(DepthPrepassRenderer())
            addRenderer(ModelRenderer())
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(SCENE_3D_SURFACE) ?: return

        if (engine.input.wasClicked(Key.F12))
            surface.config.drawWireframe = !surface.config.drawWireframe

        val depthPrepassRenderer = surface.getRenderer<DepthPrepassRenderer>()
        val modelRenderer = surface.getRenderer<ModelRenderer>()
        
        modelRenderer?.transparencyMode = transparencyMode
        modelRenderer?.weightedBlendAlphaCutoff = wightedBlendAlphaCutoff

        if (useDepthPrepass && depthPrepassRenderer == null)
        {
            surface.addRenderer(DepthPrepassRenderer())
        }
        else if (!useDepthPrepass && depthPrepassRenderer != null)
        {
            surface.deleteRenderer(depthPrepassRenderer)
        }
    }

    override fun onRender(engine: PulseEngine) 
    {
        val context = engine.gfx.sceneContext
        engine.scene.forEachEntityOfType<Scene3DRenderable>() 
        {
            if ((it as SceneEntity).isNot(HIDDEN)) it.onRender(engine, context)
        }
    }

    override fun onStateChanged(engine: PulseEngine)
    {
        if (enabled) onCreate(engine) else onDestroy(engine)
    }

    override fun onDestroy(engine: PulseEngine)
    {
        engine.gfx.deleteSurface(SCENE_3D_SURFACE)
    }

    companion object
    {
        private const val SCENE_3D_SURFACE = "scene3d"
    }
}

interface Scene3DRenderable
{
    fun onRender(engine: PulseEngine, context: SceneRenderContext)
}