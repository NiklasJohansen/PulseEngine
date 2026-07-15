package no.njoh.pulseengine.modules.physics3d.box3d

import java.lang.foreign.MemorySegment

/**
 *  Handle to one collision shape attached to a [Box3DBody]. 
 */
data class Box3DShape(
    val nativeId: MemorySegment,
    val body: Box3DBody,
)