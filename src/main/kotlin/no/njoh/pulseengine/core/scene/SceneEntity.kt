package no.njoh.pulseengine.core.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Font
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.Surface2D
import no.njoh.pulseengine.core.shared.annotations.Property
import kotlin.reflect.KClass

abstract class SceneEntity
{
    @Property("", -1)
    var id = -1L // Id gets assigned when entity is added to the scene

    @Property("Transform", 0)
    open var x: Float = 0f

    @Property("Transform", 1)
    open var y: Float = 0f

    @Property("Transform", 2)
    open var z: Float = -0.1f

    @Property("Transform", 3)
    open var width: Float = 0f

    @Property("Transform", 4)
    open var height: Float = 0f

    @Property("Transform", 5)
    var rotation: Float = 0f

    @JsonIgnore
    val typeName = this::class.simpleName ?: ""

    @JsonIgnore
    var flags = DISCOVERABLE

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

    companion object
    {
        const val DEAD              = 1  // Is the entity alive
        const val POSITION_UPDATED  = 2  // Was position of entity updated
        const val ROTATION_UPDATED  = 4  // Was rotation of entity updated
        const val SIZE_UPDATED      = 8  // Was size of entity updated
        const val DISCOVERABLE      = 16 // Can it be discovered by other entities

        val REGISTERED_TYPES = mutableSetOf<KClass<out SceneEntity>>()
    }
}