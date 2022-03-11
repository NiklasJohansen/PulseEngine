package no.njoh.pulseengine.widgets.editor

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EditorIcon(
    val textureAssetName: String = "",
    val width: Float = 64f,
    val height: Float = 64f
)