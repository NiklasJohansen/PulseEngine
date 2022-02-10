package no.njoh.pulseengine.modules.asset.types

class SpriteSheet(
    fileName: String,
    name: String,
    private val horizontalCells: Int,
    private val verticalCells: Int
) : Texture(fileName, name), Iterable<Texture> {

    private lateinit var textures: Array<Texture>

    var size = 0
        private set

    override fun finalize(textureId: Int, isBindless: Boolean, uMin: Float, vMin: Float, uMax: Float, vMax: Float, attachment: Int)
    {
        super.finalize(textureId, isBindless, uMin, vMin, uMax, vMax, attachment)

        val uCellSize = 1f / horizontalCells
        val vCellSize = 1f / verticalCells
        val uTexSize = uMax - uMin
        val vTexSize = vMax - vMin

        this.size = horizontalCells * verticalCells
        this.textures = Array(size) { index ->

            val texture = Texture(fileName, name)
            val xIndex = index % horizontalCells
            val yIndex = index / horizontalCells
            val uMinCell = uMin + xIndex * uCellSize * uTexSize
            val vMinCell = vMin + yIndex * vCellSize * vTexSize
            val uMaxCell = uMinCell + uCellSize * uTexSize
            val vMaxCell = vMinCell + vCellSize * vTexSize

            texture.load(textureData, (width * uCellSize).toInt(), (height * vCellSize).toInt(), format)
            texture.finalize(textureId, isBindless, uMinCell, vMinCell, uMaxCell, vMaxCell)
            texture
        }
    }

    fun getTexture(xIndex: Int, yIndex: Int) : Texture
    {
        return textures[yIndex * horizontalCells + xIndex]
    }

    fun getTexture(index: Int) : Texture
    {
        return textures[index]
    }

    override fun iterator(): Iterator<Texture> = TextureIterator()

    inner class TextureIterator : Iterator<Texture> {
        var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Texture = textures[index++]
    }
}