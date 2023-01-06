package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.shared.annotations.ScnProp
import no.njoh.pulseengine.core.shared.utils.Extensions.minus
import kotlin.reflect.KClass

abstract class SceneEntity
{
    @ScnProp(i = -3, editable = false)
    var id = INVALID_ID // ID gets assigned when entity is added to the scene

    @ScnProp(i = -2)
    var parentId = INVALID_ID

    @ScnProp(hidden = true)
    var childIds: LongArray? = null

    @ScnProp(i = -1)
    open var name: String? = null

    @ScnProp("Transform", 0)
    open var x: Float = 0f

    @ScnProp("Transform", 1)
    open var y: Float = 0f

    @ScnProp("Transform", 2)
    open var z: Float = -0.1f

    @ScnProp("Transform", 3)
    open var width: Float = 0f

    @ScnProp("Transform", 4)
    open var height: Float = 0f

    @ScnProp("Transform", 5)
    open var rotation: Float = 0f

    @ScnProp(hidden = true)
    var flags = DISCOVERABLE or EDITABLE

    @JsonIgnore
    val typeName = this::class.simpleName ?: ""

    open fun onCreate() { }
    open fun onStart(engine: PulseEngine) {  }
    open fun onUpdate(engine: PulseEngine) { }
    open fun onFixedUpdate(engine: PulseEngine) { }
    open fun onRender(engine: PulseEngine, surface: Surface2D)
    {
        if (engine.scene.state == SceneState.STOPPED)
        {
            surface.setDrawColor(1f, 1f, 1f, 0.5f)
            surface.drawTexture(Texture.BLANK, x, y, width, height, rotation, 0.5f, 0.5f)
            surface.setDrawColor(1f, 1f, 1f, 1f)
            var text = typeName
            val width = Font.DEFAULT.getWidth(typeName)
            if (width > this.width)
                text = text.substring(0, ((text.length / (width / this.width)).toInt().coerceIn(0, text.length)))
            surface.drawText(text, x, y, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    fun set(flag: Int) { flags = flags or flag }
    fun setNot(flag: Int) { flags = flags and flag.inv() }
    fun isSet(flag: Int) = flags and flag == flag
    fun isAnySet(flag: Int) = flags and flag != 0
    fun isNot(flag: Int) = flags and flag == 0

    fun addChild(childEntity: SceneEntity)
    {
        childEntity.parentId = this.id
        childIds = childIds?.plus(childEntity.id) ?: longArrayOf(childEntity.id)
    }

    fun removeChild(childEntity: SceneEntity)
    {
        childEntity.parentId = INVALID_ID
        childIds = childIds?.minus(childEntity.id)
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