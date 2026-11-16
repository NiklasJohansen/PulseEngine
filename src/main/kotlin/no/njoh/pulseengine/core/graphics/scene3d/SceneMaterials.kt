package no.njoh.pulseengine.core.graphics.scene3d

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.AssetHandle
import no.njoh.pulseengine.core.asset.types.Asset
import no.njoh.pulseengine.core.asset.types.Material
import no.njoh.pulseengine.core.graphics.scene3d.submission.RenderScene
import no.njoh.pulseengine.core.shared.datastructures.DynamicList
import no.njoh.pulseengine.core.shared.utils.Logger

/**
 * Retained storage for "immediate-mode" material submissions
 */
class SceneMaterials
{
    private val submissionsByScene            = Object2ObjectOpenHashMap<String, Long2ObjectOpenHashMap<MaterialSubmission>>()
    private val selectedSubmissionsByMaterial = Object2ObjectOpenHashMap<String, MaterialSubmission>()
    private val retainedSubmissions           = DynamicList<MaterialSubmission>()
    private var submissionFrame               = 0L

    fun createMaterialSubmission(
        sourceId: Long,
        sceneFileName: String, 
        materialName: String, 
        materialToOverride: AssetHandle<Material>? = null
    ): MaterialSubmission {

        val sceneSubmissions = submissionsByScene.getOrPut(sceneFileName) { Long2ObjectOpenHashMap() }
        var submission = sceneSubmissions[sourceId]
        if (submission == null)
        {
            submission = MaterialSubmission(sourceId, sceneFileName, "scene-material/$sceneFileName/$sourceId")
            sceneSubmissions[sourceId] = submission
            retainedSubmissions += submission
        }

        val overrideMaterialName = materialToOverride?.name.orEmpty()
        submission.lastSubmittedFrame   = submissionFrame
        submission.overrideMaterialName = overrideMaterialName
        submission.materialHandle.name  = overrideMaterialName.ifEmpty { submission.assetName }

        if (submission.materialName != materialName)
        {
            submission.materialName = materialName
            submission.displayName  = "Scene / $materialName"
        }

        return submission
    }

    fun initFrame(engine: PulseEngineInternal, renderScene: RenderScene)
    {
        renderScene.clearMaterialOverrides()
        selectedSubmissionsByMaterial.clear()

        retainedSubmissions.removeIf { submission ->

            submission.isSelected = false

            if (submission.lastSubmittedFrame != submissionFrame) // True if not submitted this frame
            {
                submission.unloadCreatedMaterialAsset(engine)

                // Keep the submission for one extra frame as it may still be used by the renderer
                if (submission.lastSubmittedFrame == submissionFrame - 1)
                    return@removeIf false

                submission.deleteFrameMaterials(engine)

                val sceneSubmissions = submissionsByScene[submission.sceneFileName]
                sceneSubmissions?.remove(submission.sourceId)

                if (sceneSubmissions?.isEmpty() == true) 
                    submissionsByScene.remove(submission.sceneFileName)

                return@removeIf true
            }

            val wasSuccessful = submission.update(engine)
            if (wasSuccessful)
                pickSelectedMaterialSubmission(submission)
            
            false // Keep this submission for the next frame
        }

        // Apply the selected submissions to the render scene
        retainedSubmissions.forEach() 
        {
            if (it.isSelected)
            {
                val target = engine.asset.getOrNull(it.materialHandle)
                if (target != null) renderScene.setMaterialOverride(target, it.activeFrameMaterial)
            }
        }

        submissionFrame++
    }

    fun destroy(engine: PulseEngineInternal)
    {
        retainedSubmissions.forEach() 
        {
            it.unloadCreatedMaterialAsset(engine)
            it.deleteFrameMaterials(engine)
        }
        retainedSubmissions.clear()
        submissionsByScene.clear()
        selectedSubmissionsByMaterial.clear()
    }

