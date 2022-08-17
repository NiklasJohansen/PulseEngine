package no.njoh.pulseengine.core.shared.annotations

import kotlin.annotation.AnnotationRetention.*
import kotlin.annotation.AnnotationTarget.*

/**
 * This scene property annotation is used to provide meta-data to the scene editor.
 */
@Repeatable
@Target(PROPERTY, FUNCTION, PROPERTY_GETTER)
@Retention(RUNTIME)
annotation class ScnProp(
    /** The name of the group this property belongs to. */
    val group: String = "",

    /** The index of where the property should appear in the property list. */
    val i: Int = 0,

    /** Determines if the property will visible or not in scene editor. */
    val hidden: Boolean = false,

    /** Determines if the property can be edited or not in scene editor. */
    val editable: Boolean = true,

    /** The minimum allowed value of the property (relevant when the property is numerical). */
    val min: Float = -3.4028235E38f,

    /** The maximum allowed value of the property (relevant when the property is numerical). */
    val max: Float = 3.4028235E38f,

    /** A description of the property. */
    val desc: String = ""
)