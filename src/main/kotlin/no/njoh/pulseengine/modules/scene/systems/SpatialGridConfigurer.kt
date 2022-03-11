package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Property

@Name("Spatial Grid")
class SpatialGridConfigurer : SceneSystem()
{
    @Property(min = 50f, max = 1_000f)
    var cellSize = 350f

    @Property(min = 0f, max = 100_000f)
    var minBorderSize = 3000

    @Property(min = 0f, max = 100_000_000f)
    var maxWidth = 100_000

    @Property(min = 0f, max = 100_000_000f)
    var maxHeight = 100_000

    @Property(min = 0f, max = 1f)
    var percentageToUpdatePerFrame = 0.2f

    var drawGrid = false

    override fun onUpdate(engine: PulseEngine)
    {
        val spatialGrid = engine.scene.activeScene.spatialGrid

        if (cellSize != spatialGrid.cellSize ||
            minBorderSize != spatialGrid.minBorderSize ||
            maxWidth != spatialGrid.maxWidth ||
            maxHeight != spatialGrid.maxHeight ||
            percentageToUpdatePerFrame != spatialGrid.percentageToUpdatePerFrame ||
            drawGrid != spatialGrid.drawGrid
        ) {
            spatialGrid.cellSize = cellSize
            spatialGrid.minBorderSize = minBorderSize
            spatialGrid.maxWidth = maxWidth
            spatialGrid.maxHeight = maxHeight
            spatialGrid.percentageToUpdatePerFrame = percentageToUpdatePerFrame
            spatialGrid.drawGrid = drawGrid
            spatialGrid.recalculate()
        }
    }
}