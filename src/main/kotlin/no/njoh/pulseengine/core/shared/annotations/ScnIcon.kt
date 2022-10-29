package no.njoh.pulseengine.core.shared.annotations

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(CLASS)
@Retention(RUNTIME)
annotation class ScnIcon(
    val iconName: String,
    val size: Float = 64f,
    val textureAssetName: String = "",
    val showInViewport: Boolean = false
)