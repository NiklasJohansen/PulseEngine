package engine.modules.graphics

import engine.data.ShaderType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.glBindFragDataLocation

class ShaderProgram(private val id: Int)
{
    // Used for getting matrix data as array
    private val floatArray16 = FloatArray(16)

    fun use() = this.also { glUseProgram(id) }

    fun delete() = glDeleteProgram(id)

    fun getAttributeLocation(name: String) = glGetAttribLocation(id, name)

    fun getUniformLocation(name: String) = glGetUniformLocation(id, name)

    fun setUniform(name: String, vec3: Vector3f): Vector3f
    {
        glUniform3f(getUniformLocation(name), vec3[0], vec3[1], vec3[2])
        return vec3
    }

    fun setUniform(name: String, vec4: Vector4f): Vector4f
    {
        glUniform4f(getUniformLocation(name), vec4[0], vec4[1], vec4[2], vec4[3])
        return vec4
    }

    fun setUniform(name: String, matrix: Matrix4f): Matrix4f
    {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix.get(floatArray16))
        return matrix
    }

    fun setUniform(name: String, value: Int): Int
    {
        glUniform1i(getUniformLocation(name), value)
        return value
    }

    fun setUniform(name: String, value: Float): Float
    {
        glUniform1f(getUniformLocation(name), value)
        return value
    }

    fun defineVertexAttributeArray(name: String, size: Int, type: Int, stride: Int, offset: Int, normalized: Boolean = false)
    {
        val location = getAttributeLocation(name)
        glEnableVertexAttribArray(location)
        glVertexAttribPointer(location, size, type, normalized, stride, offset.toLong())
    }

    companion object
    {
        fun create(vertexShaderFileName: String, fragmentShaderFileName: String): ShaderProgram
        {
            val vertexShader = Shader.load(vertexShaderFileName, ShaderType.VERTEX)
            val fragmentShader = Shader.load(fragmentShaderFileName, ShaderType.FRAGMENT)
            val program = create(vertexShader, fragmentShader)

            vertexShader.delete()
            fragmentShader.delete()

            return program
        }

        fun create(vararg shaders: Shader): ShaderProgram
        {
            val programId = glCreateProgram()
            for(shader in shaders)
                glAttachShader(programId, shader.id)

            glBindFragDataLocation(programId, 0, "fragColor")
            glLinkProgram(programId)

            if (glGetProgrami(programId, GL_LINK_STATUS) != GL_TRUE)
                throw RuntimeException(glGetProgramInfoLog(programId))

            return ShaderProgram(programId)
        }
    }
}