package no.njoh.pulseengine.core.asset.types

import no.njoh.pulseengine.core.asset.types.Material.BlendMode.*
import no.njoh.pulseengine.core.asset.types.Material.CullMode.BACK
import no.njoh.pulseengine.core.asset.types.Material.CullMode.NONE
import no.njoh.pulseengine.core.graphics.api.TextureFormat.*
import no.njoh.pulseengine.core.graphics.api.objects.StaticBufferObject
import no.njoh.pulseengine.core.graphics.api.objects.VertexArrayObject
import no.njoh.pulseengine.core.shared.utils.Logger
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp.*

class Mesh(filePath: String, name: String) : Asset(filePath, name) 
{
    var vao: VertexArrayObject?  = null; private set
    var vbo: StaticBufferObject? = null; private set
    var ebo: StaticBufferObject? = null; private set

    var vertices = FloatArray(0); private set
    var indices  = IntArray(0);   private set

    var subMeshes: List<SubMesh>      = emptyList(); private set
    var materials: List<MeshMaterial> = emptyList(); private set

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
            aiProcess_SortByPType or
            aiProcess_PreTransformVertices

        val scene = aiImportFile(filePath, flags) ?: throw RuntimeException(aiGetErrorString())

        try
        {
            readMeshes(scene)
            readMaterial(scene)
        }
        catch (e: Exception)
        {
            aiReleaseImport(scene)
            throw e
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

            // Vertices
            for (i in 0 until numVertices)
            {
                val v = vertices[i]
                vertexData[dst++] = v.x()
                vertexData[dst++] = v.y()
                vertexData[dst++] = v.z()

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
                materialIndex = materialIdx
            )

            globalVertexOffset += numVertices
        }

        this.vertices     = vertexData
        this.indices      = indices
        this.hasNormals   = hasNormals
        this.hasTangents  = hasTangents
        this.hasTexCoords = hasTexCoords
    }
    
    private fun readMaterial(scene: AIScene)
    {
        val numMaterials = scene.mNumMaterials()
        val materialPointers = scene.mMaterials() ?: return

        for (i in 0 until numMaterials)
        {
            val material = AIMaterial.create(materialPointers[i])
            val materialName = material.getMaterialStringProp(AI_MATKEY_NAME) ?: "material_${materials.size}"
            val basePath = this.filePath.substringBeforeLast("/") + "/"
            
            val albedoPath = material.getTexturePath(aiTextureType_DIFFUSE) ?: material.getTexturePath(aiTextureType_BASE_COLOR)

            val normalPath = material.getTexturePath(aiTextureType_NORMALS)

            val metalRoughPath =
                material.getTexturePath(aiTextureType_METALNESS)
                ?: material.getTexturePath(aiTextureType_DIFFUSE_ROUGHNESS)
                ?: material.getTexturePath(aiTextureType_UNKNOWN)

            val emissivePath = material.getTexturePath(aiTextureType_EMISSIVE)

            val cullMode = material.getMaterialIntProp(AI_MATKEY_TWOSIDED).let()
            {
                if (it == 1) "NONE" else "BACK"
            }

            val alphaMode = material.getMaterialStringProp(AI_MATKEY_GLTF_ALPHAMODE) ?: "OPAQUE"
            
            val alphaCutoff = material.getMaterialFloatProp(AI_MATKEY_GLTF_ALPHACUTOFF) ?: 0.5f

            this.materials += MeshMaterial(
                name = this.name + "_" + materialName,
                albedoPath = albedoPath?.let { basePath + it.replace("%20", " ") },
                normalPath = normalPath?.let { basePath + it.replace("%20", " ") },
                aoMetalRoughPath = metalRoughPath?.let { basePath + it.replace("%20", " ") },
                emissivePath = emissivePath?.let { basePath + it.replace("%20", " ") },
                cullMode = cullMode,
                alphaMode = alphaMode,
                alphaCutoff = alphaCutoff
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

    private fun AIMaterial.getTexturePath(type: Int): String? = AIString.calloc().use()
    {
        if (aiGetMaterialTextureCount(this, type) < 1)
            return null

        if (aiGetMaterialTexture(this, type, 0, it, null as IntArray?, null, null, null, null, null) != aiReturn_SUCCESS)
            return null

        return it.dataString()
    }

    override fun getSubAssets(): List<Asset> 
    {
        val assets = mutableListOf<Asset>()
        for (mat in materials)
        {
            val albedo    = mat.albedoPath?.let {       Texture(it, mat.name + "_albedo",       format = SRGBA8) }
            val normal    = mat.normalPath?.let {       Texture(it, mat.name + "_normal",       format = RGBA8)  }
            val aomr      = mat.aoMetalRoughPath?.let { Texture(it, mat.name + "_aoMetalRough", format = RGBA8)  }
            val emissive  = mat.emissivePath?.let {     Texture(it, mat.name + "_emissive",     format = RGBA8)  }
            val cullMode  = when (mat.cullMode.uppercase())
            {
                "NONE" -> NONE
                else   -> BACK
            }
            val blendMode = when (mat.alphaMode.uppercase())
            {
                "BLEND" -> TRANSPARENT
                "MASK"  -> MASK
                else    -> OPAQUE
            }

            albedo?.let { assets += it }
            normal?.let { assets += it }
            aomr?.let { assets += it }
            emissive?.let { assets += it }
            assets += Material(mat.name, albedo, normal, aomr, emissive, null, cullMode, blendMode, mat.alphaCutoff)
        }
        return assets  
    }

    data class SubMesh(
        val indexStart: Int,
        val indexCount: Int,
        val materialIndex: Int
    )

    data class MeshMaterial(
        val name: String,
        val albedoPath: String?,
        val normalPath: String?,
        val aoMetalRoughPath: String?,
        val emissivePath: String?,
        val cullMode: String,
        val alphaMode: String,
        val alphaCutoff: Float
    )
}