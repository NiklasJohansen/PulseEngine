package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.asset.types.Animation.QuaternionKey
import no.njoh.pulseengine.core.asset.types.Animation.VectorKey
import no.njoh.pulseengine.core.asset.types.Material.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Material.CullMode.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.buildSkinningBounds
import no.njoh.pulseengine.core.shared.utils.collectAnimatedGlobalTransforms
import no.njoh.pulseengine.core.shared.utils.getSkinnedSubMeshBounds
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.transformAabb
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIQuatKey
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.AITexture
import org.lwjgl.assimp.AIVectorKey
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.stb.STBImage.STBI_rgb_alpha
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import java.nio.ByteBuffer

class Model(filePath: String, name: String) : Asset(filePath, name) 
{
    var vao: VertexArrayObject?  = null; private set
    var vbo: StaticBufferObject? = null; private set
    var ebo: StaticBufferObject? = null; private set

    var vertices = FloatArray(0); private set
    var indices  = IntArray(0);   private set

    var subMeshInstances = emptyList<SubMeshInstance>(); private set
    var subMeshes        = emptyList<SubMesh>();         private set
    var materials        = emptyList<MeshMaterial>();    private set
    var bones            = emptyList<Bone>();            private set

    var hasNormals   = false; private set
    var hasTangents  = false; private set
    var hasTexCoords = false; private set
    var hasBones     = false; private set

    private var animations                     = ArrayList<Animation>()
    private val embeddedTextures               = HashMap<Int, EmbeddedTexture>()
    private val globalNodeTransforms           = HashMap<String, Matrix4f>()
    private val nodesByName                    = HashMap<String, ModelNode>()
    private var nodeHierarchy                  = null as ModelNode?
    private val bindPoseBoneMatricesByNodeName = HashMap<String, Array<Matrix4f>>()
    private val skeletonPoseCache       = ArrayList<AnimatedSkeletonPose>(4)
    private var activeSkeletonPoseCacheCount   = 0
    private var poseCacheFrameNumber           = Long.MIN_VALUE

    override fun load()
    {
        try { loadWithAssimp() }
        catch (e: Exception) { Logger.error { "Failed to load Mesh $filePath: ${e.message}" } }
    }
    
    override fun unload()
    {
        this.vao = null
        this.vbo = null
        this.ebo = null
        this.vertices = FloatArray(0)
        this.indices = IntArray(0)
    }

    fun onUploaded(vao: VertexArrayObject, vbo: StaticBufferObject, ebo: StaticBufferObject)
    {
        this.vbo = vbo
        this.ebo = ebo
        this.vao = vao
        this.vertices = FloatArray(0) // CPU-side culling data is baked at import time
        this.indices = IntArray(0)
    }

    private fun loadWithAssimp()
    {
        val flags =
            aiProcess_Triangulate or
            aiProcess_JoinIdenticalVertices or
            aiProcess_CalcTangentSpace or
            aiProcess_GenNormals or
            aiProcess_ImproveCacheLocality or
            aiProcess_OptimizeMeshes or
            aiProcess_SortByPType

        val scene = aiImportFile(filePath, flags) ?: throw RuntimeException(aiGetErrorString())

        try
        {
            readMeshes(scene)
            readEmbeddedTextures(scene)
            readMaterials(scene)
            readAnimations(scene)

            scene.mRootNode()?.let()
            {
                nodeHierarchy = readNodeHierarchy(it)
                globalNodeTransforms.clear()
                nodesByName.clear()
                collectGlobalNodeTransforms(nodeHierarchy!!, Matrix4f(), globalNodeTransforms)
                bindPoseBoneMatricesByNodeName.clear()
                buildSubMeshInstances(it, Matrix4f(), mutableListOf<SubMeshInstance>().also { subMeshInstances = it })
            }
        }
        catch (e: Exception) { throw e }
        finally { aiReleaseImport(scene) }
    }

    // Read scene //////////////////////////////////////////////////////////////
    
