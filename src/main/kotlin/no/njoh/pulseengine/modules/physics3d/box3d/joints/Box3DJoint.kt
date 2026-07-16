package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.box3d.raw.Box3DRaw.b3Joint_IsValid
import no.njoh.box3d.raw.Box3DRaw.b3Joint_WakeBodies
import no.njoh.pulseengine.modules.physics3d.PhysicsJointDefinition3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld

/** 
 * Handle to a joint owned by a [Box3DWorld].
 */
abstract class Box3DJoint(
    val nativeJointId: MemorySegment,
    val bodyA: Box3DBody,
    val bodyB: Box3DBody,
) {
    var isDestroyed = false; private set

    internal var appliedFrameHash = 0

    abstract fun applyDefinition(definition: PhysicsJointDefinition3D)

    fun isValid() = !isDestroyed && b3Joint_IsValid(nativeJointId)

    fun markDestroyed() { isDestroyed = true }
    
    internal fun wakeBodies()
    {
        requireUsable()
        b3Joint_WakeBodies(nativeJointId)
    }

    protected fun requireUsable()
    {
        check(isValid()) { "Box3D joint has been destroyed" }
    }
}