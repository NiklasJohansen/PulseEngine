package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneSystem
import no.njoh.pulseengine.core.shared.annotations.Name
import no.njoh.pulseengine.core.shared.annotations.Property

@Name("Spatial Grid")
class SpatialGridConfigurer : SceneSystem()
{
    @Property(order = 0, min = 50f, max = 1_000f)
    var cellSize = 350f

    @Property(order = 1, min = 0f, max = 100_000_000f)
    var maxWidth = 100_000

    @Property(order = 2, min = 0f, max = 100_000_000f)
    var maxHeight = 100_000

    @Property(order = 3, min = 0f, max = 100_000f)
    var borderSize = 3000

    @Property(order = 4, min = 0f, max = 1f)
    var percentageToUpdatePerFrame = 0.2f

    @Property(order = 5, min = 0f, max = 1f)
    var percentagePositionChangeBeforeUpdate = 0.2f

    @Property(order = 6, min = 0f, max = 1f)
    var drawGrid = false

    override fun onUpdate(engine: PulseEngine)
    {
        val spatialGrid = engine.scene.activeScene.spatialGrid

        if (cellSize != spatialGrid.cellSize ||
            maxWidth != spatialGrid.maxWidth ||
            maxHeight != spatialGrid.maxHeight ||
            borderSize != spatialGrid.borderSize
        ) {
            spatialGrid.setCellSize(cellSize)
            spatialGrid.maxWidth = maxWidth
            spatialGrid.maxHeight = maxHeight
            spatialGrid.borderSize = borderSize
            spatialGrid.recalculate()
        }

        spatialGrid.percentageOfCellsToUpdatePerFrame = percentageToUpdatePerFrame
        spatialGrid.percentagePositionChangeBeforeUpdate = percentagePositionChangeBeforeUpdate
        spatialGrid.drawGrid = drawGrid
    }
}