    private fun readMeshes(scene: AIScene)
    {
        val meshCount = scene.mNumMeshes()
        if (meshCount == 0)
            throw RuntimeException("No meshes in scene: $filePath")

        val meshPointers = scene.mMeshes() ?: throw RuntimeException("Scene meshes pointer is null: $filePath")

        var totalVertices = 0
        var totalIndices  = 0
        var hasNormals    = false
        var hasTangents   = false
        var hasTexCoords  = false
        var hasBones      = false

        for (i in 0 until meshCount)
        {
            val mesh = AIMesh.create(meshPointers[i])
            totalVertices += mesh.mNumVertices()
            totalIndices  += mesh.mNumFaces() * 3

            if (mesh.mNormals() != null)
                hasNormals = true

            if (mesh.mTextureCoords(0) != null)
                hasTexCoords = true

            if (mesh.mTangents() != null && mesh.mBitangents() != null)
                hasTangents = true

            if (mesh.mNumBones() > 0)
                hasBones = true
        }

        val stride = 3 +
            (if (hasNormals) 3 else 0) +
            (if (hasTangents) 3 + 3 else 0) +
            (if (hasTexCoords) 2 else 0) +
            (if (hasBones) MAX_BONE_INFLUENCES + MAX_BONE_INFLUENCES else 0)

        val subMeshes       = mutableListOf<SubMesh>()
        val bones           = mutableListOf<Bone>()
        val boneIndexByName = HashMap<String, Int>()
        val vertexData      = FloatArray(totalVertices * stride)
        val indices         = IntArray(totalIndices)

        // TODO: need to create flat primitive array not array of objects
        val vertexInfluences = Array(totalVertices) { VertexInfluence() }

        var dst = 0                 // write cursor in vertexData
        var globalVertexOffset = 0  // index offset per submesh
        var indexOffset = 0         // write cursor in indices[]

        for (m in 0 until meshCount)
        {
            val mesh         = AIMesh.create(meshPointers[m])
            val numVertices  = mesh.mNumVertices()
            val numFaces     = mesh.mNumFaces()
            val vertices     = mesh.mVertices()
            val normals      = mesh.mNormals()
            val tangents     = mesh.mTangents()
            val bitangents   = mesh.mBitangents()
            val texCoords    = mesh.mTextureCoords(0)
            val materialIdx  = mesh.mMaterialIndex()

            if (hasBones)
            {
                readBoneWeights(
                    mesh = mesh,
                    globalVertexOffset = globalVertexOffset,
                    vertexInfluences = vertexInfluences,
                    bones = bones,
                    boneIndexByName = boneIndexByName
                )

                for (i in globalVertexOffset until (globalVertexOffset + numVertices))
                    vertexInfluences[i].normalize()
            }

            var xMin = Float.POSITIVE_INFINITY
            var yMin = Float.POSITIVE_INFINITY
            var zMin = Float.POSITIVE_INFINITY
            var xMax = Float.NEGATIVE_INFINITY
            var yMax = Float.NEGATIVE_INFINITY
            var zMax = Float.NEGATIVE_INFINITY

            // Vertices
            for (i in 0 until numVertices)
            {
                val v = vertices[i]
                val x = v.x()
                val y = v.y()
                val z = v.z()
                
                if (x < xMin) xMin = x
                if (y < yMin) yMin = y
                if (z < zMin) zMin = z
                if (x > xMax) xMax = x
                if (y > yMax) yMax = y
                if (z > zMax) zMax = z
 
                vertexData[dst++] = x
                vertexData[dst++] = y
                vertexData[dst++] = z

                if (hasNormals)
                {
                    if (normals != null)
                    {
                        val n = normals[i]
                        vertexData[dst++] = n.x()
                        vertexData[dst++] = n.y()
                        vertexData[dst++] = n.z()
                    }
                    else
                    {
                        vertexData[dst++] = 0f
                        vertexData[dst++] = 0f
                        vertexData[dst++] = 0f
                    }
                }

                if (hasTangents)
                {
                    if (tangents != null && bitangents != null)
                    {
                        val t = tangents[i]
                        val b = bitangents[i]
                        vertexData[dst++] = t.x()
                        vertexData[dst++] = t.y()
                        vertexData[dst++] = t.z()
                        vertexData[dst++] = b.x()
                        vertexData[dst++] = b.y()
                        vertexData[dst++] = b.z()
                    }
                    else
                    {
                        vertexData[dst++] = 0f; vertexData[dst++] = 0f; vertexData[dst++] = 0f
                        vertexData[dst++] = 0f; vertexData[dst++] = 0f; vertexData[dst++] = 0f
                    }
                }

                if (hasTexCoords)
                {
                    if (texCoords != null)
                    {
                        val t = texCoords[i]
                        vertexData[dst++] = t.x()
                        vertexData[dst++] = t.y()
                    }
                    else
                    {
                        vertexData[dst++] = 0f
                        vertexData[dst++] = 0f
                    }
                }

                if (hasBones)
                {
                    val influence = vertexInfluences[globalVertexOffset + i]
                    for (slot in 0 until MAX_BONE_INFLUENCES)
                        vertexData[dst++] = influence.boneIndices[slot].toFloat()

                    for (slot in 0 until MAX_BONE_INFLUENCES)
                        vertexData[dst++] = influence.boneWeights[slot]
                }
            }

            // Indices
            val faces = mesh.mFaces()
            val subMeshIndexStart = indexOffset
            for (i in 0 until numFaces)
            {
                val face   = faces[i]
                val idxBuf = face.mIndices() // 3 indices due to Triangulate
                indices[indexOffset + 0] = globalVertexOffset + idxBuf[0]
                indices[indexOffset + 1] = globalVertexOffset + idxBuf[1]
                indices[indexOffset + 2] = globalVertexOffset + idxBuf[2]
                indexOffset += 3
            }

            subMeshes += SubMesh(
                index = subMeshes.size,
                indexStart = subMeshIndexStart,
                indexCount = numFaces * 3,
                vertexStart = globalVertexOffset,
                vertexCount = numVertices,
                materialIndex = materialIdx,
                vertexStride = stride,
                localBounds = Aabb(xMin, yMin, zMin, xMax, yMax, zMax)
            )

            globalVertexOffset += numVertices
        }

        this.vertices     = vertexData
        this.indices      = indices
        this.hasNormals   = hasNormals
        this.hasTangents  = hasTangents
        this.hasTexCoords = hasTexCoords
        this.hasBones     = bones.isNotEmpty()
        this.bones        = bones
        this.subMeshes    = if (this.hasBones)
        {
            subMeshes.map {
                it.copy(
                    skinningBounds = buildSkinningBounds(
                        subMesh = it,
                        vertices = vertexData,
                        bones = bones,
                        hasBones = this.hasBones,
                        hasNormals = hasNormals,
                        hasTangents = hasTangents,
                        hasTexCoords = hasTexCoords
                    )
                )
            }
        }
        else subMeshes
    }

