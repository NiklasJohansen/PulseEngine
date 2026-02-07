package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.asset.types.Material.*
import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Material.CullMode.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFormat
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.shared.utils.Logger
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.assimp.AIColor4D
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.AITexture
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
    
    private val embededTextures = mutableMapOf<Int, EmbeddedTexture>()

    var hasNormals   = false; private set
    var hasTangents  = false; private set
    var hasTexCoords = false; private set

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
        this.vertices = FloatArray(0) // No longer needed
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
            readMaterial(scene)

            buildSubMeshInstances(scene.mRootNode()!!, Matrix4f(), mutableListOf<SubMeshInstance>().also { subMeshInstances = it })
        }
        finally { aiReleaseImport(scene) }
    }

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
        }

        val stride = 3 +
            (if (hasNormals) 3 else 0) +
            (if (hasTangents) 3 + 3 else 0) +
            (if (hasTexCoords) 2 else 0)

        val subMeshes  = mutableListOf<SubMesh>()
        val vertexData = FloatArray(totalVertices * stride)
        val indices    = IntArray(totalIndices)

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
                indexStart = subMeshIndexStart,
                indexCount = numFaces * 3,
                materialIndex = materialIdx,
                localBounds = Aabb(xMin, yMin, zMin, xMax, yMax, zMax)
            )

            globalVertexOffset += numVertices
        }

        this.subMeshes    = subMeshes
        this.vertices     = vertexData
        this.indices      = indices
        this.hasNormals   = hasNormals
        this.hasTangents  = hasTangents
        this.hasTexCoords = hasTexCoords
    }

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
                val localBounds = subMesh.localBounds
                val worldBounds = transformAabb(localBounds, world)

                instances += SubMeshInstance(
                    subMesh = subMesh,
                    transform = Matrix4f(world),
                    worldBounds = worldBounds,
                    nodeName = nodeName
                )
            }
        }

        val children = node.mChildren() ?: return
        for (i in 0 until node.mNumChildren())
            buildSubMeshInstances(AINode.create(children[i]), world, instances)
    }

    private fun transformAabb(aabb: Aabb, transform: Matrix4f): Aabb 
    {
        val corners = floatArrayOf(
            aabb.xMin, aabb.yMin, aabb.zMin,
            aabb.xMax, aabb.yMin, aabb.zMin,
            aabb.xMin, aabb.yMax, aabb.zMin,
            aabb.xMax, aabb.yMax, aabb.zMin,
            aabb.xMin, aabb.yMin, aabb.zMax,
            aabb.xMax, aabb.yMin, aabb.zMax,
            aabb.xMin, aabb.yMax, aabb.zMax,
            aabb.xMax, aabb.yMax, aabb.zMax
        )

        val p = Vector3f()
        var xMin = Float.POSITIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var zMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        var zMax = Float.NEGATIVE_INFINITY
        
        for (i in corners.indices step 3)
        {
            p.set(corners[i], corners[i+1], corners[i+2]).mulPosition(transform)
 
            if (p.x < xMin) xMin = p.x
            if (p.y < yMin) yMin = p.y
            if (p.z < zMin) zMin = p.z
            if (p.x > xMax) xMax = p.x
            if (p.y > yMax) yMax = p.y
            if (p.z > zMax) zMax = p.z
        }

        return Aabb(xMin, yMin, zMin, xMax, yMax, zMax)
    }

    private fun AIMatrix4x4.toMatrix4f(): Matrix4f =
        Matrix4f(
            a1(), b1(), c1(), d1(),
            a2(), b2(), c2(), d2(),
            a3(), b3(), c3(), d3(),
            a4(), b4(), c4(), d4()
        )
    
    private fun readMaterial(scene: AIScene)
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

                embededTextures[i] = EmbeddedTexture(w[0], h[0], pixels, freeWithStbi = true)
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

                embededTextures[i] = EmbeddedTexture(w, h, rgba, freeWithStbi = false)
            }
        }
    }

    override fun getSubAssets(): List<Asset> 
    {
        val assets = mutableListOf<Asset>()
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
            val tex = embededTextures[idx] ?: return null
            texture.loadFrom(tex.rgbaPixels, tex.width, tex.height, freeWithStbi = tex.freeWithStbi)
        }

        return texture
    }
    
    private data class EmbeddedTexture(val width: Int, val height: Int, val rgbaPixels: ByteBuffer, val freeWithStbi: Boolean)

    data class SubMesh(
        val indexStart: Int,
        val indexCount: Int,
        val materialIndex: Int,
        val localBounds: Aabb
    )

    data class SubMeshInstance(
        val subMesh: SubMesh,
        val transform: Matrix4f,
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

    data class Aabb(
        val xMin: Float,
        val yMin: Float,
        val zMin: Float,
        val xMax: Float,
        val yMax: Float,
        val zMax: Float
    )
}