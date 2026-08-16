package no.njoh.pulseengine.core.asset.types

import gnu.trove.map.hash.THashMap
import gnu.trove.map.hash.TIntObjectHashMap
import gnu.trove.map.hash.TObjectIntHashMap
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Animation.QuaternionKey
import no.njoh.pulseengine.core.asset.types.Animation.VectorKey
import no.njoh.pulseengine.core.asset.types.Material.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Material.CullMode.NONE
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureFormat.*
import no.njoh.pulseengine.core.graphics.gpu.buffer.StaticBufferObject
import no.njoh.pulseengine.core.graphics.gpu.buffer.VertexArrayObject
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.primitives.Mat4fProps
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.loadBytesFromPath
import no.njoh.pulseengine.core.shared.utils.ResourceResolver
import no.njoh.pulseengine.core.shared.utils.buildSkinningBounds
import no.njoh.pulseengine.core.shared.utils.collectAnimatedGlobalTransforms
import no.njoh.pulseengine.core.shared.utils.collectBlendedAnimatedGlobalTransforms
import no.njoh.pulseengine.core.shared.utils.getSkinnedMeshBounds
import no.njoh.pulseengine.core.shared.utils.Logger
import no.njoh.pulseengine.core.shared.utils.ModelVertexCompressor
import no.njoh.pulseengine.core.shared.utils.emptyObjectIntHashMap
import no.njoh.pulseengine.core.shared.utils.transformAabb
import no.njoh.pulseengine.core.shared.utils.getOrPut
import no.njoh.pulseengine.core.shared.utils.set
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIFile
import org.lwjgl.assimp.AIFileCloseProc
import org.lwjgl.assimp.AIFileFlushProc
import org.lwjgl.assimp.AIFileIO
import org.lwjgl.assimp.AIFileOpenProc
import org.lwjgl.assimp.AIFileReadProc
import org.lwjgl.assimp.AIFileSeek
import org.lwjgl.assimp.AIFileTellProc
import org.lwjgl.assimp.AIFileWriteProc
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
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.file.Paths
import kotlin.math.ceil
import kotlin.math.min

