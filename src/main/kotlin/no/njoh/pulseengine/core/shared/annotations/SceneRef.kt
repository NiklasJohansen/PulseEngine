package no.njoh.pulseengine.core.shared.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER

/**
 * Marks a [String] property as a scene file reference and enables scene-file selection in the editor.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
annotation class SceneRef