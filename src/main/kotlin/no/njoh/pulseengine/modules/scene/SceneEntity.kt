package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.data.SceneState
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

    open fun onStart() {  }
    open fun onUpdate(engine: PulseEngine) { }
    open fun onFixedUpdate(engine: PulseEngine) { }
    open fun onRender(surface: Surface2D, assets: Assets, sceneState: SceneState) { }

    fun set(flag: Int) { flags = flags or flag }
    fun clear(flag: Int) { flags = flags and flag.inv() }
    fun isSet(flag: Int) = flags and flag == flag
    fun isAnySet(flag: Int) = flags and flag != 0
    fun isNot(flag: Int) = flags and flag == 0

    companion object
    {
        const val DEAD              = 0x00000000000000000000000000000001 // Is the entity alive
        const val POSITION_UPDATED  = 0x00000000000000000000000000000010 // Was position of entity updated
        const val ROTATION_UPDATED  = 0x00000000000000000000000000000100 // Was rotation of entity updated
        const val SIZE_UPDATED      = 0x00000000000000000000000000001000 // Was size of entity updated
        const val DISCOVERABLE      = 0x00000000000000000000000000010000 // Can it be discovered by other entities

        val REGISTERED_TYPES = mutableListOf<KClass<out SceneEntity>>()

        fun autoRegisterEntityTypes() =
            measureNanoTime {
                ReflectionUtil
                    .getFullyQualifiedClassNames(".")
                    .getClassesFromFullyQualifiedClassNames()
                    .getClassesOfSuperType(SceneEntity::class)
                    .forEach { REGISTERED_TYPES.add(it.kotlin) }
            }.let { Logger.debug("Registered ${REGISTERED_TYPES.size} scene entity types in " + "${"%.3f".format(it / 1_000_000f)} ms. [${REGISTERED_TYPES.joinToString { it.simpleName ?: "" }}]") }
    }
}