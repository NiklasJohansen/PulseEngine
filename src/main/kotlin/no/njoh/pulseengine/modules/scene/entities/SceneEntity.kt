package no.njoh.pulseengine.modules.scene.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
import no.njoh.pulseengine.data.assets.Font
import no.njoh.pulseengine.data.assets.Texture
import no.njoh.pulseengine.modules.Assets
import no.njoh.pulseengine.modules.graphics.Surface2D
import no.njoh.pulseengine.util.Logger
import no.njoh.pulseengine.util.ReflectionUtil
import no.njoh.pulseengine.util.ReflectionUtil.getClassesFromFullyQualifiedClassNames
import no.njoh.pulseengine.util.ReflectionUtil.getClassesOfSuperType
import kotlin.reflect.KClass
import kotlin.system.measureNanoTime

abstract class SceneEntity(
    open var x: Float = 0f,
    open var y: Float = 0f,
    open var width: Float = 0f,
    open var height: Float = 0f,
    open var rotation: Float = 0f
) {
    @JsonIgnore val typeName = this::class.simpleName ?: ""
    @JsonIgnore var flags = DISCOVERABLE

    open fun onStart(engine: PulseEngine) {  }
    open fun onUpdate(engine: PulseEngine) { }
    open fun onFixedUpdate(engine: PulseEngine) { }
    open fun onRender(surface: Surface2D, assets: Assets, sceneState: SceneState)
    {
        if (sceneState != SceneState.RUNNING)
        {
            surface.setDrawColor(1f, 1f, 1f, 0.2f)
            surface.drawTexture(Texture.BLANK, x, y, width, height, 0f, 0.5f, 0.5f)
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