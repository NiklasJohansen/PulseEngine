package no.njoh.pulseengine.core.shared.annotations

/**
 * Marks a [String] property as the optional name of an entity in the scene referenced by [sceneFileProperty].
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class EntityNameRef(val sceneFileProperty: String = "")