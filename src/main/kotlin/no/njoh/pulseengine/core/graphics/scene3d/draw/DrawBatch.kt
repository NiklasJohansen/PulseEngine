package no.njoh.pulseengine.core.graphics.scene3d.draw

import no.njoh.pulseengine.core.asset.types.Material.CullMode
import no.njoh.pulseengine.core.asset.types.Model.Mesh
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet
import no.njoh.pulseengine.core.graphics.gpu.shader.ShaderProgramSet.ShaderVariant
import org.lwjgl.opengl.GL11.*

class DrawBatch
{
    lateinit var mesh: Mesh
    lateinit var cullMode: CullMode
    lateinit var shaderVariant: ShaderVariant

    var instanceIndex = 0
    var instanceCount = 0

    fun set(mesh: Mesh, shaderVariant: ShaderVariant, cullMode: CullMode, instanceIndex: Int, instanceCount: Int)
    {
        this.mesh = mesh
        this.shaderVariant = shaderVariant
        this.cullMode = cullMode
        this.instanceIndex = instanceIndex
        this.instanceCount = instanceCount
    }

    fun matches(mesh: Mesh, shaderVariant: ShaderVariant, cullMode: CullMode): Boolean =
        this.mesh === mesh &&
        this.shaderVariant == shaderVariant &&
        this.cullMode == cullMode

    fun bindProgramAndSetCullMode(programs: ShaderProgramSet)
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