package no.njoh.pulseengine.modules.physics3d

import no.njoh.pulseengine.core.asset.types.Model.CollisionMesh
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc

/** 
 * Description of geometry to be cooked by the physics module. 
 */
sealed interface ShapeGeometry3D

data class BoxGeometry3D(
    val size: Vector3fc, // Full extents
    val center: Vector3fc = Vector3f(),
    val rotation: Quaternionfc = Quaternionf()
) : ShapeGeometry3D

data class SphereGeometry3D(
    val radius: Float,
    val center: Vector3fc = Vector3f()
) : ShapeGeometry3D

data class CapsuleGeometry3D(
    val point1: Vector3fc,
    val point2: Vector3fc,
    val radius: Float
) : ShapeGeometry3D

data class ConvexHullGeometry3D(
    val mesh: CollisionMesh,
    val scale: Vector3fc = Vector3f(1f)
) : ShapeGeometry3D

data class TriangleMeshGeometry3D(
    val mesh: CollisionMesh,
    val scale: Vector3fc = Vector3f(1f)
) : ShapeGeometry3D