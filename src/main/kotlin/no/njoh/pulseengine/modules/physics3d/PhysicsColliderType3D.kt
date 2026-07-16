package no.njoh.pulseengine.modules.physics3d

/** 
 * Collision representation used by a physics-backed model entity. 
 */
enum class PhysicsColliderType3D
{
    BOX,
    SPHERE,
    CAPSULE,
    CONVEX_HULL,
    TRIANGLE_MESH
}