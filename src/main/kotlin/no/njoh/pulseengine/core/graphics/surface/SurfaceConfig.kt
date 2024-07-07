package no.njoh.pulseengine.core.graphics.surface

import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
import java.lang.Float.intBitsToFloat

interface SurfaceConfig
{
    val name: String
    val width: Int
    val height: Int
    val zOrder: Int
    val isVisible: Boolean
    val drawWhenEmpty: Boolean
    val textureScale: Float
    val textureFormat: TextureFormat
    val textureFilter: TextureFilter
    val multisampling: Multisampling
    val blendFunction: BlendFunction
    val attachments: List<Attachment>
    val backgroundColor: Color
}

class SurfaceConfigInternal(
    override val name: String,
    override var width: Int,
    override var height: Int,
    override var zOrder: Int,
    override var isVisible: Boolean,
    override var drawWhenEmpty: Boolean,
    override var textureScale: Float,
    override var textureFormat: TextureFormat,
    override var textureFilter: TextureFilter,
    override var multisampling: Multisampling,
    override var blendFunction: BlendFunction,
    override val attachments: List<Attachment>,
    override var backgroundColor: Color
) : SurfaceConfig {

    val hasDepthAttachment = attachments.anyMatches { it.hasDepth }
    val textureAttachments = attachments.filter { it.isDrawable }.map { it.value }.toIntArray()
    var currentDrawColor   = 0f
    var currentDepth       = 0f

    init { setDrawColor(1f, 1f, 1f, 1f) }

    fun increaseDepth()
    {
        currentDepth += DEPTH_INC
    }

    fun resetDepth(value: Float)
    {
        currentDepth = value
    }

    fun setDrawColor(r: Float, g: Float, b: Float, a: Float)
    {
        currentDrawColor = intBitsToFloat(((r * 255).toInt() shl 24) or ((g * 255).toInt() shl 16) or ((b * 255).toInt() shl 8) or (a * 255).toInt())
    }

    companion object
    {
        private const val DEPTH_INC = 0.000001f
    }
}