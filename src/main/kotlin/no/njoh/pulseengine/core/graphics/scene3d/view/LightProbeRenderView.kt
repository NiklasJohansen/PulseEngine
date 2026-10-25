package no.njoh.pulseengine.core.graphics.scene3d.view

import no.njoh.pulseengine.core.graphics.camera.CameraProjectionType.PERSPECTIVE_3D
import no.njoh.pulseengine.core.graphics.camera.DefaultCamera
import no.njoh.pulseengine.core.graphics.scene3d.draw.DrawCommandBuilder
import no.njoh.pulseengine.core.graphics.scene3d.probe.LightProbe
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.graphics.scene3d.view.RenderPassMask.Companion.REFLECTION
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import org.joml.Vector3f

/** 
 * Six independent camera views reuse normal scene culling, instancing and LOD selection. 
 */
class LightProbeRenderView : RenderView(REFLECTION), CameraRenderStateProvider
{
    val cameras     = Array(6) { DefaultCamera(PERSPECTIVE_3D) }
    val cameraViews = Array(6) { CameraRenderView(renderPassMask = REFLECTION) }

    var firstFace = 0; private set
    var faceCount = 0; private set

    override val cameraStates = DynamicList<CameraRenderState>(6)
    override val shadowReferencePosition: Vector3f? = null

    override fun beginFrame()
    {
        cameraStates.clear()
        faceCount = 0
        for (view in cameraViews) view.beginFrame()
    }

    fun setFor(probe: LightProbe, resolution: Int, firstFace: Int = 0, faceCount: Int = 6)
    {
        require(firstFace in 0..5 && faceCount in 1..6 - firstFace) { "Invalid light probe capture face range" }
        this.firstFace = firstFace
        this.faceCount = faceCount

        // Reuse the same view slots across frames so their light grids can also be reused.
        for (i in 0 until faceCount)
        {
            val camera = cameras[i]
            
            probe.getCapturePosition(camera.position)
            val pos = camera.position
            val dir = directions[firstFace + i]
            val up  = upDirections[firstFace + i]

            camera.nearPlane = probe.nearPlane
            camera.farPlane  = probe.farPlane
            camera.projectionMatrix.setPerspective((Math.PI * 0.5).toFloat(), 1f, probe.nearPlane, probe.farPlane)
            camera.viewMatrix.setLookAt(pos.x, pos.y, pos.z, pos.x + dir.x, pos.y + dir.y, pos.z + dir.z, up.x, up.y, up.z)
            camera.projectionMatrix.mul(camera.viewMatrix, camera.viewProjectionMatrix)
            camera.viewMatrix.invert(camera.invViewMatrix)
            camera.projectionMatrix.invert(camera.invProjectionMatrix)
            camera.viewProjectionMatrix.invert(camera.invViewProjectionMatrix)

            cameraStates += cameraViews[i].addCameraStateFor(camera, resolution, resolution)
        }
    }

    override fun prepare(scene: RenderScene, builder: DrawCommandBuilder)
    {
        for (i in 0 until faceCount) cameraViews[i].prepare(scene, builder)
    }

    companion object
    {
        // OpenGL cubemap order and orientation: +X, -X, +Y, -Y, +Z, -Z.
        private val directions   = arrayOf(Vector3f(1f, 0f, 0f), Vector3f(-1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, -1f, 0f), Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, -1f))
        private val upDirections = arrayOf(Vector3f(0f, -1f, 0f), Vector3f(0f, -1f, 0f), Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, -1f), Vector3f(0f, -1f, 0f), Vector3f(0f, -1f, 0f))
    }
}