package no.njoh.pulseengine.core.asset.types

class Material(
    name: String,
    val albedo: Texture? = null,
    val normal: Texture? = null,
    val aoMetalRough: Texture? = null,
    val specular: Texture? = null,
    val emissive: Texture? = null,
    val height: Texture? = null
): Asset(name, name) {
    override fun load() {}
    override fun unload() {}
}