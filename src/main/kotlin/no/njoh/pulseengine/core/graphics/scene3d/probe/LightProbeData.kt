package no.njoh.pulseengine.core.graphics.scene3d.probe

import no.njoh.pulseengine.core.graphics.gpu.buffer.ShaderStorageBufferObject
import no.njoh.pulseengine.core.graphics.gpu.texture.TextureHandle

class LightProbeData
{
    var diffuseCubeMapTextureArrayHandle  = TextureHandle.NONE
    var specularCubeMapTextureArrayHandle = TextureHandle.NONE
    var specularCubeMapMipCount           = 1
    var probeCount                        = 0
    var probeGpuBuffer                    = null as ShaderStorageBufferObject?
}