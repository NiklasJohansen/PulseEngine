package no.njoh.pulseengine.core.shared.annotations

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

@Target(CLASS, ANNOTATION_CLASS)
@Retention(RUNTIME)
annotation class Name(val name: String)