    private fun readBoneWeights(
        mesh: AIMesh,
        globalVertexOffset: Int,
        vertexInfluences: Array<VertexInfluence>,
        bones: MutableList<Bone>,
        boneIndexByName: MutableMap<String, Int>
    ) {
        val bonePointers = mesh.mBones() ?: return

        for (boneIndex in 0 until mesh.mNumBones())
        {
            val bone = AIBone.create(bonePointers[boneIndex])
            val boneName = bone.mName().dataString()
            val globalBoneIndex = boneIndexByName.getOrPut(boneName)
            {
                bones += Bone(
                    index = bones.size,
                    name = boneName,
                    nodeName = bone.readOptionalNodeName() ?: boneName,
                    armatureName = bone.readOptionalArmatureName(),
                    offsetMatrix = bone.mOffsetMatrix().toMatrix4f()
                )
                bones.lastIndex
            }

            val weights = bone.mWeights()
            for (weightIndex in 0 until bone.mNumWeights())
            {
                val weight = weights[weightIndex]
                val vertexIndex = globalVertexOffset + weight.mVertexId()

                if (vertexIndex !in vertexInfluences.indices)
                {
                    Logger.warn { "Bone weight vertex index out of bounds in $filePath: $vertexIndex" }
                    continue
                }

                vertexInfluences[vertexIndex].add(globalBoneIndex, weight.mWeight())
            }
        }
    }

    private fun readNodeHierarchy(node: AINode): ModelNode
    {
        val childPointers = node.mChildren()
        val children = ArrayList<ModelNode>(node.mNumChildren())
        if (childPointers != null)
        {
            for (i in 0 until node.mNumChildren())
                children += readNodeHierarchy(AINode.create(childPointers[i]))
        }

        val meshIndicesBuffer = node.mMeshes()
        val meshIndices = if (meshIndicesBuffer != null)
        {
            IntArray(node.mNumMeshes()) { meshIndex -> meshIndicesBuffer[meshIndex] }
        }
        else IntArray(0)

        val localTransform = node.mTransformation().toMatrix4f()
        val baseTranslation = localTransform.getTranslation(Vector3f())
        val baseRotation = localTransform.getNormalizedRotation(Quaternionf())
        val baseScale = localTransform.getScale(Vector3f())

        return ModelNode(
            name = node.mName().dataString(),
            localTransform = localTransform,
            baseTranslation = baseTranslation,
            baseRotation = baseRotation,
            baseScale = baseScale,
            meshIndices = meshIndices,
            children = children
        )
    }
    