class Model(
    filePath: String,
    name: String,
    val maxTrianglesPerMesh: Int = 0
) : Asset(filePath, name) {

    var vao: VertexArrayObject?  = null; private set
    var vbo: StaticBufferObject? = null; private set
    var ebo: StaticBufferObject? = null; private set

    var vertices    = FloatArray(0); private set
    var indices     = IntArray(0);   private set
    var vertexBytes = ByteArray(0);  private set

    var meshes          = emptyList<Mesh>();          private set
    var meshInstances   = emptyList<MeshInstance>();  private set
    var collisionMeshes = emptyList<CollisionMesh>(); private set
    var lodLevels       = emptyList<LodLevel>();      private set
    var materials       = emptyList<MeshMaterial>();  private set
    var bones           = emptyList<Bone>();          private set
    var localBounds     = null as Aabb?;              private set

    var hasNormals   = false; private set
    var hasTangents  = false; private set
    var hasTexCoords = false; private set
    var hasBones     = false; private set

    private var animations                     = ArrayList<Animation>()
    private val embeddedTextures               = TIntObjectHashMap<EmbeddedTexture>()
    private val embeddedTexturesByPath         = THashMap<String, EmbeddedTexture>()
    private val globalNodeTransforms           = THashMap<String, Matrix4f>()
    private val nodesByName                    = THashMap<String, ModelNode>()
    private var nodeHierarchy                  = null as ModelNode?
    private val bindPoseBoneMatricesByNodeName = THashMap<String, Array<Matrix4f>>()
    private val skeletonPoseCaches             = Array(POSE_CACHE_FRAME_SLOT_COUNT) { ArrayList<AnimatedSkeletonPose>(4) }
    private val activeSkeletonPoseCacheCounts  = IntArray(POSE_CACHE_FRAME_SLOT_COUNT)
    private val poseCacheFrameNumbers          = LongArray(POSE_CACHE_FRAME_SLOT_COUNT) { Long.MIN_VALUE }

    override fun load()
    {
        try { AssimpAssetFileIO(filePath).use { loadWithAssimp(it) } }
        catch (e: Exception) { Logger.error { "Failed to load Mesh $filePath: ${e.message}" } }
    }
    
    override fun unload()
    {
        // ModelBank owns the GPU allocation and deletes it after queued draws have completed.
        this.vertices = FloatArray(0)
        this.vertexBytes = ByteArray(0)
        this.indices = IntArray(0)
        this.collisionMeshes = emptyList()
    }

    fun onUploaded(vao: VertexArrayObject, vbo: StaticBufferObject, ebo: StaticBufferObject)
    {
        this.vbo = vbo
        this.ebo = ebo
        this.vao = vao
        this.vertices = FloatArray(0) // CPU-side culling data is baked at import time
        this.indices = IntArray(0)
        this.vertexBytes = ByteArray(0)
        this.meshes.forEachFast { it.vao = vao }
    }

    fun onDeleted()
    {
        this.vao = null
        this.vbo = null
        this.ebo = null
    }

    private fun loadWithAssimp(assetFileIO: AssimpAssetFileIO)
    {
        Logger.debug { "Loading model $name..." }

        require(maxTrianglesPerMesh >= 0) { "maxTrianglesPerMesh must be zero (disabled) or positive" }

        var flags =
            aiProcess_Triangulate or
            aiProcess_JoinIdenticalVertices or
            aiProcess_CalcTangentSpace or
            aiProcess_GenNormals or
            aiProcess_ImproveCacheLocality or
            aiProcess_OptimizeMeshes or
            aiProcess_SortByPType

        val splitLargeMeshes = maxTrianglesPerMesh > 0
        if (splitLargeMeshes) 
            flags = flags or aiProcess_SplitLargeMeshes
        
        val properties = if (splitLargeMeshes) checkNotNull(aiCreatePropertyStore()) { "Failed to create Assimp property store" } else null

        try
        {
            val scene = if (properties != null)
            {
                aiSetImportPropertyInteger(properties, AI_CONFIG_PP_SLM_TRIANGLE_LIMIT, maxTrianglesPerMesh)
                aiImportFileExWithProperties(filePath, flags, assetFileIO.fileIO, properties)
            }
            else
            {
                aiImportFileEx(filePath, flags, assetFileIO.fileIO)
            }

            if (scene == null)
                throw RuntimeException(listOfNotNull(aiGetErrorString()?.takeIf { it.isNotBlank() }, assetFileIO.lastFailure).joinToString(". "))

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

                    val instances = mutableListOf<MeshInstance>()
                    val collisionMeshes = mutableListOf<CollisionMesh>()
                    buildSubMeshInstances(it, Matrix4f(), instances, collisionMeshes)
                    this.meshInstances = instances
                    this.collisionMeshes = collisionMeshes

                    buildBoundsAndLodLevels()
                    buildConservativeAnimatedBounds()
                }
            }
            finally { aiReleaseImport(scene) }
        }
        finally { if (properties != null) aiReleasePropertyStore(properties) }
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
            (if (hasTangents) 4 else 0) +
            (if (hasTexCoords) 2 else 0) +
            (if (hasBones) MAX_BONE_INFLUENCES + MAX_BONE_INFLUENCES else 0)

        val meshes          = mutableListOf<Mesh>()
        val bones           = mutableListOf<Bone>()
        val boneIndexByName = emptyObjectIntHashMap<String>()
        val vertexData      = FloatArray(totalVertices * stride)
        val indices         = IntArray(totalIndices)

        // TODO: need to create flat primitive array not array of objects
        val vertexInfluences = Array(totalVertices) { VertexInfluence() }

        var dst = 0                 // write cursor in vertexData
        var globalVertexOffset = 0  // index offset per mesh
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
                    if (tangents != null)
                    {
                        val t = tangents[i]
                        val sign = if (normals != null && bitangents != null)
                        {
                            val n = normals[i]
                            val b = bitangents[i]
                            val cx = n.y() * t.z() - n.z() * t.y()
                            val cy = n.z() * t.x() - n.x() * t.z()
                            val cz = n.x() * t.y() - n.y() * t.x()
                            if (cx * b.x() + cy * b.y() + cz * b.z() < 0f) -1f else 1f
                        }
                        else 1f

                        vertexData[dst++] = t.x()
                        vertexData[dst++] = t.y()
                        vertexData[dst++] = t.z()
                        vertexData[dst++] = sign
                    }
                    else
                    {
                        vertexData[dst++] = 0f; vertexData[dst++] = 0f; vertexData[dst++] = 0f; vertexData[dst++] = 1f
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
            val meshIndexStart = indexOffset
            for (i in 0 until numFaces)
            {
                val face   = faces[i]
                val idxBuf = face.mIndices() // 3 indices due to Triangulate
                indices[indexOffset + 0] = globalVertexOffset + idxBuf[0]
                indices[indexOffset + 1] = globalVertexOffset + idxBuf[1]
                indices[indexOffset + 2] = globalVertexOffset + idxBuf[2]
                indexOffset += 3
            }

            meshes += Mesh(
                index = meshes.size,
                indexStart = meshIndexStart,
                indexCount = numFaces * 3,
                vertexStart = globalVertexOffset,
                vertexCount = numVertices,
                materialIndex = materialIdx,
                vertexStride = stride,
                localBounds = Aabb(xMin, yMin, zMin, xMax, yMax, zMax)
            )

            globalVertexOffset += numVertices
        }

        val hasBoneAttributes = bones.isNotEmpty()
        this.vertices     = vertexData
        this.vertexBytes  = ModelVertexCompressor.compress(vertexData, totalVertices, stride, hasNormals, hasTangents, hasTexCoords, hasBoneAttributes)
        this.indices      = indices
        this.hasNormals   = hasNormals
        this.hasTangents  = hasTangents
        this.hasTexCoords = hasTexCoords
        this.hasBones     = hasBoneAttributes
        this.bones        = bones
        this.meshes       = if (this.hasBones)
        {
            meshes.map {
                it.copy(
                    skinningBounds = buildSkinningBounds(
                        mesh = it,
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
        else meshes

        if (maxTrianglesPerMesh > 0) Logger.debug()
        {
            val totalTriangles = this.meshes.sumOf { it.indexCount / 3 }
            val largestMeshTriangles = this.meshes.maxOfOrNull { it.indexCount / 3 } ?: 0
            "Imported model $name with ${this.meshes.size} meshes and $totalTriangles triangles; largest mesh has $largestMeshTriangles triangles (limit=$maxTrianglesPerMesh)"
        }
    }

    private fun readBoneWeights(
        mesh: AIMesh,
        globalVertexOffset: Int,
        vertexInfluences: Array<VertexInfluence>,
        bones: MutableList<Bone>,
        boneIndexByName: TObjectIntHashMap<String>
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

            val baseColor = material.getMaterialColorProp(AI_MATKEY_BASE_COLOR)
                ?: material.getMaterialColorProp(AI_MATKEY_COLOR_DIFFUSE)
                ?: Color(1f, 1f, 1f, 1f)

            val albedoPath = material.getTexturePath(aiTextureType_DIFFUSE)
                ?: material.getTexturePath(aiTextureType_BASE_COLOR)

            val normalPath = material.getTexturePath(aiTextureType_NORMALS)
                ?: material.getTexturePath(aiTextureType_HEIGHT)

            val aoPath = material.getTexturePath(aiTextureType_AMBIENT)
                ?: material.getTexturePath(aiTextureType_AMBIENT_OCCLUSION)
                ?: material.getTexturePath(aiTextureType_LIGHTMAP)

            val metalRoughPath = material.getTexturePath(aiTextureType_METALNESS)
                ?: material.getTexturePath(aiTextureType_DIFFUSE_ROUGHNESS)
                ?: material.getTexturePath(aiTextureType_UNKNOWN)

            val emissivePath = material.getTexturePath(aiTextureType_EMISSIVE)

            val cullMode = if (material.getMaterialIntProp(AI_MATKEY_TWOSIDED) == 1) "NONE" else "BACK"

            val blendMode = when (material.getMaterialStringProp(AI_MATKEY_GLTF_ALPHAMODE)?.uppercase())
            {
                "MASK"  -> MASK
                "BLEND" -> BLEND
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
        embeddedTextures.clear()
        embeddedTexturesByPath.clear()

        val numTex = scene.mNumTextures()
        if (numTex == 0) return

        val texPtrs = scene.mTextures() ?: return

        for (i in 0 until numTex)
        {
            val tex = AITexture.create(texPtrs[i])
            val embeddedTexture = if (tex.mHeight() == 0)
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

                EmbeddedTexture(w[0], h[0], pixels, freeWithStbi = true)
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

                EmbeddedTexture(w, h, rgba, freeWithStbi = false)
            }

            embeddedTextures[i] = embeddedTexture

            val texturePath = normalizeTextureReference(tex.mFilename().dataString())
            if (texturePath.isNotBlank())
                embeddedTexturesByPath[embeddedTexturePathKey(texturePath)] = embeddedTexture
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
        val inverseMeshNodeTransform = meshNodeGlobalTransform?.let { Matrix4f(it).invert() } ?: Matrix4f()

        val palette = Array(bones.size)
        {
            val bone = bones[it]
            val boneNodeTransform = globalNodeTransforms[bone.nodeName] ?: globalNodeTransforms[bone.name]

            if (boneNodeTransform == null)
            {
                Logger.warn { "Bone node '${bone.nodeName}' was not found in hierarchy for $filePath" }
                Matrix4f()
            }
            else Matrix4f(inverseMeshNodeTransform).mul(boneNodeTransform).mul(bone.offsetMatrix)
        }

        bindPoseBoneMatricesByNodeName[cacheKey] = palette
        return palette
    }

    /**
     * Returns a frame-local animated model pose from a full-body crossfade between two clips.
     */
    fun getAnimationPose(
        animation: Animation?,
        animationTimeSeconds: Float,
        frameNumber: Long,
        blendAnimation: Animation? = null,
        blendAnimationTimeSeconds: Float = 0f,
        blendFactor: Float = 0f,
    ): AnimatedSkeletonPose? {
        if (bones.isEmpty())
            return null

        val primaryAnimation = animation?.takeIf { it.modelName == name }
        val secondaryAnimation = blendAnimation?.takeIf { it.modelName == name && it !== primaryAnimation }
        val clampedBlendFactor = blendFactor.coerceIn(0f, 1f)

        val sampledPrimaryAnimation: Animation
        var sampledPrimaryTimeSeconds = 0f
        val sampledSecondaryAnimation: Animation?
        var sampledSecondaryTimeSeconds = 0f
        var sampledBlendFactor = 0f

        when
        {
            primaryAnimation != null && (secondaryAnimation == null || clampedBlendFactor <= 0f) ->
            {
                sampledPrimaryAnimation = primaryAnimation
                sampledPrimaryTimeSeconds = animationTimeSeconds
                sampledSecondaryAnimation = null
                sampledSecondaryTimeSeconds = 0f
                sampledBlendFactor = 0f
            }
            primaryAnimation == null && secondaryAnimation != null && clampedBlendFactor > 0f ->
            {
                sampledPrimaryAnimation = secondaryAnimation
                sampledPrimaryTimeSeconds = blendAnimationTimeSeconds
                sampledSecondaryAnimation = null
                sampledSecondaryTimeSeconds = 0f
                sampledBlendFactor = 0f
            }
            primaryAnimation != null && secondaryAnimation != null && clampedBlendFactor >= 1f ->
            {
                sampledPrimaryAnimation = secondaryAnimation
                sampledPrimaryTimeSeconds = blendAnimationTimeSeconds
                sampledSecondaryAnimation = null
                sampledSecondaryTimeSeconds = 0f
                sampledBlendFactor = 0f
            }
            primaryAnimation != null && secondaryAnimation != null ->
            {
                sampledPrimaryAnimation = primaryAnimation
                sampledPrimaryTimeSeconds = animationTimeSeconds
                sampledSecondaryAnimation = secondaryAnimation
                sampledSecondaryTimeSeconds = blendAnimationTimeSeconds
                sampledBlendFactor = clampedBlendFactor
            }
            else -> return null
        }

        val rootNode = nodeHierarchy ?: return null
        val poseCacheSlot = getPoseCacheSlot(frameNumber)

        if (poseCacheFrameNumbers[poseCacheSlot] != frameNumber)
        {
            poseCacheFrameNumbers[poseCacheSlot] = frameNumber
            activeSkeletonPoseCacheCounts[poseCacheSlot] = 0
        }

        return getAnimatedSkeletonPose(
            animation = sampledPrimaryAnimation,
            animationTimeSeconds = sampledPrimaryTimeSeconds,
            blendAnimation = sampledSecondaryAnimation,
            blendAnimationTimeSeconds = sampledSecondaryTimeSeconds,
            blendFactor = sampledBlendFactor,
            poseCacheSlot = poseCacheSlot,
            rootNode = rootNode
        )
    }
    
    // Helpers ////////////////////////////////////////////////////////////////////////

    private fun buildSubMeshInstances(
        node: AINode,
        parentWorld: Matrix4f,
        instances: MutableList<MeshInstance>,
        collisionMeshes: MutableList<CollisionMesh>,
        inheritedLodLevel: Int? = null,
        inheritedColliderName: String? = null
    ) {
        val local = node.mTransformation().toMatrix4f()
        val world = Matrix4f(parentWorld).mul(local)

        val nodeName = node.mName().dataString()
        val lodLevel = nodeName.extractLodLevel() ?: inheritedLodLevel
        val colliderName = nodeName.takeIf { it.isColliderNodeName() } ?: inheritedColliderName
        val meshIndices = node.mMeshes()

        if (meshIndices != null)
        {
            for (i in 0 until node.mNumMeshes())
            {
                val meshIndex = meshIndices[i] // aiMesh index
                val mesh      = meshes[meshIndex]

                if (colliderName != null)
                {
                    collisionMeshes += extractCollisionMesh(colliderName, mesh, world)
                }
                else
                {
                    val localBounds = if (hasBones) getBindPoseSubMeshBounds(mesh, nodeName) else mesh.localBounds
                    val worldBounds = transformAabb(localBounds, world)

                    instances += MeshInstance(
                        mesh = mesh,
                        transform = Matrix4f(world),
                        cullingBounds = localBounds,
                        worldBounds = worldBounds,
                        nodeName = nodeName,
                        lodLevel = lodLevel,
                        materialHandle = AssetHandle(materials.getOrNull(mesh.materialIndex)?.name ?: "")
                    )
                }
            }
        }

        val children = node.mChildren() ?: return
        for (i in 0 until node.mNumChildren())
            buildSubMeshInstances(AINode.create(children[i]), world, instances, collisionMeshes, lodLevel, colliderName)
    }

    private fun extractCollisionMesh(name: String, mesh: Mesh, transform: Matrix4fc): CollisionMesh
    {
        val positions = FloatArray(mesh.vertexCount * 3)
        var destination = 0
        for (vertexIndex in mesh.vertexStart until mesh.vertexStart + mesh.vertexCount)
        {
            val source = vertexIndex * mesh.vertexStride
            positions[destination++] = vertices[source]
            positions[destination++] = vertices[source + 1]
            positions[destination++] = vertices[source + 2]
        }

        val localIndices = IntArray(mesh.indexCount)
        for (index in localIndices.indices)
        {
            val localIndex = indices[mesh.indexStart + index] - mesh.vertexStart
            check(localIndex in 0 until mesh.vertexCount) { "Collision mesh '$name' contains an index outside its vertex range" }
            localIndices[index] = localIndex
        }

        return CollisionMesh(name, positions, localIndices, Matrix4f(transform))
    }

    private fun buildBoundsAndLodLevels()
    {
        localBounds = meshInstances.firstOrNull()?.worldBounds?.let { Aabb().set(it) }
        for (i in 1 until meshInstances.size)
            localBounds?.include(meshInstances[i].worldBounds)

        val lodInstances = meshInstances.filter { it.lodLevel != null }

        lodLevels = if (lodInstances.isNotEmpty())
        {
            val sharedInstances = meshInstances.filter { it.lodLevel == null }
            lodInstances
                .mapNotNull { it.lodLevel }
                .distinct()
                .sorted()
                .map { level ->
                    val instances = ArrayList<MeshInstance>(sharedInstances.size + lodInstances.size)
                    instances += sharedInstances
                    lodInstances.forEachFast { if (it.lodLevel == level) instances += it }
                    LodLevel(level, instances)
                }
        }
        else emptyList()
    }

    fun getMeshInstancesAtLevel(lodLevel: Int): List<MeshInstance> = 
        lodLevels.firstOrNull { it.level == lodLevel }?.meshInstances ?: meshInstances

    private fun String.extractLodLevel(): Int?
    {
        val match = LOD_NAME_REGEX.find(this) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun String.isColliderNodeName(): Boolean =
        startsWith("COL_", ignoreCase = true) || 
        startsWith("COLLIDER_", ignoreCase = true) || 
        startsWith("UCX_", ignoreCase = true)

    private fun getBindPoseSubMeshBounds(mesh: Mesh, nodeName: String): Aabb
    {
        val aabb = Aabb()
        val boneMatrices = getBindPoseBoneMatrices(nodeName) ?: emptyArray()
        getSkinnedMeshBounds(mesh, boneMatrices, hasBones, aabb)
        return aabb
    }

    private fun buildConservativeAnimatedBounds()
    {
        if (!hasBones || animations.isEmpty() || meshInstances.isEmpty())
            return

        val boundsByMesh = arrayOfNulls<Aabb>(meshes.size)

        for (instance in meshInstances)
            boundsByMesh.include(instance.mesh.index, instance.cullingBounds)

        var frameNumber = 0L
        for (animation in animations)
        {
            val sampleCount = getAnimationBoundsSampleCount(animation)
            val durationSeconds = animation.durationSeconds.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() } ?: 0.0

            for (sampleIndex in 0 until sampleCount)
            {
                val sampleTimeSeconds = if (sampleCount == 1) 0f else (durationSeconds * sampleIndex / sampleCount).toFloat()
                frameNumber++

                for (instance in meshInstances)
                {
                    val skeletonPose = getAnimationPose(animation, sampleTimeSeconds, frameNumber) ?: continue
                    val meshPose = skeletonPose.getAnimatedMeshPose(instance.nodeName)
                    boundsByMesh.include(instance.mesh.index, meshPose.getBounds(instance.mesh))
                }
            }
        }

        meshes.forEachFast { mesh -> mesh.animatedBounds = boundsByMesh[mesh.index] }
    }

    private fun getAnimationBoundsSampleCount(animation: Animation): Int
    {
        val durationSeconds = animation.durationSeconds
        if (durationSeconds <= 0.0 || durationSeconds.isNaN() || durationSeconds.isInfinite())
            return 1

        return ceil(durationSeconds * ANIMATION_BOUNDS_SAMPLE_RATE).toInt().coerceIn(1, MAX_ANIMATION_BOUNDS_SAMPLES_PER_CLIP)
    }
    
    private fun getAnimatedSkeletonPose(
        animation: Animation,
        animationTimeSeconds: Float,
        blendAnimation: Animation?,
        blendAnimationTimeSeconds: Float,
        blendFactor: Float,
        poseCacheSlot: Int,
        rootNode: ModelNode
    ): AnimatedSkeletonPose {

        val animationTimeKey = animationTimeSeconds.toBits()
        val blendAnimationIndex = blendAnimation?.index ?: -1
        val blendAnimationTimeKey = if (blendAnimation != null) blendAnimationTimeSeconds.toBits() else 0
        val blendFactorKey = if (blendAnimation != null) blendFactor.toBits() else 0
        val skeletonPoseCache = skeletonPoseCaches[poseCacheSlot]
        val activePoseCount = activeSkeletonPoseCacheCounts[poseCacheSlot]

        for (i in 0 until activePoseCount)
        {
            val pose = skeletonPoseCache[i]
            if (pose.matches(animation.index, animationTimeKey, blendAnimationIndex, blendAnimationTimeKey, blendFactorKey))
                return pose
        }

        val poseIndex = activePoseCount
        activeSkeletonPoseCacheCounts[poseCacheSlot] = poseIndex + 1

        val pose = skeletonPoseCache.getOrElse(poseIndex) { AnimatedSkeletonPose().also { skeletonPoseCache += it } }
        pose.animationIndex = animation.index
        pose.animationTimeKey = animationTimeKey
        pose.blendAnimationIndex = blendAnimationIndex
        pose.blendAnimationTimeKey = blendAnimationTimeKey
        pose.blendFactorKey = blendFactorKey
        pose.activeMeshPoseCacheCount = 0

        if (blendAnimation != null)
        {
            collectBlendedAnimatedGlobalTransforms(
                node = rootNode,
                parentTransform = IDENTITY_MATRIX,
                animation = animation,
                animationTimeTicks = animation.wrapTimeSecondsToTicks(animationTimeSeconds.toDouble()),
                blendAnimation = blendAnimation,
                blendAnimationTimeTicks = blendAnimation.wrapTimeSecondsToTicks(blendAnimationTimeSeconds.toDouble()),
                blendFactor = blendFactor,
                localTransformScratch = pose.localTransformScratch,
                translationScratch = pose.translationScratch,
                rotationScratch = pose.rotationScratch,
                scaleScratch = pose.scaleScratch,
                blendTranslationScratch = pose.blendTranslationScratch,
                blendRotationScratch = pose.blendRotationScratch,
                blendScaleScratch = pose.blendScaleScratch,
                outTransforms = pose.animatedGlobalTransforms
            )
        }
        else
        {
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
        }

        return pose
    }

    private fun collectGlobalNodeTransforms(node: ModelNode, parentTransform: Matrix4f, outGlobalNodeTransforms: THashMap<String, Matrix4f>)
    {
        val globalTransform = Matrix4f(parentTransform).mul(node.localTransform)
        outGlobalNodeTransforms[node.name] = globalTransform
        nodesByName[node.name] = node

        for (child in node.children)
            collectGlobalNodeTransforms(child, globalTransform, outGlobalNodeTransforms)
    }
    
    private fun AIMatrix4x4.toMatrix4f() =Matrix4f(
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

    private fun AIMaterial.getTexturePath(type: Int): String? = AIString.calloc().use()
    {
        if (aiGetMaterialTextureCount(this, type) < 1)
            return null

        if (aiGetMaterialTexture(this, type, 0, it, null as IntArray?, null, null, null, null, null) != aiReturn_SUCCESS)
            return null

        val path = normalizeTextureReference(it.dataString())
        if (path.isBlank())
            return null

        if (path.startsWith("*") || embeddedTexturesByPath.containsKey(embeddedTexturePathKey(path)))
            return path

        return ResourceResolver.resolveRelativeReferencePath(filePath, path)
    }

    private fun normalizeTextureReference(path: String): String
    {
        var normalized = path.replace('\\', '/').replace("%20", " ")
        while (normalized.startsWith("./"))
            normalized = normalized.substring(2)
        return normalized
    }

    private fun embeddedTexturePathKey(path: String) = normalizeTextureReference(path).lowercase()

    // Sub assets ////////////////////////////////////////////////////////////////////////
    
    override fun getSubAssets(): List<Asset> 
    {
        val textureAssets = THashMap<TextureAssetKey, Texture>()
        val materialAssets = mutableListOf<Material>()

        for (mat in materials)
        {
            val albedo   = createTexture(mat.albedoPath,       mat.name + "_albedo",       SRGBA8, textureAssets)
            val normal   = createTexture(mat.normalPath,       mat.name + "_normal",       RGBA8,  textureAssets)
            val aomr     = createTexture(mat.aoMetalRoughPath, mat.name + "_aoMetalRough", RGBA8,  textureAssets)
            val emissive = createTexture(mat.emissivePath,     mat.name + "_emissive",     SRGBA8, textureAssets)

            val cullMode = when (mat.cullMode.uppercase())
            {
                "NONE" -> NONE
                else   -> BACK
            }

            materialAssets += Material(
                name = mat.name,
                baseColor = Color(mat.baseColor.asSrgb()),
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
                emissiveFactor = Color(mat.emissiveFactor.asSrgb()),
                occlusionStrength = mat.occlusionStrength,
                normalScale = 1f
            )
        }

        val assets = ArrayList<Asset>(animations.size + textureAssets.size + materialAssets.size)
        assets += animations
        assets += textureAssets.values
        assets += materialAssets

        return assets  
    }

    private fun createTexture(path: String?, assetName: String, format: TextureFormat, textureAssets: THashMap<TextureAssetKey, Texture>): Texture? 
    {
        if (path.isNullOrEmpty()) return null
        val normalizedPath = normalizeTextureReference(path)
        val key = TextureAssetKey(normalizedPath, format)
        return textureAssets.getOrPut(key)
        {
            val texture = Texture(normalizedPath, assetName, format = format)
            val embeddedTexture = if (normalizedPath.startsWith("*"))
            {
                val idx = normalizedPath.substring(1).toIntOrNull() ?: return null
                embeddedTextures[idx]
            }
            else embeddedTexturesByPath[embeddedTexturePathKey(normalizedPath)]

            if (normalizedPath.startsWith("*") && embeddedTexture == null)
                return null

            embeddedTexture?.let { texture.loadFrom(it.rgbaPixels.duplicate(), it.width, it.height, freeWithStbi = it.freeWithStbi) }
            texture
        }
    }

    // Data classes ////////////////////////////////////////////////////////////////////////

    private data class TextureAssetKey(val path: String, val format: TextureFormat)

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

    data class Mesh(
        val index: Int,
        val indexStart: Int,
        val indexCount: Int,
        val vertexStart: Int,
        val vertexCount: Int,
        val materialIndex: Int,
        val vertexStride: Int,
        val localBounds: Aabb,
        val skinningBounds: SkinningBounds? = null,
        var animatedBounds: Aabb? = null,
        var gpuMetadataIndex: Int = -1,
        var vao: VertexArrayObject? = null
    ) {
        var batchSortKeyBase = createBatchSortKeyBase(gpuMetadataIndex)
            private set

        fun assignGpuMetadataIndex(index: Int)
        {
            gpuMetadataIndex = index
            batchSortKeyBase = createBatchSortKeyBase(index)
        }

        fun getBatchSortKey(cullMode: CullMode) = batchSortKeyBase or cullMode.ordinal

        private fun createBatchSortKeyBase(metadataIndex: Int): Int
        {
            if (metadataIndex < 0)
                return INVALID_BATCH_SORT_KEY

            val shaderVariantBit = if (skinningBounds == null) 0 else 1
            return (metadataIndex shl BATCH_STATE_BITS) or (shaderVariantBit shl CULL_MODE_BITS)
        }

        companion object
        {
            private const val CULL_MODE_BITS = 1
            private const val BATCH_STATE_BITS = 2
            private const val INVALID_BATCH_SORT_KEY = -1
        }
    }

    data class MeshInstance(
        val mesh: Mesh,
        val transform: Matrix4f,
        val cullingBounds: Aabb,
        val worldBounds: Aabb,
        val nodeName: String,
        val lodLevel: Int?,
        val materialHandle: AssetHandle<Material>
    ) {
        val transformProperties = Mat4fProps.from(transform)
    }

    data class CollisionMesh(
        val name: String,
        val vertices: FloatArray,
        val indices: IntArray,
        val transform: Matrix4fc
    )

    data class LodLevel(
        val level: Int,
        val meshInstances: List<MeshInstance>
    )
    
    /** Imported material metadata. RGB color factors are linear until public assets are created. */
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

        fun include(other: Aabb): Aabb
        {
            if (other.xMin < xMin) xMin = other.xMin
            if (other.yMin < yMin) yMin = other.yMin
            if (other.zMin < zMin) zMin = other.zMin
            if (other.xMax > xMax) xMax = other.xMax
            if (other.yMax > yMax) yMax = other.yMax
            if (other.zMax > zMax) zMax = other.zMax
            return this
        }
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

    inner class AnimatedSkeletonPose
    {
        val animatedGlobalTransforms = THashMap<String, Matrix4f>(nodesByName.size)
        val localTransformScratch = Matrix4f()
        val translationScratch = Vector3f()
        val rotationScratch = Quaternionf()
        val scaleScratch = Vector3f()
        val blendTranslationScratch = Vector3f()
        val blendRotationScratch = Quaternionf()
        val blendScaleScratch = Vector3f()
        val meshPoseCache = ArrayList<AnimatedMeshPose>(4)
        var activeMeshPoseCacheCount = 0
        var animationIndex = -1
        var animationTimeKey = 0
        var blendAnimationIndex = -1
        var blendAnimationTimeKey = 0
        var blendFactorKey = 0

        fun matches(
            animationIndex: Int,
            animationTimeKey: Int,
            blendAnimationIndex: Int,
            blendAnimationTimeKey: Int,
            blendFactorKey: Int
        ): Boolean =
            this.animationIndex == animationIndex &&
                this.animationTimeKey == animationTimeKey &&
                this.blendAnimationIndex == blendAnimationIndex &&
                this.blendAnimationTimeKey == blendAnimationTimeKey &&
                this.blendFactorKey == blendFactorKey

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
            pose.meshBoundsValid.fill(false)
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
        val meshBounds = Array(meshes.size) { Aabb() }
        val meshBoundsValid = BooleanArray(meshes.size)

        fun getBounds(mesh: Mesh): Aabb
        {
            if (!meshBoundsValid[mesh.index])
            {
                getSkinnedMeshBounds(mesh, boneMatrices, hasBones, meshBounds[mesh.index])
                meshBoundsValid[mesh.index] = true
            }
            return meshBounds[mesh.index]
        }
    }

    companion object
    {
        const val MAX_BONE_INFLUENCES = 4
        private val IDENTITY_MATRIX = Matrix4f()
        private val LOD_NAME_REGEX = Regex("""(?:^|_)LOD(\d+)$""", RegexOption.IGNORE_CASE)
        private const val POSE_CACHE_FRAME_SLOT_COUNT = 2
        private const val ANIMATION_BOUNDS_SAMPLE_RATE = 15.0
        private const val MAX_ANIMATION_BOUNDS_SAMPLES_PER_CLIP = 120
    }

    private fun getPoseCacheSlot(frameNumber: Long) = (frameNumber % POSE_CACHE_FRAME_SLOT_COUNT).toInt()

    private fun Array<Aabb?>.include(index: Int, bounds: Aabb)
    {
        val currentBounds = this[index]
        if (currentBounds == null)
            this[index] = Aabb().set(bounds)
        else
            currentBounds.include(bounds)
    }
}

/**
 * Lets Assimp read a model and its neighboring files through [ResourceResolver].
 * For example, while importing "content/model.gltf", Assimp may request "model.bin". This adapter resolves 
 * the request as "content/model.bin", then reads the file either besides the application JAR or from inside it.
 * One adapter is created for each model import and owns all native callbacks and byte buffers until Assimp is 
 * finished with the scene.
 */
internal class AssimpAssetFileIO(private val modelPath: String) : AutoCloseable 
{
    val fileIO: AIFileIO = AIFileIO.calloc()
    var lastFailure: String? = null
        private set

    private val filesByAddress = HashMap<Long, OpenFile>()
    private val allocatedFiles = ArrayList<OpenFile>()
    private var closed = false

    private val openProc = AIFileOpenProc.create { _, fileNameAddress, _ ->
        callback("open", modelPath, MemoryUtil.NULL)
        {
            val requestedPath = MemoryUtil.memUTF8(fileNameAddress).replace("%20", " ")
            open(requestedPath)
        }
    }

    private val closeProc = AIFileCloseProc.create { _, fileAddress ->
        callback("close", filesByAddress[fileAddress]?.path ?: modelPath, Unit)
        {
            filesByAddress.remove(fileAddress)
            Unit
        }
    }

    init
    {
        fileIO.OpenProc(openProc)
        fileIO.CloseProc(closeProc)
    }

    /**
     * Opens a filename requested by Assimp and returns an "aiFile" pointer.
     * For example, "model.bin" resolves to "content/model.bin" when the main model is "content/model.gltf".
     */
    private fun open(requestedPath: String): Long
    {
        val candidatePaths = resolveCandidatePaths(requestedPath)
        var resolvedPath: String? = null
        var bytes: ByteArray? = null

        for (candidatePath in candidatePaths)
        {
            val candidateBytes = candidatePath.loadBytesFromPath()
            if (candidateBytes != null)
            {
                resolvedPath = candidatePath
                bytes = candidateBytes
                break
            }
        }

        if (resolvedPath == null || bytes == null)
        {
            lastFailure = "Assimp could not resolve '$requestedPath' while importing '$modelPath'"
            return MemoryUtil.NULL
        }

        val data = BufferUtils.createByteBuffer(bytes.size)
        data.put(bytes).flip()

        val file = OpenFile(resolvedPath, data)
        filesByAddress[file.nativeFile.address()] = file
        allocatedFiles += file
        return file.nativeFile.address()
    }

    /**
     * Produces the possible asset paths for an Assimp filename.
     * Assimp may ask for either "model.bin" or the already expanded "content/model.bin". This method handles both 
     * forms without adding the model directory twice.
     */
    private fun resolveCandidatePaths(requestedPath: String): List<String>
    {
        val requested = requestedPath.replace('\\', '/')
        val requestedFile = runCatching { Paths.get(requestedPath) }.getOrNull()
        if (requestedFile?.isAbsolute == true)
            return listOf(requestedPath)

        val normalizedModel = ResourceResolver.normalizeRelativePath(modelPath)
        val normalizedRequested = ResourceResolver.normalizeRelativePath(requested)
        if (normalizedRequested != null && normalizedRequested == normalizedModel)
            return listOf(modelPath)

        val relativeToModel = ResourceResolver.resolveRelativeReferencePath(modelPath, requested)
        val modelDirectory = normalizedModel?.substringBeforeLast('/', "")
        val requestedAlreadyRelativeToModel =
            modelDirectory?.isNotEmpty() == true &&
                normalizedRequested?.startsWith("$modelDirectory/") == true

        return buildList()
        {
            if (requestedAlreadyRelativeToModel) add(requested)
            relativeToModel?.let { add(it) }
            add(requested)
        }.distinct()
    }

    override fun close()
    {
        if (closed) return
        closed = true

        filesByAddress.clear()
        allocatedFiles.forEach { it.close() }
        allocatedFiles.clear()
        fileIO.free()
        openProc.close()
        closeProc.close()
    }

    private inline fun <T> callback(operation: String, path: String, failureValue: T, block: () -> T): T =
        try { block() }
        catch (error: Throwable)
        {
            lastFailure = "Assimp asset $operation failed for '$path': ${error.message}"
            Logger.error(error) { lastFailure!! }
            failureValue
        }

    private inner class OpenFile(val path: String, val data: ByteBuffer) : AutoCloseable 
    {
        var position = 0L
        val nativeFile: AIFile = AIFile.calloc()

        private val readProc = AIFileReadProc.create { _, destination, elementSize, elementCount ->
            callback("read", path, 0L) 
            {
                if (elementSize <= 0L || elementCount <= 0L)
                    return@callback 0L

                val remaining = data.limit().toLong() - position
                val readableElements = min(elementCount, remaining / elementSize)
                val bytesToRead = readableElements * elementSize
                if (bytesToRead > 0L)
                {
                    MemoryUtil.memCopy(MemoryUtil.memAddress(data) + position, destination, bytesToRead)
                    position += bytesToRead
                }
                readableElements
            }
        }

        private val writeProc = AIFileWriteProc.create { _, _, _, _ -> 0L }

        private val tellProc = AIFileTellProc.create { _ -> callback("tell", path, 0L) { position } }

        private val sizeProc = AIFileTellProc.create { _ -> callback("size", path, 0L) { data.limit().toLong() } }

        private val seekProc = AIFileSeek.create { _, offset, origin ->
            callback("seek", path, aiReturn_FAILURE) 
            {
                val base = when (origin)
                {
                    aiOrigin_SET -> 0L
                    aiOrigin_CUR -> position
                    aiOrigin_END -> data.limit().toLong()
                    else -> return@callback aiReturn_FAILURE
                }
                val target = base + offset
                if (target < 0L || target > data.limit().toLong())
                    aiReturn_FAILURE
                else
                {
                    position = target
                    aiReturn_SUCCESS
                }
            }
        }

        private val flushProc = AIFileFlushProc.create { }

        init
        {
            nativeFile.ReadProc(readProc)
            nativeFile.WriteProc(writeProc)
            nativeFile.TellProc(tellProc)
            nativeFile.FileSizeProc(sizeProc)
            nativeFile.SeekProc(seekProc)
            nativeFile.FlushProc(flushProc)
        }

        override fun close()
        {
            nativeFile.free()
            readProc.close()
            writeProc.close()
            tellProc.close()
            sizeProc.close()
            seekProc.close()
            flushProc.close()
        }
    }
}
