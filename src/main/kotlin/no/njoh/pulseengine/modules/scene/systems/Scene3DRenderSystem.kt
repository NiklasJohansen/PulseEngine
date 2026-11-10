package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_1
import no.njoh.pulseengine.core.graphics.gpu.GlCapabilities
import no.njoh.pulseengine.core.graphics.gpu.texture.AttachmentPoint.COLOR_TEXTURE_0
import no.njoh.pulseengine.core.graphics.gpu.texture.multisampling.HdrMsaaResolver
import no.njoh.pulseengine.core.graphics.gpu.texture.Multisampling.MSAA4
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.NEAREST
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.R32UI
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.RGBA16F
import no.njoh.pulseengine.core.graphics.gpu.texture.mipmap.DepthPyramidGenerator
import no.njoh.pulseengine.core.graphics.scene3d.SceneRenderContext
import no.njoh.pulseengine.core.graphics.scene3d.draw.TransparencyMode.ORDER_INDEPENDENT
import no.njoh.pulseengine.core.graphics.scene3d.renderers.DepthPrepassRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.ModelRenderer
import no.njoh.pulseengine.core.graphics.scene3d.renderers.PomQuality.MEDIUM
import no.njoh.pulseengine.core.graphics.scene3d.renderers.SampleShadingMode.DISABLED
import no.njoh.pulseengine.core.graphics.surface.SurfaceOutputSpec
import no.njoh.pulseengine.core.graphics.surface.colorAttachment
import no.njoh.pulseengine.core.graphics.surface.depthTexture
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches

@Icon("MONITOR")
@Name("3D Scene Renderer")
class Scene3DRenderSystem : SceneSystem()
{
    @Prop(i=0)                 var writeRenderIds           = true
    @Prop(i=1)                 var useDepthPrepass          = true
    @Prop(i=2)                 var transparencyMode         = ORDER_INDEPENDENT
    @Prop(i=3, min=0f, max=1f) var weightedBlendAlphaCutoff = 0.04f
    @Prop(i=4)                 var multisampling            = MSAA4
    @Prop(i=5)                 var perSampleShading         = DISABLED
    @Prop(i=6)                 var pomQuality               = MEDIUM

    override fun onCreate(engine: PulseEngine)
    {
        GlCapabilities.requireFullGraphics("Scene3DRenderSystem")

        engine.gfx.createSurface(
            name = SCENE_3D_SURFACE,
            isVisible = true,
            camera = engine.gfx.mainCamera,
            zOrder = -1,
            output = SurfaceOutputSpec(
                multisampling = multisampling,
                attachments = buildList() 
                {
                    add(colorAttachment(attachmentPoint = COLOR_TEXTURE_0, format = RGBA16F, filter = LINEAR, msaaResolver = HdrMsaaResolver()))
 
                    if (writeRenderIds) 
                        add(colorAttachment(attachmentPoint = RENDER_ID_ATTACHMENT_POINT, format = R32UI, filter = NEAREST))

                    add(depthTexture(mipmapGenerator = DepthPyramidGenerator()))
                }
            ),
            clearColor = Color(63, 63, 63),
        ).apply {
            if (useDepthPrepass)
                addRenderer(DepthPrepassRenderer())
            addRenderer(ModelRenderer())
        }
    }

    override fun onUpdate(engine: PulseEngine)
    {
        val surface = engine.gfx.getSurface(SCENE_3D_SURFACE) ?: return
        surface.setMultisampling(multisampling)

        val hasRenderIdAttachment = surface.config.attachments.anyMatches { it.attachmentPoint == RENDER_ID_ATTACHMENT_POINT }
        if (writeRenderIds && !hasRenderIdAttachment)
        {
            surface.setAttachment(colorAttachment(RENDER_ID_ATTACHMENT_POINT, R32UI, NEAREST))
        }
        else if (!writeRenderIds && hasRenderIdAttachment)
        {
            surface.removeAttachment(RENDER_ID_ATTACHMENT_POINT)
        }

        surface.getRenderer<ModelRenderer>()?.let() 
        {
            it.sampleShadingMode        = perSampleShading
            it.pomQuality               = pomQuality
            it.transparencyMode         = transparencyMode
            it.weightedBlendAlphaCutoff = weightedBlendAlphaCutoff
            it.writeRenderIds           = writeRenderIds
        }

        val depthPrepassRenderer = surface.getRenderer<DepthPrepassRenderer>()
        depthPrepassRenderer?.pomQuality = pomQuality

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