    private fun readMaterials(scene: AIScene)
    {
        val numMaterials = scene.mNumMaterials()
        val materialPointers = scene.mMaterials() ?: return
        val materials = mutableListOf<MeshMaterial>()

        for (i in 0 until numMaterials)
        {
            val material = AIMaterial.create(materialPointers[i])
            val materialName = material.getMaterialStringProp(AI_MATKEY_NAME) ?: "material_${materials.size}"
            val basePath = this.filePath.substringBeforeLast("/") + "/"

            val baseColor = material.getMaterialColorProp(AI_MATKEY_BASE_COLOR)
                ?: material.getMaterialColorProp(AI_MATKEY_COLOR_DIFFUSE)
                ?: Color(1f, 1f, 1f, 1f)

            val albedoPath = material.getTexturePath(aiTextureType_DIFFUSE, basePath)
                ?: material.getTexturePath(aiTextureType_BASE_COLOR, basePath)

            val normalPath = material.getTexturePath(aiTextureType_NORMALS, basePath)

            val aoPath = material.getTexturePath(aiTextureType_AMBIENT, basePath)
                ?: material.getTexturePath(aiTextureType_AMBIENT_OCCLUSION, basePath)
                ?: material.getTexturePath(aiTextureType_LIGHTMAP, basePath)

            val metalRoughPath = material.getTexturePath(aiTextureType_METALNESS, basePath)
                ?: material.getTexturePath(aiTextureType_DIFFUSE_ROUGHNESS, basePath)
                ?: material.getTexturePath(aiTextureType_UNKNOWN, basePath)

            val emissivePath = material.getTexturePath(aiTextureType_EMISSIVE, basePath)

            val cullMode = if (material.getMaterialIntProp(AI_MATKEY_TWOSIDED) == 1) "NONE" else "BACK"

            val blendMode = when (material.getMaterialStringProp(AI_MATKEY_GLTF_ALPHAMODE)?.uppercase())
            {
                "MASK"  -> MASK
                "BLEND" -> TRANSPARENT
                else    -> OPAQUE
            }

            val alphaCutoff = material.getMaterialFloatProp(AI_MATKEY_GLTF_ALPHACUTOFF) ?: 0.5f

            val metallicFactor = material.getMaterialFloatProp(AI_MATKEY_METALLIC_FACTOR)?.coerceIn(0f, 1f) ?: 1f

            val roughnessFactor = material.getMaterialFloatProp(AI_MATKEY_ROUGHNESS_FACTOR)?.coerceIn(0f, 1f) ?: 1f

            val emissiveFactor = material.getMaterialColorProp(AI_MATKEY_COLOR_EMISSIVE) ?: Color(0f, 0f, 0f, 1f)

            val emissiveStrength = material.getMaterialFloatProp(AI_MATKEY_EMISSIVE_INTENSITY) ?: 1f

            materials += MeshMaterial(
                name = this.name + "_" + materialName,
                baseColor = baseColor,
                albedoPath = albedoPath,
                normalPath = normalPath,
                aoMetalRoughPath = metalRoughPath,
                emissivePath = emissivePath,
                cullMode = cullMode,
                blendMode = blendMode,
                alphaCutoff = alphaCutoff,
                metallicFactor = metallicFactor,
                roughnessFactor = roughnessFactor,
                emissiveFactor = emissiveFactor.multiplyRgb(emissiveStrength),
                occlusionStrength = if (aoPath != null && aoPath == metalRoughPath) 1f else 0f
            )
        }

        this.materials = materials
    }

    private fun readEmbeddedTextures(scene: AIScene)
    {
        val numTex = scene.mNumTextures()
        if (numTex == 0) return

        val texPtrs = scene.mTextures() ?: return

        for (i in 0 until numTex)
        {
            val tex = AITexture.create(texPtrs[i])

            if (tex.mHeight() == 0)
            {
                // Compressed bytes (PNG/JPG/etc). mWidth == byte length.
                val encoded = tex.pcDataCompressed()
                encoded.position(0)
                encoded.limit(tex.mWidth())

                val w = IntArray(1)
                val h = IntArray(1)
                val comp = IntArray(1)

                val pixels = stbi_load_from_memory(encoded, w, h, comp, STBI_rgb_alpha)
                    ?: throw RuntimeException("stbi_load_from_memory failed for embedded *$i: ${stbi_failure_reason()}")

                embeddedTextures[i] = EmbeddedTexture(w[0], h[0], pixels, freeWithStbi = true)
            }
            else
            {
                // Convert raw aiTexel array with format BGRA to RGBA
                val w = tex.mWidth()
                val h = tex.mHeight()
                val texels = tex.pcData()

                val rgba = BufferUtils.createByteBuffer(w * h * 4)
                for (p in 0 until (w * h))
                {
                    val t = texels[p]
                    rgba.put(t.r()).put(t.g()).put(t.b()).put(t.a())
                }
                rgba.flip()

                embeddedTextures[i] = EmbeddedTexture(w, h, rgba, freeWithStbi = false)
            }
        }
    }

