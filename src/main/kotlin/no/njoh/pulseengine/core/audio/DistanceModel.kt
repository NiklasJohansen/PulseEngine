package no.njoh.pulseengine.core.audio

import org.lwjgl.openal.AL11.*

/**
 * Wraps common OpenAL distance models used to calculate sound attenuation
 */
enum class DistanceModel(val value: Int)
{
    NONE(AL_NONE),
    INVERSE(AL_INVERSE_DISTANCE),
    INVERSE_CLAMPED(AL_INVERSE_DISTANCE_CLAMPED),
    LINEAR(AL_LINEAR_DISTANCE),
    LINEAR_CLAMPED(AL_LINEAR_DISTANCE_CLAMPED),
    EXPONENT(AL_EXPONENT_DISTANCE),
    EXPONENT_CLAMPED(AL_EXPONENT_DISTANCE_CLAMPED);
}