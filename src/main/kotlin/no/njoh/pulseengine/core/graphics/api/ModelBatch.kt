package no.njoh.pulseengine.core.graphics.api

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model
import org.lwjgl.opengl.GL11.GL_BACK
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.glCullFace
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable

class ModelBatch
{
    lateinit var model: Model
    lateinit var subMesh: Model.SubMesh
    lateinit var program: ShaderProgram
    lateinit var cullMode: CullMode

    var instanceIndex = 0
    var instanceCount = 0

    fun set(model: Model, subMesh: Model.SubMesh, program: ShaderProgram, cullMode: CullMode, instanceIndex: Int, instanceCount: Int)
    {
        this.model = model
        this.subMesh = subMesh
        this.program = program
        this.cullMode = cullMode
        this.instanceIndex = instanceIndex
        this.instanceCount = instanceCount
    }

    fun matches(model: Model, subMesh: Model.SubMesh, program: ShaderProgram, cullMode: CullMode): Boolean =
        this.model === model &&
        this.subMesh === subMesh &&
        this.program === program &&
        this.cullMode == cullMode
    
    fun bind()
    {
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
        fun reset()
        { 
            currentProgramId = -1 
            currentCullMode = null 
        }
    }
}

class ModelBatchList(initialCapacity: Int = 128)
{
    @PublishedApi
    internal val batches = ArrayList<ModelBatch>(initialCapacity)

    var size = 0; private set

    fun clear() { size = 0 }

    fun lastOrNull() = if (size == 0) null else batches[size - 1]

    fun totalInstanceCount(): Int
    {
        var count = 0
        for (i in 0 until size)
            count += batches[i].instanceCount
        return count
    }

    fun add(model: Model, subMesh: Model.SubMesh, program: ShaderProgram, cullMode: CullMode, instanceIndex: Int, instanceCount: Int)
    {
        val batch = if (size < batches.size) batches[size] else ModelBatch().also { batches += it }
        batch.set(model, subMesh, program, cullMode, instanceIndex, instanceCount)
        size++
    }

    inline fun forEach(action: (ModelBatch) -> Unit)
    {
        for (i in 0 until size) action(batches[i])
    }
}