    private fun readAnimations(scene: AIScene)
    {
        val animationCount = scene.mNumAnimations()
        if (animationCount == 0)
        {
            animations = ArrayList()
            return
        }

        val animationPointers = scene.mAnimations()
        if (animationPointers == null)
        {
            animations = ArrayList()
            return
        }

        val animations = ArrayList<Animation>(animationCount)

        for (i in 0 until animationCount)
        {
            val animation = AIAnimation.create(animationPointers[i])
            val animationName = animation.mName().dataString().ifBlank { "animation_$i" }
            val ticksPerSecond = animation.mTicksPerSecond().takeIf { it > 0.0 } ?: 1.0
            val channels = mutableListOf<Animation.NodeAnimation>()
            val channelPointers = animation.mChannels()

            if (channelPointers != null)
            {
                for (channelIndex in 0 until animation.mNumChannels())
                {
                    val channel = AINodeAnim.create(channelPointers[channelIndex])
                    channels += Animation.NodeAnimation(
                        nodeName = channel.mNodeName().dataString(),
                        positionKeys = channel.mPositionKeys().toVectorKeys(channel.mNumPositionKeys()),
                        rotationKeys = channel.mRotationKeys().toQuaternionKeys(channel.mNumRotationKeys()),
                        scalingKeys = channel.mScalingKeys().toVectorKeys(channel.mNumScalingKeys()),
                        preState = channel.mPreState(),
                        postState = channel.mPostState()
                    )
                }
            }

            animations += Animation(
                filePath = filePath,
                modelName = name,
                name = name + "_" + animationName,
                index = i,
                durationTicks = animation.mDuration(),
                ticksPerSecond = ticksPerSecond,
                channels = channels
            )
        }

        this.animations = animations
    }

    // Runtime queries //////////////////////////////////////////////////////////////

    /** 
     * Returns cached bind-pose bone matrices for the mesh node, or `null` if the model is not skinned. 
     */
    fun getBindPoseBoneMatrices(nodeName: String): Array<Matrix4f>?
    {
        if (bones.isEmpty())
            return null

        val cacheKey = nodeName.ifBlank { "__default__" }
        bindPoseBoneMatricesByNodeName[cacheKey]?.let { return it }

        val meshNodeGlobalTransform = globalNodeTransforms[nodeName]
        val inverseMeshNodeTransform = if (meshNodeGlobalTransform != null) Matrix4f(meshNodeGlobalTransform).invert() else Matrix4f()

        val palette = Array(bones.size)
        {
            val bone = bones[it]
            val boneNodeTransform = globalNodeTransforms[bone.nodeName] ?: globalNodeTransforms[bone.name]

            if (boneNodeTransform == null)
            {
                Logger.warn { "Bone node '${bone.nodeName}' was not found in hierarchy for $filePath" }
                Matrix4f()
            }
            else
            {
                Matrix4f(inverseMeshNodeTransform)
                    .mul(boneNodeTransform)
                    .mul(bone.offsetMatrix)
            }
        }

        bindPoseBoneMatricesByNodeName[cacheKey] = palette
        return palette
    }

    /** 
     * Returns the frame-local animated mesh pose for the given node and animation state. 
     */
    fun getAnimatedPose(nodeName: String, animation: Animation?, animationTimeSeconds: Float, frameNumber: Long): AnimatedMeshPose?
    {
        if (bones.isEmpty() || animation == null || animation.modelName != name)
            return null

        val rootNode = nodeHierarchy ?: return null

        if (poseCacheFrameNumber != frameNumber)
        {
            poseCacheFrameNumber = frameNumber
            activeSkeletonPoseCacheCount = 0
        }

        val timeKey = animationTimeSeconds.toBits()
        val skeletonPose = getAnimatedSkeletonPose(animation, animationTimeSeconds, timeKey, rootNode)

        return skeletonPose.getAnimatedMeshPose(nodeName)
    }
    
    // Helpers ////////////////////////////////////////////////////////////////////////

    private fun buildSubMeshInstances(node: AINode, parentWorld: Matrix4f, instances: MutableList<SubMeshInstance>)
    {
        val local = node.mTransformation().toMatrix4f()
        val world = Matrix4f(parentWorld).mul(local)

        val nodeName = node.mName().dataString()
        val meshIndices = node.mMeshes()

        if (meshIndices != null)
        {
            for (i in 0 until node.mNumMeshes())
            {
                val meshIndex   = meshIndices[i] // aiMesh index
                val subMesh     = subMeshes[meshIndex]
                val localBounds = if (hasBones) getBindPoseSubMeshBounds(subMesh, nodeName) else subMesh.localBounds
                val worldBounds = transformAabb(localBounds, world)

                instances += SubMeshInstance(
                    subMesh = subMesh,
                    transform = Matrix4f(world),
                    cullingBounds = localBounds,
                    worldBounds = worldBounds,
                    nodeName = nodeName
                )
            }
        }

        val children = node.mChildren() ?: return
        for (i in 0 until node.mNumChildren())
            buildSubMeshInstances(AINode.create(children[i]), world, instances)
    }
    
    private fun getBindPoseSubMeshBounds(subMesh: SubMesh, nodeName: String): Aabb
    {
        val aabb = Aabb()
        val boneMatrices = getBindPoseBoneMatrices(nodeName) ?: emptyArray()
        getSkinnedSubMeshBounds(subMesh, boneMatrices, hasBones, aabb)
        return aabb
    }
    
