package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.asset.types.Animation
import no.njoh.pulseengine.core.asset.types.Model
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/** 
 * Recursively samples an animation and writes global node transforms into [outTransforms]. 
 */
internal fun collectAnimatedGlobalTransforms(
    node: Model.ModelNode,
    parentTransform: Matrix4f,
    animation: Animation,
    animationTimeTicks: Double,
    localTransformScratch: Matrix4f,
    translationScratch: Vector3f,
    rotationScratch: Quaternionf,
    scaleScratch: Vector3f,
    outTransforms: MutableMap<String, Matrix4f>
) {
    getAnimatedLocalTransform(
        node = node,
        animation = animation,
        animationTimeTicks = animationTimeTicks,
        translation = translationScratch,
        rotation = rotationScratch,
        scale = scaleScratch,
        outTransform = localTransformScratch
    )

    val globalTransform = outTransforms[node.name] ?: Matrix4f().also { outTransforms[node.name] = it }
    globalTransform.set(parentTransform).mul(localTransformScratch)

    for (child in node.children)
    {
        collectAnimatedGlobalTransforms(
            node = child,
            parentTransform = globalTransform,
            animation = animation,
            animationTimeTicks = animationTimeTicks,
            localTransformScratch = localTransformScratch,
            translationScratch = translationScratch,
            rotationScratch = rotationScratch,
            scaleScratch = scaleScratch,
            outTransforms = outTransforms
        )
    }
}

/** 
 * Resolves a node's animated local transform, falling back to its bind-pose transform. 
 * */
internal fun getAnimatedLocalTransform(
    node: Model.ModelNode,
    animation: Animation,
    animationTimeTicks: Double,
    translation: Vector3f,
    rotation: Quaternionf,
    scale: Vector3f,
    outTransform: Matrix4f
): Matrix4f {
    val channel = animation.channelsByNodeName[node.name] ?: return outTransform.set(node.localTransform)

    sampleVectorKeys(channel.positionKeys, animationTimeTicks, animation.durationTicks, node.baseTranslation, translation)
    sampleQuaternionKeys(channel.rotationKeys, animationTimeTicks, animation.durationTicks, node.baseRotation, rotation)
    sampleVectorKeys(channel.scalingKeys, animationTimeTicks, animation.durationTicks, node.baseScale, scale)

    return outTransform.translationRotateScale(translation, rotation, scale)
}

/** 
 * Samples interpolated vector keys into [outVector] without allocating new vectors. 
 */
internal fun sampleVectorKeys(
    keys: List<Animation.VectorKey>,
    timeTicks: Double,
    durationTicks: Double,
    defaultValue: Vector3f,
    outVector: Vector3f
) {
    if (keys.isEmpty())
    {
        outVector.set(defaultValue)
        return
    }

    if (keys.size == 1)
    {
        outVector.set(keys.first().value)
        return
    }

    val lastIndex = keys.lastIndex
    for (index in 0 until lastIndex)
    {
        val currentKey = keys[index]
        val nextKey = keys[index + 1]

        if (timeTicks <= nextKey.time)
        {
            val blendFactor = if (nextKey.time > currentKey.time)
                ((timeTicks - currentKey.time) / (nextKey.time - currentKey.time)).toFloat().coerceIn(0f, 1f)
            else
                0f

            outVector.set(currentKey.value).lerp(nextKey.value, blendFactor)
            return
        }
    }

    val currentKey = keys[lastIndex]
    val nextKey = keys[0]
    val nextTime = nextKey.time + durationTicks
    val wrappedTime = if (timeTicks < currentKey.time) timeTicks + durationTicks else timeTicks
    val blendFactor = if (nextTime > currentKey.time)
        ((wrappedTime - currentKey.time) / (nextTime - currentKey.time)).toFloat().coerceIn(0f, 1f)
    else
        0f

    outVector.set(currentKey.value).lerp(nextKey.value, blendFactor)
}

/** 
 * Samples interpolated quaternion keys into [outQuaternion] without allocating new quaternions. 
 */
internal fun sampleQuaternionKeys(
    keys: List<Animation.QuaternionKey>,
    timeTicks: Double,
    durationTicks: Double,
    defaultValue: Quaternionf,
    outQuaternion: Quaternionf
) {
    if (keys.isEmpty())
    {
        outQuaternion.set(defaultValue)
        return
    }

    if (keys.size == 1)
    {
        outQuaternion.set(keys.first().value)
        return
    }

    val lastIndex = keys.lastIndex
    for (index in 0 until lastIndex)
    {
        val currentKey = keys[index]
        val nextKey = keys[index + 1]

        if (timeTicks <= nextKey.time)
        {
            val blendFactor = if (nextKey.time > currentKey.time)
                ((timeTicks - currentKey.time) / (nextKey.time - currentKey.time)).toFloat().coerceIn(0f, 1f)
            else
                0f

            outQuaternion.set(currentKey.value).slerp(nextKey.value, blendFactor)
            return
        }
    }

    val currentKey = keys[lastIndex]
    val nextKey = keys[0]
    val nextTime = nextKey.time + durationTicks
    val wrappedTime = if (timeTicks < currentKey.time) timeTicks + durationTicks else timeTicks
    val blendFactor = if (nextTime > currentKey.time)
        ((wrappedTime - currentKey.time) / (nextTime - currentKey.time)).toFloat().coerceIn(0f, 1f)
    else
        0f

    outQuaternion.set(currentKey.value).slerp(nextKey.value, blendFactor)
}