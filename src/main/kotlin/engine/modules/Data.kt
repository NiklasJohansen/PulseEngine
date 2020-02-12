package engine.modules

// Exposed to game and engine
interface DataInterface
{
    val currentFps: Int
    val renderTimeMs: Float
    val updateTimeMS: Float
}

// Exposed to game engine
interface DataEngineInterface : DataInterface
{
    override var currentFps: Int
    override var renderTimeMs: Float
    override var updateTimeMS: Float
}

class Data : DataEngineInterface
{
    override var currentFps: Int = 0
    override var renderTimeMs: Float = 0f
    override var updateTimeMS: Float = 0f
}