    private fun getAnimatedSkeletonPose(animation: Animation, animationTimeSeconds: Float, timeKey: Int, rootNode: ModelNode): AnimatedSkeletonPose
    {
        for (i in 0 until activeSkeletonPoseCacheCount)
        {
            val pose = skeletonPoseCache[i]
            if (pose.matches(animation.index, timeKey))
                return pose
        }

        val poseIndex = activeSkeletonPoseCacheCount++
        val pose = skeletonPoseCache.getOrElse(poseIndex) { AnimatedSkeletonPose().also { skeletonPoseCache += it } }
        pose.animationIndex = animation.index
        pose.animationTimeKey = timeKey
        pose.activeMeshPoseCacheCount = 0

        collectAnimatedGlobalTransforms(
            node = rootNode,
            parentTransform = IDENTITY_MATRIX,
            animation = animation,
            animationTimeTicks = animation.wrapTimeSecondsToTicks(animationTimeSeconds.toDouble()),
            localTransformScratch = pose.localTransformScratch,
            translationScratch = pose.translationScratch,
            rotationScratch = pose.rotationScratch,
            scaleScratch = pose.scaleScratch,
            outTransforms = pose.animatedGlobalTransforms
        )

        return pose
    }

    private fun collectGlobalNodeTransforms(node: ModelNode, parentTransform: Matrix4f, outGlobalNodeTransforms: MutableMap<String, Matrix4f>)
    {
        val globalTransform = Matrix4f(parentTransform).mul(node.localTransform)
        outGlobalNodeTransforms[node.name] = globalTransform
        nodesByName[node.name] = node

        for (child in node.children)
            collectGlobalNodeTransforms(child, globalTransform, outGlobalNodeTransforms)
    }
    
    private fun AIMatrix4x4.toMatrix4f(): Matrix4f =
        Matrix4f(
            a1(), b1(), c1(), d1(),
            a2(), b2(), c2(), d2(),
            a3(), b3(), c3(), d3(),
            a4(), b4(), c4(), d4()
        )

