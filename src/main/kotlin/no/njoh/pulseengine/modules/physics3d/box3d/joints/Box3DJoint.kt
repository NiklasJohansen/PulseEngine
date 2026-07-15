package no.njoh.pulseengine.modules.physics3d.box3d.joints

import java.lang.foreign.MemorySegment
import no.njoh.box3d.raw.Box3DRaw.b3Joint_IsValid
import no.njoh.box3d.raw.Box3DRaw.b3Joint_WakeBodies
import no.njoh.pulseengine.modules.physics3d.PhysicsJoint3D
import no.njoh.pulseengine.modules.physics3d.box3d.Box3DBody

/** 
 * Handle to a joint owned by a [no.njoh.pulseengine.modules.physics3d.box3d.Box3DWorld].
 */
abstract class Box3DJoint(
    final override val bodyAEntityId: Long,
    final override val bodyBEntityId: Long,
    val bodyA: Box3DBody,
    val bodyB: Box3DBody,
    val nativeJointId: MemorySegment
) : PhysicsJoint3D {

    var isDestroyed = false; private set

    override fun isValid() = !isDestroyed && b3Joint_IsValid(nativeJointId)

    protected fun wakeBodies()
    {
        requireUsable()
        b3Joint_WakeBodies(nativeJointId)
    }

    fun markDestroyed()
    {
        isDestroyed = true
    }

    protected fun requireUsable()
    {
        check(isValid()) { "Box3D joint has been destroyed" }
    }
}