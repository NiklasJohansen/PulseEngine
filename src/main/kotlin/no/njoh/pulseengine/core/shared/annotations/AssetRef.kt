package no.njoh.pulseengine.core.shared.annotations

import no.njoh.pulseengine.core.asset.types.Asset
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.asset.types.Texture
import kotlin.reflect.KClass

/**
 * Fields of type [String] can be annotated with this annotation.
 * Lets the editor know that the field refers to an asset and will
 * provide an asset picker to help find assets.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AssetRef(
    val type: KClass<out Asset> = Asset::class
)

/**
 * Lets the editor know that the field refers to a [Texture] asset and will
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@AssetRef(Texture::class)
annotation class TexRef

/**
 * Lets the editor know that the field refers to a [Sound] asset and will
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@AssetRef(Sound::class)
annotation class SoundRef

/**
 * Lets the editor know that the field refers to a [Font] asset and will
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@AssetRef(Font::class)
annotation class FontRef