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
    val totalMemory: Long
    val usedMemory: Long
    val dataSources: Map<String, DataSource>

    fun addSource(name: String, unit: String, source: () -> Float)

    companion object
    {
        internal lateinit var INSTANCE: DataInterface
    }
}

interface DataEngineInterface : DataInterface
{
    fun update()
}

class MutableDataContainer : DataEngineInterface
{
    override var currentFps: Int = 0
    override var renderTimeMs: Float = 0f
    override var updateTimeMS: Float = 0f
    override var fixedUpdateTimeMS: Float = 0f
    override var fixedDeltaTime: Float = 0.017f
    override var deltaTime: Float = 0.017f
    override var interpolation: Float = 0f
    override var usedMemory: Long = 0L
    override var totalMemory: Long = 0L
    override var dataSources = mutableMapOf<String, DataSource>()

    init
    {
        DataInterface.INSTANCE = this
    }

    override fun addSource(name: String, unit: String, source: () -> Float)
    {
        dataSources[name] = DataSource(name, unit, source)
    }

    override fun update()
    {
        usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / MEGA_BYTE
        totalMemory = runtime.maxMemory() / MEGA_BYTE
    }

    companion object
    {
        private val runtime = Runtime.getRuntime()
        private const val MEGA_BYTE = 1048576L
    }
}

data class DataSource(
    val name: String,
    val unit: String,
    val source: () -> Float
)
