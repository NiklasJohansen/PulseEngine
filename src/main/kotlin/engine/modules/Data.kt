package engine.modules

// Exposed to game and engine
interface DataInterface
{
    val currentFps: Int
    val renderTimeMs: Float
    val updateTimeMS: Float
    val fixedUpdateTimeMS: Float
    val fixedDeltaTime: Float
    val deltaTime: Float
    val interpolation: Float

    companion object
    {
        internal lateinit var INSTANCE: DataInterface
    }
}

class MutableDataContainer : DataInterface
{
    override var currentFps: Int = 0
    override var renderTimeMs: Float = 0f
    override var updateTimeMS: Float = 0f
    override var fixedUpdateTimeMS: Float = 0f
    override var fixedDeltaTime: Float = 0.017f
    override var deltaTime: Float = 0.017f
    override var interpolation: Float = 0f

    init
    {
        DataInterface.INSTANCE = this
    }
}
