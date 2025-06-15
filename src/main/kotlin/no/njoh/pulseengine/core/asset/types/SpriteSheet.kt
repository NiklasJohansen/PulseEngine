package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.graphics.api.*
import no.njoh.pulseengine.core.graphics.api.TextureFilter.LINEAR_MIPMAP
import no.njoh.pulseengine.core.graphics.api.TextureFormat.SRGBA8
import no.njoh.pulseengine.core.graphics.api.TextureWrapping.REPEAT
import no.njoh.pulseengine.core.shared.annotations.Icon

@Icon("IMAGE")
class SpriteSheet(
    filePath: String,
    name: String,
    filter: TextureFilter = LINEAR_MIPMAP,
    wrapping: TextureWrapping = REPEAT,
    format: TextureFormat = SRGBA8,
    maxMipLevels: Int = 5,
    private val horizontalCells: Int,
    private val verticalCells: Int,
) : Texture(filePath, name, initWidth = 1, initHeight = 1, filter, wrapping, format, maxMipLevels), Iterable<Texture> {

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
            Texture(filePath, name, cellWidth, cellHeight, filter, wrapping, format, maxMipLevels).also()
            {
                it.onUploaded(handle, uMinCell, vMinCell, uMaxCell, vMaxCell)
            }
        }
    }

    fun getTexture(xIndex: Int, yIndex: Int): Texture
    {
        return textures[yIndex * horizontalCells + xIndex]
    }

    fun getTexture(index: Int): Texture
    {
        return textures[index]
    }

    override fun iterator(): Iterator<Texture> = ImageIterator()

    inner class ImageIterator : Iterator<Texture>
    {
        var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Texture = textures[index++]
    }
}