    private fun AIBone.readOptionalNodeName(): String? =
        runCatching { mNode() }
            .getOrNull()
            ?.let { node -> runCatching { node.mName().dataString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    private fun AIBone.readOptionalArmatureName(): String? =
        runCatching { mArmature() }
            .getOrNull()
            ?.let { node -> runCatching { node.mName().dataString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    private fun AIVectorKey.Buffer?.toVectorKeys(count: Int): List<VectorKey>
    {
        if (this == null || count == 0)
            return emptyList()

        return List(count)
        {
            val key = this[it]
            val value = key.mValue()
            VectorKey(
                time = key.mTime(),
                value = Vector3f(value.x(), value.y(), value.z()),
                interpolation = key.mInterpolation()
            )
        }
    }

    private fun AIQuatKey.Buffer?.toQuaternionKeys(count: Int): List<QuaternionKey>
    {
        if (this == null || count == 0)
            return emptyList()

        return List(count)
        {
            val key = this[it]
            val value = key.mValue()
            QuaternionKey(
                time = key.mTime(),
                value = Quaternionf(value.x(), value.y(), value.z(), value.w()),
                interpolation = key.mInterpolation()
            )
        }
    }

    private fun AIMaterial.getMaterialStringProp(prop: String): String? = AIString.calloc().use()
    {
        val res = aiGetMaterialString(this, prop, aiTextureType_NONE, 0, it)
        if (res == aiReturn_SUCCESS) it.dataString() else null
    }

    private fun AIMaterial.getMaterialFloatProp(prop: String): Float? = AIString.calloc().use()
    {
        val tmp = FloatArray(1)
        val res = aiGetMaterialFloatArray(this, prop, aiTextureType_NONE, 0, tmp, intArrayOf(1))
        if (res == aiReturn_SUCCESS) tmp[0] else null
    }

    private fun AIMaterial.getMaterialIntProp(prop: String): Int? = AIString.calloc().use()
    {
        val tmp = IntArray(1)
        val res = aiGetMaterialIntegerArray(this, prop, aiTextureType_NONE, 0, tmp, intArrayOf(1))
        if (res == aiReturn_SUCCESS) tmp[0] else null
    }

    private fun AIMaterial.getMaterialColorProp(prop: String): Color? = AIColor4D.calloc().use()
    {
        val res = aiGetMaterialColor(this, prop, aiTextureType_NONE, 0,it)
        if (res == aiReturn_SUCCESS) Color(it.r(), it.g(), it.b(), it.a()) else null
    }

    private fun AIMaterial.getTexturePath(type: Int, basePath: String): String? = AIString.calloc().use()
    {
        if (aiGetMaterialTextureCount(this, type) < 1)
            return null

        if (aiGetMaterialTexture(this, type, 0, it, null as IntArray?, null, null, null, null, null) != aiReturn_SUCCESS)
            return null

        val path = it.dataString()

        return if (path.startsWith("*")) path else basePath + path.replace("%20", " ")
    }

    // Sub assets ////////////////////////////////////////////////////////////////////////
    
    override fun getSubAssets(): List<Asset> 
    {
        val assets = mutableListOf<Asset>()
        assets += animations
        for (mat in materials)
        {
            val albedo   = createTexture(mat.albedoPath,       mat.name + "_albedo",       SRGBA8)
            val normal   = createTexture(mat.normalPath,       mat.name + "_normal",       RGBA8)
            val aomr     = createTexture(mat.aoMetalRoughPath, mat.name + "_aoMetalRough", RGBA8)
            val emissive = createTexture(mat.emissivePath,     mat.name + "_emissive",     SRGBA8)
            
            val cullMode = when (mat.cullMode.uppercase())
            {
                "NONE" -> NONE
                else   -> BACK
            }

            albedo?.let { assets += it }
            normal?.let { assets += it }
            aomr?.let { assets += it }
            emissive?.let { assets += it }
            assets += Material(
                name = mat.name,
                baseColor = mat.baseColor,
                albedo = albedo,
                normal = normal,
                aoMetalRough = aomr,
                emissive = emissive,
                height = null,
                cullMode = cullMode,
                blendMode = mat.blendMode,
                alphaCutoff = mat.alphaCutoff,
                metallicFactor = mat.metallicFactor,
                roughnessFactor = mat.roughnessFactor,
                emissiveFactor = mat.emissiveFactor,
                occlusionStrength = mat.occlusionStrength,
                normalScale = 1f
            )
        }
        return assets  
    }

    private fun createTexture(path: String?, assetName: String, format: TextureFormat): Texture?
    {
        if (path.isNullOrEmpty()) return null

        val texture = Texture(path, assetName, format = format)
        
        if (path.startsWith("*")) // Embedded texture
        {
            val idx = path.substring(1).toIntOrNull() ?: return null
            val tex = embeddedTextures[idx] ?: return null
            texture.loadFrom(tex.rgbaPixels, tex.width, tex.height, freeWithStbi = tex.freeWithStbi)
        }

        return texture
    }

    // Data classes ////////////////////////////////////////////////////////////////////////
    
    private data class EmbeddedTexture(
        val width: Int, 
        val height: Int, 
        val rgbaPixels: ByteBuffer, 
        val freeWithStbi: Boolean
    )

    private class VertexInfluence
    {
        val boneIndices = IntArray(MAX_BONE_INFLUENCES) { -1 }
        val boneWeights = FloatArray(MAX_BONE_INFLUENCES)

        fun add(boneIndex: Int, weight: Float)
        {
            if (weight <= 0f)
                return

            for (slot in 0 until MAX_BONE_INFLUENCES)
            {
                if (boneIndices[slot] == boneIndex)
                {
                    boneWeights[slot] += weight
                    return
                }
            }

            for (slot in 0 until MAX_BONE_INFLUENCES)
            {
                if (boneIndices[slot] == -1)
                {
                    boneIndices[slot] = boneIndex
                    boneWeights[slot] = weight
                    return
                }
            }

            var smallestSlot = 0
            var smallestWeight = boneWeights[0]
            for (slot in 1 until MAX_BONE_INFLUENCES)
            {
                if (boneWeights[slot] < smallestWeight)
                {
                    smallestWeight = boneWeights[slot]
                    smallestSlot = slot
                }
            }

            if (weight > smallestWeight)
            {
                boneIndices[smallestSlot] = boneIndex
                boneWeights[smallestSlot] = weight
            }
        }

        fun normalize()
        {
            var totalWeight = 0f
            for (slot in 0 until MAX_BONE_INFLUENCES)
                totalWeight += boneWeights[slot]

            if (totalWeight <= 0f)
                return

            for (slot in 0 until MAX_BONE_INFLUENCES)
                boneWeights[slot] /= totalWeight
        }
    }

    data class SubMesh(
        val index: Int,
        val indexStart: Int,
        val indexCount: Int,
        val vertexStart: Int,
        val vertexCount: Int,
        val materialIndex: Int,
        val vertexStride: Int,
        val localBounds: Aabb,
        val skinningBounds: SkinningBounds? = null
    )

    data class SubMeshInstance(
        val subMesh: SubMesh,
        val transform: Matrix4f,
        val cullingBounds: Aabb,
        val worldBounds: Aabb,
        val nodeName: String
    )
    
    data class MeshMaterial(
        val name: String,
        val baseColor: Color,
        val albedoPath: String?,
        val normalPath: String?,
        val aoMetalRoughPath: String?,
        val emissivePath: String?,
        val cullMode: String,
        val blendMode: BlendMode,
        val alphaCutoff: Float,
        val metallicFactor: Float,
        val roughnessFactor: Float,
        val emissiveFactor: Color,
        val occlusionStrength: Float
    )

    class Aabb(
        var xMin: Float = 0f,
        var yMin: Float = 0f,
        var zMin: Float = 0f,
        var xMax: Float = 0f,
        var yMax: Float = 0f,
        var zMax: Float = 0f
    ) {
        fun set(xMin: Float, yMin: Float, zMin: Float, xMax: Float, yMax: Float, zMax: Float): Aabb
        {
            this.xMin = xMin
            this.yMin = yMin
            this.zMin = zMin
            this.xMax = xMax
            this.yMax = yMax
            this.zMax = zMax
            return this
        }

        fun set(other: Aabb): Aabb =
            set(other.xMin, other.yMin, other.zMin, other.xMax, other.yMax, other.zMax)
    }

    data class SkinningBounds(
        val boneIndices: IntArray,
        val restPoseBounds: FloatArray,
        val staticBounds: Aabb?
    )

    data class Bone(
        val index: Int,
        val name: String,
        val nodeName: String,
        val armatureName: String?,
        val offsetMatrix: Matrix4f
    )

    data class ModelNode(
        val name: String,
        val localTransform: Matrix4f,
        val baseTranslation: Vector3f,
        val baseRotation: Quaternionf,
        val baseScale: Vector3f,
        val meshIndices: IntArray,
        val children: List<ModelNode>
    )

    internal inner class AnimatedSkeletonPose
    {
        val animatedGlobalTransforms = HashMap<String, Matrix4f>(nodesByName.size)
        val localTransformScratch = Matrix4f()
        val translationScratch = Vector3f()
        val rotationScratch = Quaternionf()
        val scaleScratch = Vector3f()
        val meshPoseCache = ArrayList<AnimatedMeshPose>(4)
        var activeMeshPoseCacheCount = 0
        var animationIndex = -1
        var animationTimeKey = 0

        fun matches(animationIndex: Int, animationTimeKey: Int): Boolean =
            this.animationIndex == animationIndex && this.animationTimeKey == animationTimeKey

        /** 
         * Returns a node-specific mesh pose derived from this sampled skeleton pose. 
         */
        fun getAnimatedMeshPose(nodeName: String): AnimatedMeshPose
        {
            val nodeKey = nodeName.ifBlank { "__default__" }
            for (i in 0 until activeMeshPoseCacheCount)
            {
                val pose = meshPoseCache[i]
                if (pose.nodeKey == nodeKey)
                    return pose
            }

            val poseIndex = activeMeshPoseCacheCount++
            val pose = meshPoseCache.getOrElse(poseIndex) { AnimatedMeshPose().also { meshPoseCache += it } }
            pose.nodeKey = nodeKey
            pose.subMeshBoundsValid.fill(false)
            globalNodeTransforms[nodeName]
                ?.let { pose.inverseMeshNodeTransform.set(it).invert() } 
                ?: run { pose.inverseMeshNodeTransform.identity() }

            for (boneIndex in bones.indices)
            {
                val bone = bones[boneIndex]
                val boneNodeTransform = animatedGlobalTransforms[bone.nodeName]
                    ?: animatedGlobalTransforms[bone.name]
                    ?: globalNodeTransforms[bone.nodeName]
                    ?: globalNodeTransforms[bone.name]

                val matrix = pose.boneMatrices[boneIndex]
                if (boneNodeTransform == null)
                {
                    Logger.warn { "Animated bone node '${bone.nodeName}' was not found in hierarchy for $filePath" }
                    matrix.identity()
                }
                else matrix.set(pose.inverseMeshNodeTransform).mul(boneNodeTransform).mul(bone.offsetMatrix)
            }

            return pose
        }
    }

    inner class AnimatedMeshPose
    {
        var nodeKey = ""
        val boneMatrices = Array(bones.size) { Matrix4f() }
        val inverseMeshNodeTransform = Matrix4f()
        val subMeshBounds = Array(subMeshes.size) { Aabb() }
        val subMeshBoundsValid = BooleanArray(subMeshes.size)

        fun getBounds(subMesh: SubMesh): Aabb
        {
            if (!subMeshBoundsValid[subMesh.index])
            {
                getSkinnedSubMeshBounds(subMesh, boneMatrices, hasBones, subMeshBounds[subMesh.index])
                subMeshBoundsValid[subMesh.index] = true
            }
            return subMeshBounds[subMesh.index]
        }
    }

    companion object
    {
        const val MAX_BONE_INFLUENCES = 4
        private val IDENTITY_MATRIX = Matrix4f()
    }
}