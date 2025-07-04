package no.njoh.pulseengine.widgets.editor

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.utils.Extensions.noneMatches
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ReflectionUtil.findPropertyAnnotation
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*
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
     * Returns true if the [KMutableProperty] is a primitive array.
     */
    fun KMutableProperty<*>.isPrimitiveArray() =
        this.javaField?.type?.kotlin?.let {
            it.isSubclassOf(LongArray::class) ||
            it.isSubclassOf(IntArray::class) ||
            it.isSubclassOf(ShortArray::class) ||
            it.isSubclassOf(ByteArray::class) ||
            it.isSubclassOf(FloatArray::class) ||
            it.isSubclassOf(DoubleArray::class) ||
            it.isSubclassOf(CharArray::class) ||
            it.isSubclassOf(BooleanArray::class)
        } ?: false

    /**
     * Parses the given string value into the class type given by the [KMutableProperty].
     * Sets the named property of the obj to the parsed value.
     */
    fun Any.setPrimitiveProperty(property: KMutableProperty<*>, value: String) =
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
            Logger.error { "Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}" }
        }

    /**
     * Sets the named property of the object to the given value.
     */
    fun Any.setPrimitiveProperty(name: String, value: Any?)
    {
        if (value == null)
            return

        val prop = this::class.memberProperties.find { it.name == name } as? KMutableProperty<*> ?: return

        try { prop.setter.call(this, value) }
        catch (e: Exception) { Logger.error { "Failed to set property with name: $name, reason: ${e.message}" } }
    }

    /**
     * Sets the named array property of the object to a copy of the given array.
     */
    fun Any.setArrayProperty(name: String, value: Any?)
    {
        if (value == null)
            return

        val property = this::class.memberProperties.find { it.name == name } as? KMutableProperty<*> ?: return

        try
        {
            when (value)
            {
                is LongArray    -> value.copyOf()
                is IntArray     -> value.copyOf()
                is ShortArray   -> value.copyOf()
                is ByteArray    -> value.copyOf()
                is FloatArray   -> value.copyOf()
                is DoubleArray  -> value.copyOf()
                is CharArray    -> value.copyOf()
                is BooleanArray -> value.copyOf()
                else            -> null
            }?.let { property.setter.call(this, it) }
        }
        catch (e: Exception)
        {
            Logger.error { "Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}" }
        }
    }

    /**
     * Parses the given string value into the class type given by the [KMutableProperty].
     * Sets the named property of the obj to the parsed value.
     */
    fun Any.setArrayProperty(property: KMutableProperty<*>, value: String) =
        try
        {
            val values = value.split(",").map { it.trim() }
            when (property.javaField?.type)
            {
                LongArray::class.java    -> LongArray(values.size)   { values[it].trim().toLong() }
                IntArray::class.java     -> IntArray(values.size)    { values[it].trim().toInt() }
                ShortArray::class.java   -> ShortArray(values.size)  { values[it].trim().toShort() }
                ByteArray::class.java    -> ByteArray(values.size)   { values[it].trim().toByte() }
                FloatArray::class.java   -> FloatArray(values.size)  { values[it].trim().toFloat() }
                DoubleArray::class.java  -> DoubleArray(values.size) { values[it].trim().toDouble() }
                else                     -> null
            }?.let { property.setter.call(this, it) }
        }
        catch (e: Exception)
        {
            Logger.error { "Failed to parse value: $value into required type: ${property.javaField?.type}, reason: ${e.message}" }
        }

    /**
     * Returns the [Prop] annotations from the property if available, else null.
     */
    fun Any.getPropInfo(prop: KProperty<*>): Prop? = this::class.findPropertyAnnotation<Prop>(prop.name)

    /**
     * Returns the name of the class.
     */
    fun KClass<*>.getName() = this.findAnnotation<Name>()?.name ?: this.simpleName ?: "NO_NAME"

    /**
     * Creates a copy of each entity and inserts them into the active [Scene].
     * Will preserve parent-child relationships and update fields annotated with [EntityRef].
     */
    fun duplicateAndInsertEntities(engine: PulseEngine, entities: List<SceneEntity>): List<SceneEntity>
    {
        val copies = entities.map { it.createCopy() }.toMutableList()
        val insertedCopies = mutableListOf<SceneEntity>()
        val idMapping = mutableMapOf<Long, Long>()

        // Insert copied entities in order of parents before children
        while (copies.isNotEmpty())
        {
            val copiesLeft = copies.size
            for (copy in copies)
            {
                if (copies.noneMatches { it.id == copy.parentId })
                {
                    copy.childIds = null
                    copy.parentId = idMapping[copy.parentId] ?: copy.parentId
                    copy.setNot(SceneEntity.SELECTED)
                    val lastId = copy.id
                    val newId = engine.scene.addEntity(copy)
                    idMapping[lastId] = newId
                    copies.remove(copy)
                    insertedCopies.add(copy)
                    break
                }
            }

            if (copiesLeft == copies.size)
            {
                Logger.error { "Failed to copy entities with circular dependencies! IDs: ${copies.map { it.id }}" }
                return emptyList()
            }
        }

        // Handle fields annotated with EntityRef
        for (entity in insertedCopies)
        {
            for (prop in entity::class.memberProperties)
            {
                if (prop is KMutableProperty<*> && prop.name != "parent" && prop.name != "childIds" && prop.hasAnnotation<EntityRef>())
                {
                    val ref = prop.getter.call(entity)
                    if (ref is Long)
                    {
                        idMapping[ref]?.let { newRef -> prop.setter.call(entity, newRef) }
                    }
                    else if (ref is LongArray && ref.isNotEmpty())
                    {
                        prop.setter.call(entity, LongArray(ref.size) { idMapping[ref[it]] ?: ref[it] })
                    }
                }
            }
        }

        return insertedCopies
    }

    /**
     * Creates a copy of the [SceneEntity].
     */
    fun SceneEntity.createCopy(): SceneEntity
    {
        val entityCopy = this::class.constructors.first().call()
        for (prop in this::class.members)
        {
            if (prop is KMutableProperty<*> && prop.visibility == KVisibility.PUBLIC)
            {
                prop.getter.call(this)?.also { propValue: Any ->

                    if (propValue::class.isData)
                    {
                        // Use .copy() if parameter is a data class
                        val copyFunc = propValue::class.memberFunctions.first { it.name == "copy" }
                        val instanceParam = copyFunc.instanceParameter!!
                        copyFunc.callBy(mapOf(instanceParam to propValue))?.let {
                            entityCopy.setPrimitiveProperty(prop.name, it)
                        }
                    }
                    else if (prop.isPrimitiveValue())
                    {
                        entityCopy.setPrimitiveProperty(prop.name, propValue)
                    }
                    else if (prop.isPrimitiveArray())
                    {
                        entityCopy.setArrayProperty(prop.name, propValue)
                    }
                }
            }
        }
        return entityCopy
    }
}