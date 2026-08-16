package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy.Companion.defaultFor
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.SRGBA8
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping.REPEAT
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureAnisotropy
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFilter
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureWrapping
import no.njoh.pulseengine.core.shared.annotations.Icon
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast

@Icon("IMAGE")
class SpriteSheet(
    filePath: String,
    name: String,
    filter: TextureFilter = LINEAR_MIPMAP,
    anisotropy: TextureAnisotropy = defaultFor(filter),
    wrapping: TextureWrapping = REPEAT,
    format: TextureFormat = SRGBA8,
    maxMipLevels: Int = 5,
    private val horizontalCells: Int,
    private val verticalCells: Int,
) : Texture(filePath, name, initWidth = 1, initHeight = 1, filter, anisotropy, wrapping, format, maxMipLevels), Iterable<Texture> {

    private lateinit var textures: Array<Texture>

    var size = 0
        private set

    override fun onUploaded(handle: TextureHandle, uMin: Float, vMin: Float, uMax: Float, vMax: Float)
    {
        super.onUploaded(handle, uMin, vMin, uMax, vMax)

        val uCellSize = 1f / horizontalCells
        val vCellSize = 1f / verticalCells
        val uTexSize = uMax - uMin
        val vTexSize = vMax - vMin

        this.size = horizontalCells * verticalCells
        this.textures = Array(size) { index ->
            val xIndex = index % horizontalCells
            val yIndex = index / horizontalCells
            val uMinCell = uMin + xIndex * uCellSize * uTexSize
            val vMinCell = vMin + yIndex * vCellSize * vTexSize
            val uMaxCell = uMinCell + uCellSize * uTexSize
            val vMaxCell = vMinCell + vCellSize * vTexSize
            val cellWidth = (width * uCellSize).toInt()
            val cellHeight = (height * vCellSize).toInt()
            Texture(filePath, name, cellWidth, cellHeight, filter, anisotropy, wrapping, format, maxMipLevels).also()
            {
                it.onUploaded(handle, uMinCell, vMinCell, uMaxCell, vMaxCell)
            }
        }
    }

    override fun onDeleted()
    {
        super.onDeleted()
        if (this::textures.isInitialized)
            textures.forEachFast { it.onDeleted() }
    }

    fun getTexture(xIndex: Int, yIndex: Int): Texture
    {
        return textures[yIndex * horizontalCells + xIndex]
    }

    fun getTexture(index: Int): Texture
    {
        return textures[index]
    }

    override fun iterator(): Iterator<Texture> = TextureIterator()

    inner class TextureIterator : Iterator<Texture>
    {
        var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Texture = textures[index++]
    }
}