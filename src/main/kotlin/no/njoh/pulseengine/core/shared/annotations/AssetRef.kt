package no.njoh.pulseengine.core.shared.annotations

import no.njoh.pulseengine.core.asset.types.Asset
import kotlin.reflect.KClass

/**
 * Fields of type [String] can be annotated with this annotation.
 * Lets the editor know that the field refers to an asset and will
 * provide an asset picker to help find assets.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AssetRef(
    val type: KClass<out Asset> = Asset::class
)