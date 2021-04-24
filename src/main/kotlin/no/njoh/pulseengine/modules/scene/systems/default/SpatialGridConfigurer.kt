package no.njoh.pulseengine.modules.scene.systems.default

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.Scene
import no.njoh.pulseengine.modules.scene.systems.SceneSystem
import no.njoh.pulseengine.widgets.sceneEditor.Name
import no.njoh.pulseengine.widgets.sceneEditor.ValueRange

@Name("Spatial Grid")
class SpatialGridConfigurer : SceneSystem()
{
    @ValueRange(10f, 1_000f)
    var cellSize = 350f

    @ValueRange(0f, 100_000f)
    var minBorderSize = 3000

    @ValueRange(0f, 100_000_000f)
    var maxWidth = 100_000

    @ValueRange(0f, 100_000_000f)
    var maxHeight = 100_000

    @ValueRange(0f, 1f)
    var percentageToUpdatePerFrame = 0.2f

    override fun onUpdate(engine: PulseEngine)
    {
        val spatialGrid = engine.scene.activeScene.spatialGrid

        if (cellSize != spatialGrid.cellSize ||
            minBorderSize != spatialGrid.minBorderSize ||
            maxWidth != spatialGrid.maxWidth ||
            maxHeight != spatialGrid.maxHeight ||
            percentageToUpdatePerFrame != spatialGrid.percentageToUpdatePerFrame
        ) {
            spatialGrid.cellSize = cellSize
            spatialGrid.minBorderSize = minBorderSize
            spatialGrid.maxWidth = maxWidth
            spatialGrid.maxHeight = maxHeight
            spatialGrid.percentageToUpdatePerFrame = percentageToUpdatePerFrame
            spatialGrid.recalculate()
        }
    }
}