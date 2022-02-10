package no.njoh.pulseengine.core.graphics

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.*
import java.lang.Float.intBitsToFloat

interface RenderContext
{
    val zOrder: Int
    val isVisible: Boolean
    val textureScale: Float
    val textureFormat: TextureFormat
    val textureFilter: TextureFilter
    val antiAliasing: AntiAliasing
    val blendFunction: BlendFunction
    val attachments: List<Attachment>
    val backgroundColor: Color
    val drawColor: Float
    val depth: Float
}

class RenderContextInternal(
    override val zOrder: Int,
    override var isVisible: Boolean,
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter,
    override var antiAliasing: AntiAliasing,
    override var blendFunction: BlendFunction,
    override val attachments: List<Attachment>,
    override val backgroundColor: Color,
    override var drawColor: Float = 0f,
    override var depth: Float = 0f
) : RenderContext {

    val hasDepthAttachment = attachments.any { it.hasDepth }
    val textureAttachments = attachments
        .filter { it.isDrawable }
        .map { it.value }
        .toTypedArray()
        .toIntArray()

    init { setDrawColor(1f, 1f, 1f, 1f) }

    fun increaseDepth()
    {
        depth += DEPTH_INC
    }

    fun resetDepth(value: Float)
    {
        depth = value
    }

    fun setDrawColor(r: Float, g: Float, b: Float, a: Float)
    {
        val red   = (r * 255).toInt()
        val green = (g * 255).toInt()
        val blue  = (b * 255).toInt()
        val alpha = (a * 255).toInt()
        this.drawColor = intBitsToFloat((red shl 24) or (green shl 16) or (blue shl 8) or alpha)
    }

    companion object
    {
        private const val DEPTH_INC = 0.000001f
    }
}