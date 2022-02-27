package no.njoh.pulseengine.core.graphics.api

enum class Multisampling(val samples: Int)
{
    NONE(0),
    MSAA4(4),
    MSAA8(8),
    MSAA16(16),
    MSAA32(32),
    MSAA_MAX(-1) // Uses max supported number of samples
}