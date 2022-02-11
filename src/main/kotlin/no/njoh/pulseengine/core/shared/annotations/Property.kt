package no.njoh.pulseengine.core.shared.annotations

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(CLASS, PROPERTY, VALUE_PARAMETER, PROPERTY_GETTER)
@Retention(RUNTIME)
annotation class Property(
    val category: String = "",
    val order: Int = 0,
    val min: Float = -3.40282346638528850e+38f,
    val max: Float = 3.40282346638528850e+38f
)