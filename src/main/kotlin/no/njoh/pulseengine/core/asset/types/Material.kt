package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.shared.primitives.Color

class Material(
    name: String,
    var baseColor: Color                     = Color(1f, 1f, 1f),
    var albedoMap: Texture?                  = null,
    var normalMap: Texture?                  = null,
    var aoRoughMetalMap: Texture?            = null,
    var emissiveMap: Texture?                = null,
    var heightMap: Texture?                  = null,
    var cullMode: CullMode                   = CullMode.BACK,
    var blendMode: BlendMode                 = BlendMode.OPAQUE,
    var alphaCutoff: Float                   = 0.5f,
    var metallicFactor: Float                = 0f,
    var roughnessFactor: Float               = 1f,
    var emissiveFactor: Color                = Color(0f, 0f, 0f),
    var occlusionStrength: Float             = 1f,
    var heightScale: Float                   = DEFAULT_HEIGHT_SCALE,
    var normalScale: Float                   = 1f,
    var normalOrientation: NormalOrientation = NormalOrientation.NORMAL,
    var xTiling: Float                       = 1f,
    var yTiling: Float                       = 1f,
    var layerMask: Int                       = 1
): Asset(name, name) {

    override var displayName = name

    var id      = DEFAULT_ID; private set
    var isDirty = true

    fun markDirty()
    {
        isDirty = true
    }

    fun onUploaded(id: Int)
    {
        this.id = id
        this.isDirty = false
    }

    fun onDeleted()
    {
        id = DEFAULT_ID
        isDirty = true
    }

    override fun load() {}
    override fun unload() {}

    fun setFrom(source: Material): Material
    {
        if (isEqual(source)) return this

        baseColor.setFrom(source.baseColor)
        emissiveFactor.setFrom(source.emissiveFactor)

        albedoMap          = source.albedoMap
        normalMap          = source.normalMap
        aoRoughMetalMap    = source.aoRoughMetalMap
        emissiveMap        = source.emissiveMap
        heightMap          = source.heightMap
        cullMode           = source.cullMode
        blendMode          = source.blendMode
        alphaCutoff        = source.alphaCutoff
        metallicFactor     = source.metallicFactor
        roughnessFactor    = source.roughnessFactor
        occlusionStrength  = source.occlusionStrength
        heightScale        = source.heightScale
        normalScale        = source.normalScale
        normalOrientation  = source.normalOrientation
        xTiling            = source.xTiling
        yTiling            = source.yTiling
        layerMask          = source.layerMask

        markDirty()
        return this
    }

    fun isEqual(source: Material): Boolean =
        baseColor         == source.baseColor &&
        albedoMap         == source.albedoMap && 
        normalMap         == source.normalMap && 
        aoRoughMetalMap   == source.aoRoughMetalMap && 
        emissiveMap       == source.emissiveMap && 
        heightMap         == source.heightMap && 
        cullMode          == source.cullMode && 
        blendMode         == source.blendMode && 
        alphaCutoff       == source.alphaCutoff && 
        metallicFactor    == source.metallicFactor && 
        roughnessFactor   == source.roughnessFactor && 
        emissiveFactor    == source.emissiveFactor && 
        occlusionStrength == source.occlusionStrength && 
        heightScale       == source.heightScale && 
        normalScale       == source.normalScale && 
        normalOrientation == source.normalOrientation && 
        xTiling           == source.xTiling && 
        yTiling           == source.yTiling && 
        layerMask         == source.layerMask

    enum class BlendMode 
    {
        OPAQUE, // Alpha ignored (solids)
        MASK,   // Alpha-tested (fences, leaves, etc.)
        BLEND   // Alpha-blended (glass, smoke, etc.)
    }

    enum class CullMode { NONE, BACK }

    enum class NormalOrientation { NORMAL, INVERT_U, INVERT_V, INVERT_UV }

    companion object
    {
        const val DEFAULT_ID = 0
        const val DEFAULT_HEIGHT_SCALE = 0.1f
    }
}