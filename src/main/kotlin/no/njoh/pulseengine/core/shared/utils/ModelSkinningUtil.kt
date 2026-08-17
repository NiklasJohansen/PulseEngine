package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import org.joml.Matrix4f
import kotlin.math.abs

/** 
 * Returns the transformed AABB of `source`. 
 */
internal fun transformAabb(source: Model.Aabb, transform: Matrix4f): Model.Aabb =
    Model.Aabb().also { transformAabb(source, transform, it) }

/** 
 * Writes the transformed AABB of `source` into `out`. 
 */
internal fun transformAabb(source: Model.Aabb, transform: Matrix4f, outAabb: Model.Aabb)
{
    val cx = (source.xMin + source.xMax) * 0.5f
    val cy = (source.yMin + source.yMax) * 0.5f
    val cz = (source.zMin + source.zMax) * 0.5f
    val hx = (source.xMax - source.xMin) * 0.5f
    val hy = (source.yMax - source.yMin) * 0.5f
    val hz = (source.zMax - source.zMin) * 0.5f

    val tx = transform.m00() * cx + transform.m10() * cy + transform.m20() * cz + transform.m30()
    val ty = transform.m01() * cx + transform.m11() * cy + transform.m21() * cz + transform.m31()
    val tz = transform.m02() * cx + transform.m12() * cy + transform.m22() * cz + transform.m32()

    val thx = abs(transform.m00()) * hx + abs(transform.m10()) * hy + abs(transform.m20()) * hz
    val thy = abs(transform.m01()) * hx + abs(transform.m11()) * hy + abs(transform.m21()) * hz
    val thz = abs(transform.m02()) * hx + abs(transform.m12()) * hy + abs(transform.m22()) * hz

    outAabb.set(tx - thx, ty - thy, tz - thz, tx + thx, ty + thy, tz + thz)
}

/** 
 * Combines transformed per-bone bounds into a conservative skinned mesh AABB. 
 */
internal fun getSkinnedMeshBounds(
    mesh: Mesh,
    boneMatrices: Array<Matrix4f>,
    hasBones: Boolean,
    outAabb: Model.Aabb
) {
    if (!hasBones || boneMatrices.isEmpty())
    {
        outAabb.set(mesh.localBounds)
        return
    }

    val skinningBounds = mesh.skinningBounds
    if (skinningBounds == null)
    {
        outAabb.set(mesh.localBounds)
        return
    }

    var hasAnyBounds = false
    var xMin = 0f
    var yMin = 0f
    var zMin = 0f
    var xMax = 0f
    var yMax = 0f
    var zMax = 0f

    skinningBounds.staticBounds?.let { staticBounds ->
        xMin = staticBounds.xMin
        yMin = staticBounds.yMin
        zMin = staticBounds.zMin
        xMax = staticBounds.xMax
        yMax = staticBounds.yMax
        zMax = staticBounds.zMax
        hasAnyBounds = true
    }

    val boneIndices = skinningBounds.boneIndices
    val restPoseBounds = skinningBounds.restPoseBounds
    for (i in boneIndices.indices)
    {
        val boneIndex = boneIndices[i]
        if (boneIndex !in boneMatrices.indices)
            continue

        val matrix = boneMatrices[boneIndex]
        val offset = i * 6
        val txMin = transformedMinX(restPoseBounds, offset, matrix)
        val tyMin = transformedMinY(restPoseBounds, offset, matrix)
        val tzMin = transformedMinZ(restPoseBounds, offset, matrix)
        val txMax = transformedMaxX(restPoseBounds, offset, matrix)
        val tyMax = transformedMaxY(restPoseBounds, offset, matrix)
        val tzMax = transformedMaxZ(restPoseBounds, offset, matrix)

        if (!hasAnyBounds)
        {
            xMin = txMin
            yMin = tyMin
            zMin = tzMin
            xMax = txMax
            yMax = tyMax
            zMax = tzMax
            hasAnyBounds = true
            continue
        }

        if (txMin < xMin) xMin = txMin
        if (tyMin < yMin) yMin = tyMin
        if (tzMin < zMin) zMin = tzMin
        if (txMax > xMax) xMax = txMax
        if (tyMax > yMax) yMax = tyMax
        if (tzMax > zMax) zMax = tzMax
    }

    if (hasAnyBounds)
        outAabb.set(xMin, yMin, zMin, xMax, yMax, zMax)
    else
        outAabb.set(mesh.localBounds)
}

/** 
 * Builds per-bone rest-pose bounds for a mesh from its weighted vertices. 
 */
