package no.njoh.pulseengine.modules.physics3d.box3d

import no.njoh.pulseengine.modules.physics3d.ShapeGeometry3D
import java.lang.foreign.MemorySegment

/**
 *  Handle to one collision shape attached to a [Box3DBody]. 
 */
data class Box3DShape(
    val nativeId: MemorySegment,
    val body: Box3DBody,
    var geometryHash: Int,
    var sensor: Boolean
)

/**
 * Material, filtering, and geometry for one shape attached to a rigid body.
 */
data class Box3DShapeDefinition(
    var geometry: ShapeGeometry3D,
    var density: Float               = 1f,
    var friction: Float              = 0.6f,
    var restitution: Float           = 0f,
    var categoryBits: Long           = 1L,
    var maskBits: Long               = -1L,
    var groupIndex: Int              = 0,
    var sensor: Boolean              = false,
    var enableContactEvents: Boolean = false,
    var enableSensorEvents: Boolean  = false,
    var enableHitEvents: Boolean     = false
)