    private fun pickSelectedMaterialSubmission(candidateSubmission: MaterialSubmission)
    {
        val materialAssetName = candidateSubmission.overrideMaterialName.ifEmpty { candidateSubmission.assetName }
        val currentSubmission = selectedSubmissionsByMaterial[materialAssetName]

        val shouldUseCandidateSubmission = when
        {
            // Use the candidate submission if there is no current submission for this material
            currentSubmission == null -> true

            // Use the candidate submission if it overrides a material and the current submission does not
            candidateSubmission.overrideMaterialName.isNotEmpty() && currentSubmission.overrideMaterialName.isEmpty() -> true

            // Use the current submission if it overrides a material and the candidate submission does not
            candidateSubmission.overrideMaterialName.isEmpty() && currentSubmission.overrideMaterialName.isNotEmpty() -> false

            // Equal priority: use the highest source ID, regardless of submission order
            else -> candidateSubmission.sourceId > currentSubmission.sourceId
        }

        if (shouldUseCandidateSubmission)
        {
            currentSubmission?.isSelected  = false
            candidateSubmission.isSelected = true
            selectedSubmissionsByMaterial[materialAssetName] = candidateSubmission
        }
    }

    class MaterialSubmission(val sourceId: Long, val sceneFileName: String, val assetName: String)
    {
        val materialHandle       = AssetHandle<Material>(assetName)
        val materialToSubmit     = Material("$assetName/pending")
        var overrideMaterialName = ""
        var materialName         = ""
        var displayName          = "Scene / Material"
        var lastSubmittedFrame   = -1L
        var isSelected           = false

        private val frameMaterialPing       = Material("$assetName/0")
        private val frameMaterialPong       = Material("$assetName/1")
        private var createdMaterialAsset    = null as Material?
        private var hasLoggedAssetCollision = false

        var activeFrameMaterial = frameMaterialPing; private set

        fun update(engine: PulseEngineInternal): Boolean
        {
            if (overrideMaterialName.isEmpty())
            {
                // Check if the created asset has been unloaded or replaced outside this submission
                val registeredAsset = engine.asset.getOrNull<Asset>(assetName)
                if (createdMaterialAsset !== registeredAsset)
                    createdMaterialAsset = null

                // This submission does not override an existing material asset, so create a new one instead
                var materialAsset = createdMaterialAsset
                if (materialAsset == null)
                {
                    // Check for name collisions with existing assets before creating a new one
                    if (registeredAsset != null)
                    {
                        if (!hasLoggedAssetCollision) 
                            Logger.error { "New scene material asset collides with existing asset: $assetName. Skipping creation." }
                        hasLoggedAssetCollision = true
                        return false
                    }

                    materialAsset = Material(assetName)
                    materialAsset.setFrom(materialToSubmit)
                    engine.asset.loadNow(materialAsset)
                    createdMaterialAsset = materialAsset
                }

                materialAsset.displayName = displayName
                materialAsset.setFrom(materialToSubmit)
            } 
            else
            {
                // Remove the created material asset as this submission is now overriding an existing material asset instead
                unloadCreatedMaterialAsset(engine)
            }

            val currentMaterial = activeFrameMaterial
            if (currentMaterial.id == Material.DEFAULT_ID || !currentMaterial.isEqual(materialToSubmit))
            {
                // Submitted material has changed, so upload it to the GPU and switch the active material
                val nextMaterial = if (currentMaterial === frameMaterialPing) frameMaterialPong else frameMaterialPing
                nextMaterial.setFrom(materialToSubmit)
                engine.gfx.uploadMaterial(nextMaterial)
                activeFrameMaterial = nextMaterial
            }

            hasLoggedAssetCollision = false
            return true
        }

        fun deleteFrameMaterials(engine: PulseEngineInternal)
        {
            engine.gfx.deleteMaterial(frameMaterialPing)
            engine.gfx.deleteMaterial(frameMaterialPong)
        }

        fun unloadCreatedMaterialAsset(engine: PulseEngineInternal)
        {
            val materialAsset = createdMaterialAsset ?: return
            if (engine.asset.getOrNull<Material>(assetName) === materialAsset)
                engine.asset.unload(assetName)
            createdMaterialAsset = null
        }
    }
}