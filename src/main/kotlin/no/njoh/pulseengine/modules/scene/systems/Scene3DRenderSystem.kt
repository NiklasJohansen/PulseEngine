package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.R32UI
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.DepthPyramidGenerator
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.WEIGHTED_BLENDED_OIT
import no.njoh.pulseengine.core.graphics.scene3d.renderers.DepthPrepassRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.graphics.surface.colorAttachment
import no.njoh.pulseengine.core.graphics.surface.depthTexture
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color

@Icon("MONITOR")
@Name("3D Scene Renderer")
class Scene3DRenderSystem : SceneSystem()
{
    @Prop(i=0)                 var useDepthPrepass = true
    @Prop(i=1)                 var transparencyMode = WEIGHTED_BLENDED_OIT
    @Prop(i=2, min=0f, max=1f) var wightedBlendAlphaCutoff = 0.04f
    @Prop(i=3)                 var writeRenderIds = true

    private var hasAppliedWriteRenderIds = false

    override fun onCreate(engine: PulseEngine)
    {
        GlCapabilities.requireFullGraphics("Scene3DRenderSystem")
        engine.gfx.createSurface(
            name = SCENE_3D_SURFACE,
            isVisible = true,
            camera = engine.gfx.mainCamera,
            zOrder = -1,
            output = SurfaceOutputSpec(
                multisampling = Multisampling.MSAA4,
                attachments = buildList() 
                {
                    add(colorAttachment())
                    if (writeRenderIds) 
                        add(colorAttachment(RENDER_ID_ATTACHMENT_POINT, R32UI, NEAREST))
                    add(depthTexture(DepthPyramidGenerator()))
                }
            ),
            clearColor = Color(63, 63, 63),
        ).apply {
            if (useDepthPrepass)
                addRenderer(DepthPrepassRenderer())
            addRenderer(ModelRenderer(writeRenderIds = writeRenderIds))
        }

        hasAppliedWriteRenderIds = writeRenderIds
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(SCENE_3D_SURFACE) ?: return

        if (engine.input.wasClicked(Key.F12))
            surface.config.drawWireframe = !surface.config.drawWireframe

        val depthPrepassRenderer = surface.getRenderer<DepthPrepassRenderer>()
        val modelRenderer = surface.getRenderer<ModelRenderer>()
        val renderIdsEnabled = writeRenderIds

        if (renderIdsEnabled != hasAppliedWriteRenderIds)
        {
            if (!renderIdsEnabled)
            {
                surface.removeAttachment(RENDER_ID_ATTACHMENT_POINT)
            }
            else
            {
                surface.setAttachment(colorAttachment(RENDER_ID_ATTACHMENT_POINT, R32UI, NEAREST))
            }
            hasAppliedWriteRenderIds = renderIdsEnabled
        }
        
        modelRenderer?.transparencyMode = transparencyMode
        modelRenderer?.weightedBlendAlphaCutoff = wightedBlendAlphaCutoff
        modelRenderer?.writeRenderIds = renderIdsEnabled

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
            if ((it as SceneEntity).isNot(HIDDEN or DEAD)) it.onRender(engine, context)
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
        const val SCENE_3D_SURFACE = "scene3d"
        val RENDER_ID_ATTACHMENT_POINT = COLOR_TEXTURE_1
    }
}

interface Scene3DRenderable
{
    fun onRender(engine: PulseEngine, context: SceneRenderContext)
}
