package no.njoh.pulseengine.core.asset.types

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
            val aiMaterial = AIMaterial.create(materialPointers[i])
            val basePath = this.filePath.substringBeforeLast("/") + "/"
            
            val albedoPath = 
                getTexturePath(aiMaterial, aiTextureType_DIFFUSE) 
                ?: getTexturePath(aiMaterial, aiTextureType_BASE_COLOR)

            val normalPath = getTexturePath(aiMaterial, aiTextureType_NORMALS)

            val metalRoughPath =
                getTexturePath(aiMaterial, aiTextureType_METALNESS)
                ?: getTexturePath(aiMaterial, aiTextureType_DIFFUSE_ROUGHNESS)
                ?: getTexturePath(aiMaterial, aiTextureType_UNKNOWN)

            val specularPath = getTexturePath(aiMaterial, aiTextureType_SHININESS)

            val emissivePath = getTexturePath(aiMaterial, aiTextureType_EMISSIVE)

            this.materials += MeshMaterial(
                name = this.name + "_" + getMaterialName(aiMaterial),
                albedoPath = albedoPath?.let { basePath + it.replace("%20", " ") },
                normalPath = normalPath?.let { basePath + it.replace("%20", " ") },
                aoMetalRoughPath = metalRoughPath?.let { basePath + it.replace("%20", " ") },
                specularPath = specularPath?.let { basePath + it.replace("%20", " ") },
                emissivePath = emissivePath?.let { basePath + it.replace("%20", " ") }
            )
        }
    }

    private fun getMaterialName(mat: AIMaterial): String = AIString.calloc().use()
    {
        aiGetMaterialString(mat, AI_MATKEY_NAME, aiTextureType_NONE, 0, it)
        it.dataString()
    }

    private fun getTexturePath(mat: AIMaterial, type: Int): String? = AIString.calloc().use()
    {
        if (aiGetMaterialTextureCount(mat, type) < 1)
            return null

        if (aiGetMaterialTexture(mat, type, 0, it, null as IntArray?, null, null, null, null, null) != aiReturn_SUCCESS)
            return null

        return it.dataString()
    }

    override fun getSubAssets(): List<Asset> 
    {
        val assets = mutableListOf<Asset>()
        for (mat in materials)
        {
            val albedo   = mat.albedoPath?.let {       Texture(it, mat.name + "_albedo",       format = SRGBA8) }
            val normal   = mat.normalPath?.let {       Texture(it, mat.name + "_normal",       format = RGBA8)  }
            val aomr     = mat.aoMetalRoughPath?.let { Texture(it, mat.name + "_aoMetalRough", format = RGBA8)  }
            val specular = mat.specularPath?.let {     Texture(it, mat.name + "_specular",     format = RGBA8)  }
            val emissive = mat.emissivePath?.let {     Texture(it, mat.name + "_emissive",     format = RGBA8)  }

            albedo?.let { assets += it }
            normal?.let { assets += it }
            aomr?.let { assets += it }
            specular?.let { assets += it }
            emissive?.let { assets += it }
            assets += Material(mat.name, albedo, normal, aomr, specular, emissive)
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
        val specularPath: String?,
        val emissivePath: String?,
    )
}