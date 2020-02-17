package engine.modules

// Exposed to game and engine
interface DataInterface
{
    val currentFps: Int
    val renderTimeMs: Float
    val updateTimeMS: Float
    val deltaTime: Float
    val interpolation: Float
}

// Exposed to game engine
interface DataEngineInterface : DataInterface
{
    override var currentFps: Int
    override var renderTimeMs: Float
    override var updateTimeMS: Float
    override var deltaTime: Float
    override var interpolation: Float
}

class Data : DataEngineInterface
{
    override var currentFps: Int = 0
    override var renderTimeMs: Float = 0f
    override var updateTimeMS: Float = 0f
    override var deltaTime: Float = 0.017f
    override var interpolation: Float = 0f
}
