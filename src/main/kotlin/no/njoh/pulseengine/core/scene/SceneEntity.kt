package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.shared.annotations.EntityRef
import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.core.shared.utils.Extensions.minus
import kotlin.reflect.KClass

/**
 * Base class for all implementations of a scene entity.
 */
abstract class SceneEntity
{
    @ScnProp(i = -3, editable = false)
    var id = INVALID_ID // ID gets assigned when entity is added to the scene

    @EntityRef
    @ScnProp(i = -2)
    var parentId = INVALID_ID

    @ScnProp(hidden = true)
    var childIds: LongArray? = null

    @ScnProp(hidden = true)
    var flags = DISCOVERABLE or EDITABLE

    @JsonIgnore
    val typeName = this::class.simpleName ?: ""

    fun set(flag: Int) { flags = flags or flag }
    fun setNot(flag: Int) { flags = flags and flag.inv() }
    fun isSet(flag: Int) = flags and flag == flag
    fun isAnySet(flag: Int) = flags and flag != 0
    fun isNot(flag: Int) = flags and flag == 0

    fun addChild(child: SceneEntity)
    {
        child.parentId = this.id
        childIds = childIds?.plus(child.id) ?: longArrayOf(child.id)
    }

    fun removeChild(child: SceneEntity)
    {
        child.parentId = INVALID_ID
        childIds = childIds?.minus(child.id)
    }

    companion object
    {
        // An ID pointing to no entity
        const val INVALID_ID = -1L

        // Flags
        const val DEAD              = 1   // Is the entity dead
        const val POSITION_UPDATED  = 2   // Was position of entity updated
        const val ROTATION_UPDATED  = 4   // Was rotation of entity updated
        const val SIZE_UPDATED      = 8   // Was size of entity updated
        const val DISCOVERABLE      = 16  // Can it be discovered by other entities
        const val SELECTED          = 32  // Is the entity selected by e.g. the Editor
        const val EDITABLE          = 64  // Is the entity editable by e.g. the Editor
        const val HIDDEN            = 128 // Is the entity hidden and not visible while rendering

        val REGISTERED_TYPES = mutableSetOf<KClass<out SceneEntity>>()
    }
}