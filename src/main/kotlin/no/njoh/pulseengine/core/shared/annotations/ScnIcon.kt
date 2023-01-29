package no.njoh.pulseengine.core.shared.annotations

import no.njoh.pulseengine.core.shared.primitives.Color
import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(CLASS)
@Retention(RUNTIME)
annotation class ScnIcon(
    val iconName: String,
    val size: Float = 64f,
    val hexColor: String = "",
    val textureAssetName: String = "",
    val showInViewport: Boolean = false
) {
    companion object
    {
        fun ScnIcon.getColor() =
            if (hexColor.isBlank()) null
            else cache.getOrPut(hexColor) { Color().also { it.setFrom(hexColor) } }

        private val cache = mutableMapOf<String, Color>()
    }
}