fun buildSkinningBounds(mesh: Mesh, vertices: FloatArray, bones: List<Model.Bone>, hasTexCoords: Boolean): Model.SkinningBounds?
{
    if (bones.isEmpty() || vertices.isEmpty())
        return null

    val boneIndexOffset = Model.BASE_VERTEX_FLOATS + if (hasTexCoords) 2 else 0
    val boneWeightOffset = boneIndexOffset + Model.MAX_BONE_INFLUENCES
    val stride = mesh.vertexStride

    val localSlotByBone = IntArray(bones.size) { -1 }
    val boneIndices = IntArray(bones.size)
    val restPoseBounds = FloatArray(bones.size * 6)
    var boneCount = 0

    var hasStaticBounds = false
    var staticXMin = 0f
    var staticYMin = 0f
    var staticZMin = 0f
    var staticXMax = 0f
    var staticYMax = 0f
    var staticZMax = 0f

    for (vertexIndex in mesh.vertexStart until (mesh.vertexStart + mesh.vertexCount))
    {
        val baseOffset = vertexIndex * stride
        val x = vertices[baseOffset]
        val y = vertices[baseOffset + 1]
        val z = vertices[baseOffset + 2]

        var hasValidInfluence = false
        for (slot in 0 until Model.MAX_BONE_INFLUENCES)
        {
            val weight = vertices[baseOffset + boneWeightOffset + slot]
            if (weight <= 0f)
                continue

            val boneIndex = vertices[baseOffset + boneIndexOffset + slot].toInt()
            if (boneIndex !in bones.indices)
                continue

            hasValidInfluence = true
            val localSlot = localSlotByBone[boneIndex]
            if (localSlot == -1)
            {
                localSlotByBone[boneIndex] = boneCount
                boneIndices[boneCount] = boneIndex

                val boundsOffset = boneCount * 6
                restPoseBounds[boundsOffset] = x
                restPoseBounds[boundsOffset + 1] = y
                restPoseBounds[boundsOffset + 2] = z
                restPoseBounds[boundsOffset + 3] = x
                restPoseBounds[boundsOffset + 4] = y
                restPoseBounds[boundsOffset + 5] = z
                boneCount++
            }
            else
            {
                val boundsOffset = localSlot * 6
                if (x < restPoseBounds[boundsOffset]) restPoseBounds[boundsOffset] = x
                if (y < restPoseBounds[boundsOffset + 1]) restPoseBounds[boundsOffset + 1] = y
                if (z < restPoseBounds[boundsOffset + 2]) restPoseBounds[boundsOffset + 2] = z
                if (x > restPoseBounds[boundsOffset + 3]) restPoseBounds[boundsOffset + 3] = x
                if (y > restPoseBounds[boundsOffset + 4]) restPoseBounds[boundsOffset + 4] = y
                if (z > restPoseBounds[boundsOffset + 5]) restPoseBounds[boundsOffset + 5] = z
            }
        }

        if (!hasValidInfluence)
        {
            if (!hasStaticBounds)
            {
                staticXMin = x
                staticYMin = y
                staticZMin = z
                staticXMax = x
                staticYMax = y
                staticZMax = z
                hasStaticBounds = true
            }
            else
            {
                if (x < staticXMin) staticXMin = x
                if (y < staticYMin) staticYMin = y
                if (z < staticZMin) staticZMin = z
                if (x > staticXMax) staticXMax = x
                if (y > staticYMax) staticYMax = y
                if (z > staticZMax) staticZMax = z
            }
        }
    }

    if (boneCount == 0 && !hasStaticBounds)
        return null

    return Model.SkinningBounds(
        boneIndices = boneIndices.copyOf(boneCount),
        restPoseBounds = restPoseBounds.copyOf(boneCount * 6),
        staticBounds = if (hasStaticBounds) Model.Aabb(staticXMin, staticYMin, staticZMin, staticXMax, staticYMax, staticZMax) else null
    )
}

private fun transformedMinX(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val tx = matrix.m00() * cx + matrix.m10() * cy + matrix.m20() * cz + matrix.m30()
    val thx = abs(matrix.m00()) * hx + abs(matrix.m10()) * hy + abs(matrix.m20()) * hz
    return tx - thx
}

private fun transformedMinY(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val ty = matrix.m01() * cx + matrix.m11() * cy + matrix.m21() * cz + matrix.m31()
    val thy = abs(matrix.m01()) * hx + abs(matrix.m11()) * hy + abs(matrix.m21()) * hz
    return ty - thy
}

private fun transformedMinZ(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val tz = matrix.m02() * cx + matrix.m12() * cy + matrix.m22() * cz + matrix.m32()
    val thz = abs(matrix.m02()) * hx + abs(matrix.m12()) * hy + abs(matrix.m22()) * hz
    return tz - thz
}

private fun transformedMaxX(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val tx = matrix.m00() * cx + matrix.m10() * cy + matrix.m20() * cz + matrix.m30()
    val thx = abs(matrix.m00()) * hx + abs(matrix.m10()) * hy + abs(matrix.m20()) * hz
    return tx + thx
}

private fun transformedMaxY(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val ty = matrix.m01() * cx + matrix.m11() * cy + matrix.m21() * cz + matrix.m31()
    val thy = abs(matrix.m01()) * hx + abs(matrix.m11()) * hy + abs(matrix.m21()) * hz
    return ty + thy
}

private fun transformedMaxZ(bounds: FloatArray, offset: Int, matrix: Matrix4f): Float
{
    val cx = (bounds[offset] + bounds[offset + 3]) * 0.5f
    val cy = (bounds[offset + 1] + bounds[offset + 4]) * 0.5f
    val cz = (bounds[offset + 2] + bounds[offset + 5]) * 0.5f
    val hx = (bounds[offset + 3] - bounds[offset]) * 0.5f
    val hy = (bounds[offset + 4] - bounds[offset + 1]) * 0.5f
    val hz = (bounds[offset + 5] - bounds[offset + 2]) * 0.5f
    val tz = matrix.m02() * cx + matrix.m12() * cy + matrix.m22() * cz + matrix.m32()
    val thz = abs(matrix.m02()) * hx + abs(matrix.m12()) * hy + abs(matrix.m22()) * hz
    return tz + thz
}
