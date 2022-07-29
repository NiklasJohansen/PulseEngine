package no.njoh.pulseengine.widgets.editor

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.shared.utils.Logger
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Contains utility functions used by the [SceneEditor].
 */
object EditorUtil
{
    /**
     * Returns true if the [KMutableProperty] should be editable in the editor.
     */
    fun KMutableProperty<*>.isEditable() =
        visibility != KVisibility.PRIVATE &&
        visibility != KVisibility.PROTECTED &&
        javaField?.getAnnotation(JsonIgnore::class.java) == null

    /**
     * Returns true if the [KMutableProperty] is a primitive type.
     */
    fun KMutableProperty<*>.isPrimitiveValue() =
        this.javaField?.type?.kotlin?.let {
            it.isSubclassOf(Number::class) ||
            it.isSubclassOf(Boolean::class) ||
            it.isSubclassOf(Char::class) ||
            it.isSubclassOf(CharSequence::class) ||
            it.isSubclassOf(Enum::class)
        } ?: false

    /**
     * Parses the given string value into the class type given by the [KMutableProperty].
     * Sets the named property of the obj to the parsed value.
     */
    fun Any.setProperty(property: KMutableProperty<*>, value: String) =
        try
        {
            when (property.javaField?.type)
            {
                String::class.java  -> value
                Int::class.java     -> value.toIntOrNull()
                Float::class.java   -> value.toFloatOrNull()
                Double::class.java  -> value.toDoubleOrNull()
                Long::class.java    -> value.toLongOrNull()
                Boolean::class.java -> value.toBoolean()
                else                -> null
            }?.let { property.setter.call(this, it) }
        }
        catch (e: Exception)
        {
            Logger.error("Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}")
        }

    /**
     * Sets the named property of the object to the given value.
     */
    fun Any.setProperty(name: String, value: Any?)
    {
        if (value == null)
            return

        val prop = this::class.memberProperties.find { it.name == name } as? KMutableProperty<*> ?: return

        try { prop.setter.call(this, value) }
        catch (e: Exception) { Logger.error("Failed to set property with name: $name, reason: ${e.message}") }
    }
}