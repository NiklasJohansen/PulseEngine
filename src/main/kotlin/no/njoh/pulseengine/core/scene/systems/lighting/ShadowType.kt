package no.njoh.pulseengine.core.scene.systems.lighting

enum class ShadowType(val flag: Int)
{
    NONE(64),
    HARD(128),
    SOFT(256)
}