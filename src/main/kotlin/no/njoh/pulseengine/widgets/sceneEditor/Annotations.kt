package no.njoh.pulseengine.widgets.sceneEditor

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueRange(val min: Float, val max: Float)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Name(val name: String)