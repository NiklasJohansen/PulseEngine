package no.njoh.pulseengine.core.shared.annotations

import no.njoh.pulseengine.core.asset.types.Asset
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.asset.types.SpriteSheet
import no.njoh.pulseengine.core.asset.types.Texture
import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*
import kotlin.reflect.KClass

/**
 * Fields of type [String] can be annotated with this annotation.
 * Lets the editor know that the field refers to an asset and will
 * provide an asset picker.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER, ANNOTATION_CLASS)
@Retention(RUNTIME)
annotation class AssetRef(
    val type: KClass<out Asset> = Asset::class
)

/**
 * Lets the editor know that the field refers to a [Texture] asset and will provide an asset picker.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
@AssetRef(Texture::class)
annotation class TexRef

/**
 * Lets the editor know that the field refers to a [SpriteSheet] asset and will provide an asset picker.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
@AssetRef(SpriteSheet::class)
annotation class SpriteSheetRef

/**
 * Lets the editor know that the field refers to a [Sound] asset and will provide an asset picker.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
@AssetRef(Sound::class)
annotation class SoundRef

/**
 * Lets the editor know that the field refers to a [Font] asset and will provide an asset picker.
 */
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
@AssetRef(Font::class)
annotation class FontRef