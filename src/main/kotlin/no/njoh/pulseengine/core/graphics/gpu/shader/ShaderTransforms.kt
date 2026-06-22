package no.njoh.pulseengine.core.graphics.gpu.shader

/**
 * Defines shader variants by adding preprocessor defines.
 */
fun defineShaderVariant(vararg defines: String): (source: String) -> String = { source ->
    if (defines.isNotEmpty())
    {
        val versionLineEnd = source.indexOf('\n').let { if (it >= 0) it + 1 else 0 }
        val definitionBlock = defines.joinToString(separator = "") { "#define $it 1\n" }
        source.substring(0, versionLineEnd) + definitionBlock + source.substring(versionLineEnd)
    } 
    else source
}