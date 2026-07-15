package no.njoh.pulseengine.modules.physics3d.box3d

import no.njoh.pulseengine.modules.physics3d.ShapeGeometry3D
import java.lang.foreign.MemorySegment

/**
 *  Handle to one collision shape attached to a [Box3DBody]. 
 */
data class Box3DShape(
    val nativeId: MemorySegment,
    val body: Box3DBody,
)

/**
 * Material, filtering, and geometry for one shape attached to a rigid body.
 */
data class Box3DShapeDefinition(
    val geometry: ShapeGeometry3D,
    val density: Float = 1f,
    val friction: Float = 0.6f,
    val restitution: Float = 0f,
    val categoryBits: Long = 1L,
    val maskBits: Long = -1L,
    val groupIndex: Int = 0,
    val sensor: Boolean = false,
    val enableContactEvents: Boolean = false,
    val enableSensorEvents: Boolean = false,
    val enableHitEvents: Boolean = false
)