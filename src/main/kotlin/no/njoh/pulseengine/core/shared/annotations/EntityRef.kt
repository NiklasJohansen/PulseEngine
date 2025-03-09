package no.njoh.pulseengine.core.shared.annotations

import no.njoh.pulseengine.core.scene.SceneEntity
import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KClass

/**
 * Fields of type [Long] and [LongArray] can be annotated with this annotation.
 * Lets the editor know that the fields refers to other entities and handles
 * the ID mapping when copying a cluster of related entities.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER, ANNOTATION_CLASS)
@Retention(RUNTIME)
annotation class EntityRef(
    val type: KClass<out SceneEntity> = SceneEntity::class
)