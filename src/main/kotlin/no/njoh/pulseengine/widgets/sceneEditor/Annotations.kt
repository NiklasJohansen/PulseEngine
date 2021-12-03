package no.njoh.pulseengine.widgets.sceneEditor

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueRange(val min: Float, val max: Float)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Name(val name: String)

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Property(
    val category: String = "",
    val order: Int = 0,
    val min: Float = -3.40282346638528850e+38f,
    val max: Float = 3.40282346638528850e+38f
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EditorIcon(
    val textureAssetName: String = "",
    val width: Float = 64f,
    val height: Float = 64f
)