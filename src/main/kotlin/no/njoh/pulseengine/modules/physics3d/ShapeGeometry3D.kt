package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.asset.types.Model.CollisionMesh
import org.joml.Quaternionf
import org.joml.Vector3f

/** 
 * Description of geometry to be cooked by the physics module. 
 */
sealed interface ShapeGeometry3D

data class BoxGeometry3D(
    val size: Vector3f = Vector3f(1f), // Full extents
    val center: Vector3f = Vector3f(),
    val rotation: Quaternionf = Quaternionf()
) : ShapeGeometry3D

data class SphereGeometry3D(
    var radius: Float = 0.5f,
    val center: Vector3f = Vector3f()
) : ShapeGeometry3D

data class CapsuleGeometry3D(
    val point1: Vector3f = Vector3f(),
    val point2: Vector3f = Vector3f(),
    var radius: Float = 0.5f
) : ShapeGeometry3D

data class ConvexHullGeometry3D(
    var mesh: CollisionMesh,
    val scale: Vector3f = Vector3f(1f)
) : ShapeGeometry3D

data class TriangleMeshGeometry3D(
    var mesh: CollisionMesh,
    val scale: Vector3f = Vector3f(1f)
) : ShapeGeometry3D