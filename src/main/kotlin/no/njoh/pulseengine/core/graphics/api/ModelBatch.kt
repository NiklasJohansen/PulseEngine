package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model
import no.njoh.pulseengine.core.graphics.api.ModelShaderVariant.*
import org.lwjgl.opengl.GL11.GL_BACK
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.glCullFace
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable

class ModelBatch
{
    lateinit var model: Model
    lateinit var subMesh: Model.SubMesh
    lateinit var cullMode: CullMode
    lateinit var shaderVariant: ModelShaderVariant

    var instanceIndex = 0
    var instanceCount = 0

    fun set(model: Model, subMesh: Model.SubMesh, shaderVariant: ModelShaderVariant, cullMode: CullMode, instanceIndex: Int, instanceCount: Int)
    {
        this.model = model
        this.subMesh = subMesh
        this.shaderVariant = shaderVariant
        this.cullMode = cullMode
        this.instanceIndex = instanceIndex
        this.instanceCount = instanceCount
    }

    fun matches(model: Model, subMesh: Model.SubMesh, shaderVariant: ModelShaderVariant, cullMode: CullMode): Boolean =
        this.model === model &&
        this.subMesh === subMesh &&
        this.shaderVariant == shaderVariant &&
        this.cullMode == cullMode
    
    fun bindProgramAndSetCullMode(programs: ModelProgramSet)
    {
        val program = programs[shaderVariant]
        if (program.id != currentProgramId)
        {
            program.bind()
            currentProgramId = program.id
        }

        if (cullMode != currentCullMode)
        {
            currentCullMode = cullMode
            when (cullMode)
            {
                CullMode.NONE -> glDisable(GL_CULL_FACE)
                CullMode.BACK -> { glEnable(GL_CULL_FACE); glCullFace(GL_BACK) }
            }
        }
    }

    companion object
    {
        private var currentProgramId = -1
        private var currentCullMode = null as CullMode?
        fun resetBoundProgramAndCullMode()
        { 
            currentProgramId = -1 
            currentCullMode = null 
        }
    }
}

enum class ModelShaderVariant
{
    STATIC, SKINNED
}

class ModelProgramSet(
    val staticProgram: ShaderProgram,
    val skinnedProgram: ShaderProgram
) {
    operator fun get(variant: ModelShaderVariant) = when (variant)
    {
        STATIC -> staticProgram
        SKINNED -> skinnedProgram
    }
}

class ModelBatchList(initialCapacity: Int = 128)
{
    @PublishedApi
    internal val batches = ArrayList<ModelBatch>(initialCapacity)

    var commandStartIndex = 0
        private set

    var size = 0
        private set

    fun clear(commandStartIndex: Int = 0)
    {
        this.size = 0
        this.commandStartIndex = commandStartIndex
    }

    fun lastOrNull() = if (size == 0) null else batches[size - 1]

    fun totalInstanceCount(): Int
    {
        var count = 0
        for (i in 0 until size)
            count += batches[i].instanceCount
        return count
    }

    fun add(model: Model, subMesh: Model.SubMesh, shaderVariant: ModelShaderVariant, cullMode: CullMode, instanceIndex: Int, instanceCount: Int)
    {
        val batch = if (size < batches.size) batches[size] else ModelBatch().also { batches += it }
        batch.set(model, subMesh, shaderVariant, cullMode, instanceIndex, instanceCount)
        size++
    }

    inline fun forEach(action: (ModelBatch) -> Unit)
    {
        for (i in 0 until size) action(batches[i])
    }
}