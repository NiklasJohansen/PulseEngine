package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.PulseEngine
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
    open var height: Float = 0f
) {
    // For serialization
    val className = this::class.simpleName

    // For fast querying
    @JsonIgnore var next: SceneEntity? = null
    @JsonIgnore var prev: SceneEntity? = null

    var rotation: Float = 0f

    open fun onStart() {  }
    open fun onUpdate(engine: PulseEngine) { }
    open fun onFixedUpdate(engine: PulseEngine) { }
    open fun onRender(surface: Surface2D, assets: Assets, isRunning: Boolean) { }

    companion object
    {
        val REGISTERED_TYPES = mutableListOf<KClass<out SceneEntity>>()

        fun <T: SceneEntity> registerEntityType(type: KClass<T>) =
            REGISTERED_TYPES.add(type)

        fun autoRegisterEntityTypes() =
            measureNanoTime {
                ReflectionUtil
                    .getFullyQualifiedClassNames(".")
                    .getClassesFromFullyQualifiedClassNames()
                    .getClassesOfSuperType(SceneEntity::class)
                    .forEach { registerEntityType(it.kotlin) }
            }.let { Logger.debug("Registered ${REGISTERED_TYPES.size} scene entity types in " + "${"%.3f".format(it / 1_000_000f)} ms. [${REGISTERED_TYPES.joinToString { it.simpleName ?: "" }}]") }
    }
}