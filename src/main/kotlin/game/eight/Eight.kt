package game.eight

import engine.PulseEngine
import engine.modules.Game
import engine.data.Array2D
import engine.data.Color
import engine.data.Key
import engine.data.Mouse
import engine.modules.graphics.postprocessing.effects.BloomEffect
import engine.modules.graphics.postprocessing.effects.LightingEffect
import engine.modules.graphics.postprocessing.effects.VignetteEffect
import engine.modules.graphics.postprocessing.effects.BlurEffect
import engine.modules.graphics.postprocessing.effects.MultiplyEffect
import game.eight.Eight.BlockType.*
import kotlin.math.*
import kotlin.random.Random

fun main() = PulseEngine.run(Eight())

class Eight : Game()
{
    private val targetBoard = Array2D(8, 8) { x, y -> GROUND }
    private val board = Array2D(8, 8) { x, y -> GROUND }
    private var levels: MutableList<Level> = mutableListOf()
    private var maxRacedLevel = 0
    private var changLevelTo = -1

    private var xPlayer = 4
    private var yPlayer = 4
    private var winningFade = 0f
    private var angle = 0f
    private var screenShake = 0f
    private var shakeMagnitude = 50f
    private var shakeDampening = 0.8f
    private var yMoveInterpolation = 0f
    private var xMoveInterpolation = 0f
    private lateinit var currentLevel: Level

    private var editBlockType = WALL
    private var editMode = false
    private var juiceMode = true
    private var bgColor = Color(0.2f, 0.2f, 0.2f)

    // Effects
    private lateinit var lightingEffect: LightingEffect
    private lateinit var bloomEffect: BloomEffect
    private lateinit var vignetteEffect: VignetteEffect
    private lateinit var multiplyEffect: MultiplyEffect

    override fun init()
    {
        engine.window.title = "Eight"
        engine.config.targetFps = 120

        lightingEffect = LightingEffect(engine.gfx.mainCamera)

        val lightMask = engine.gfx
            .createSurface2D("lightMask")
            .setBackgroundColor(0.02f, 0.02f, 0.02f, 1f)
            .addPostProcessingEffect(lightingEffect)
            .addPostProcessingEffect(BlurEffect(radius = 0.1f))
            .setIsVisible(false)

        bloomEffect = BloomEffect(0.1f, exposure = 1.0f, blurPasses = 3, blurRadius = 0.3f)
        vignetteEffect = VignetteEffect()
        multiplyEffect = MultiplyEffect(lightMask)

        engine.gfx.mainSurface
            .setBackgroundColor(0f, 0f, 0f, 1f)
            .addPostProcessingEffect(multiplyEffect)

        engine.gfx
            .createSurface2D("blocks", camera = engine.gfx.mainCamera)
            .addPostProcessingEffect(bloomEffect)
            .addPostProcessingEffect(vignetteEffect)

        levels = engine.data.load<LevelList>("levels.dat")
            ?.toLevels()?.toMutableList()
            ?: (engine.data.loadInternal<LevelList>("eight/levels.dat")?.toLevels()?.toMutableList()
            ?: createBlankLevel())
        currentLevel = levels.first()

        engine.data.addSource("LEVEL", "") { levels.indexOf(currentLevel).toFloat() }
        reset()
        toggleJuiceMode()
    }

    private fun createBlankLevel(): MutableList<Level>
    {
        println("Creating blank level")
        val newLevel = Level(
            targetBoard = Array2D(8, 8) { x, y -> GROUND },
            board = Array2D(8, 8) { x, y -> GROUND },
            xPlayer = 4,
            yPlayer = 4
        )
        newLevel.board[4, 4] = PLAYER

        val index = levels.indexOf(currentLevel) + 1
        if(index >= levels.size)
            levels.add(newLevel)
        else
            levels.add(index, newLevel)
        currentLevel = newLevel
        reset()
        return levels
    }

    override fun fixedUpdate()
    {
        angle += 2
        winningFade -= 0.006f + if (editMode) 0.1f else 0f
        screenShake *= shakeDampening
        xMoveInterpolation *= 0.8f
        yMoveInterpolation *= 0.8f
    }

    override fun update()
    {
        if (winningFade > 0)
            screenShake = (cos(winningFade * PI * 2f + PI).toFloat() + 1f) / 2f

        if (engine.input.wasClicked(Key.L) && engine.input.isPressed(Key.LEFT_CONTROL))
            editMode = !editMode

        if (engine.input.wasClicked(Key.R))
            reset()

        if (engine.input.wasClicked(Key.J))
            toggleJuiceMode()

        if (engine.input.wasClicked(Key.PAGE_DOWN))
            increaseLevel(-1)

        if (engine.input.wasClicked(Key.PAGE_UP))
            increaseLevel(1)

        if (editMode)
            edit()

        if (checkWin() && !editMode)
        {
            if (levels.indexOf(currentLevel) == maxRacedLevel)
                maxRacedLevel++
            increaseLevel(1)
        }

        if (winningFade >= 0)
        {
            if (winningFade < 0.5f && changLevelTo != -1)
            {
                currentLevel = levels[changLevelTo]
                changLevelTo = -1
                reset()
            }
            return
        }

        var xPlayerNew = xPlayer
        var yPlayerNew = yPlayer

        if (engine.input.wasClicked(Key.W) || engine.input.wasClicked(Key.UP)) yPlayerNew--
        if (engine.input.wasClicked(Key.A) || engine.input.wasClicked(Key.LEFT)) xPlayerNew--
        if (engine.input.wasClicked(Key.S) || engine.input.wasClicked(Key.DOWN)) yPlayerNew++
        if (engine.input.wasClicked(Key.D) || engine.input.wasClicked(Key.RIGHT)) xPlayerNew++

        xPlayerNew = xPlayerNew.coerceIn(0, board.width - 1)
        yPlayerNew = yPlayerNew.coerceIn(0, board.height - 1)

        val xMoveDiff = xPlayerNew - xPlayer
        val yMoveDiff = yPlayerNew - yPlayer

        board[xPlayer, yPlayer] = GROUND
        val block = board[xPlayerNew, yPlayerNew]
        if (block == BLOCK)
        {
            // Pushing
            val moved = move(xPlayerNew, yPlayerNew, xMoveDiff, yMoveDiff)
            screenShake = 0.2f
            if (!moved)
                return
        }
        else if (block == STICKY_BLOCK || block == WALL)
        {
            // Stop player movement
            board[xPlayer, yPlayer] = PLAYER
            screenShake = 0.4f
            return
        }

        // Pulling
        for(y in 0 until 3)
        {
            for(x in 0 until 3)
            {
                val x0 = xPlayer + x - 1
                val y0 = yPlayer + y - 1
                if (x0 >= 0 && x0 < board.width && y0 >= 0 && y0 < board.height && (x+y+1) % 2 == 0)
                {
                    when
                    {
                        board[x0, y0] != STICKY_BLOCK -> {}
                        x0 > xPlayer && xMoveDiff < 0 -> { move(x0, y0, -1, 0); screenShake = 0.2f }
                        x0 < xPlayer && xMoveDiff > 0 -> { move(x0, y0, 1, 0); screenShake = 0.2f }
                        y0 > yPlayer && yMoveDiff < 0 -> { move(x0, y0, 0, -1); screenShake = 0.2f }
                        y0 < yPlayer && yMoveDiff > 0 -> { move(x0, y0, 0, 1); screenShake = 0.2f }
                    }
                }
            }
        }

        if (xMoveDiff != 0 || yMoveDiff != 0)
        {
            xMoveInterpolation = abs(xMoveDiff.toFloat())
            yMoveInterpolation = abs(yMoveDiff.toFloat())
        }

        // Move player
        board[xPlayerNew, yPlayerNew] = PLAYER
        xPlayer = xPlayerNew
        yPlayer = yPlayerNew
    }

    private fun move(xCell: Int, yCell: Int, xDist: Int, yDist: Int): Boolean
    {
        if (xCell+xDist < 0 || xCell + xDist >= board.width || yCell+yDist < 0 || yCell + yDist >= board.height)
            return false

        if (board[xCell+xDist, yCell+yDist] != GROUND)
            return false

        val type = board[xCell, yCell]
        board[xCell, yCell] = GROUND
        board[xCell+xDist, yCell+yDist] = type
        return true
    }

    private fun checkWin(): Boolean {
        if(currentLevel == levels.last())
            return false

        for (y in 0 until board.height)
            for (x in 0 until board.width)
                if (targetBoard[x, y] == BLOCK || targetBoard[x, y] == STICKY_BLOCK)
                    if(targetBoard[x, y] != board[x, y])
                        return false
        return true
    }

    private fun increaseLevel(amount: Int)
    {
        val index = levels.indexOf(currentLevel) + amount

        if (index >= 0 && index < levels.size && (editMode || index <= maxRacedLevel))
        {
            if (winningFade < 0f && changLevelTo == -1)
            {
                winningFade = 1f
                changLevelTo = index
            }
        }
    }

    private fun reset()
    {
        xPlayer = currentLevel.xPlayer
        yPlayer = currentLevel.yPlayer

        for (y in 0 until board.height)
            for (x in 0 until board.width)
            {
                board[x, y] = currentLevel.board[x, y]
                targetBoard[x, y] = currentLevel.targetBoard[x, y]
            }
    }

    private fun toggleJuiceMode()
    {
        juiceMode = !juiceMode
        if (juiceMode)
        {
            multiplyEffect.active = true
            lightingEffect.active = true
            bloomEffect.active = true
            vignetteEffect.active = true
            bgColor = Color(1f, 1f, 1f)
        }
        else
        {
            multiplyEffect.active = false
            lightingEffect.active = false
            bloomEffect.active = false
            vignetteEffect.active = false
            bgColor = Color(0.2f, 0.2f, 0.2f)
        }
    }

    private fun edit()
    {
        val cellHeight = engine.window.height / 8f
        val cellWidth = cellHeight
        val xOffset = (engine.window.width - cellWidth * 8) / 2

        val x0 = ((engine.input.xMouse - xOffset) /  cellWidth).toInt()
        val y0 = (engine.input.yMouse /  cellHeight).toInt()
        if (x0 >= 0 && x0 < currentLevel.board.width && y0 >= 0 && y0 < currentLevel.board.height)
        {
            if (engine.input.isPressed(Mouse.BUTTON_LEFT))
            {
                currentLevel.board[x0, y0] = editBlockType
                if (editBlockType == PLAYER)
                {
                    currentLevel.board[xPlayer, yPlayer] = GROUND
                    currentLevel.xPlayer = x0
                    currentLevel.yPlayer = y0
                }
                reset()
            }

            if (engine.input.isPressed(Mouse.BUTTON_RIGHT))
            {
                currentLevel.targetBoard[x0, y0] = editBlockType
                reset()
            }

            if (engine.input.isPressed(Mouse.BUTTON_MIDDLE))
            {
                currentLevel.board[x0, y0] = GROUND
                currentLevel.targetBoard[x0, y0] = GROUND
                reset()
            }
        }

        if (engine.input.wasClicked(Key.K_0)) editBlockType = PLAYER
        if (engine.input.wasClicked(Key.K_1)) editBlockType = WALL
        if (engine.input.wasClicked(Key.K_2)) editBlockType = BLOCK
        if (engine.input.wasClicked(Key.K_3)) editBlockType = STICKY_BLOCK
        if (engine.input.wasClicked(Key.K_4)) editBlockType = WINNING
        if (engine.input.wasClicked(Key.ENTER)) createBlankLevel()
    }

    override fun render()
    {
        val alpha = 0.1f + 0.9f * ((sin(angle / 45) + 1f) / 2f)
        val cellSize = engine.window.height / 8f
        val xOffset = (engine.window.width - cellSize * 8) / 2

        engine.gfx.mainSurface.setDrawColor(bgColor.red, bgColor.green, bgColor.blue)
        engine.gfx.mainSurface.drawQuad(xOffset, 0f, cellSize * 8f, cellSize * 8f)

        for (y in 0 until board.height)
        {
            for (x in 0 until board.width)
            {
                val surface = engine.gfx.getSurface2D("blocks")
                val targetColor = targetBoard[x, y].color
                surface.setDrawColor(targetColor.red, targetColor.green, targetColor.blue, targetColor.alpha * alpha)
                surface.drawQuad(x * cellSize + xOffset, y * cellSize, cellSize, cellSize)

                var xScale = 1f
                var yScale = 1f
                var color = board[x, y].color

                if (board[x, y] == PLAYER && juiceMode)
                {
                    xScale = 1f - yMoveInterpolation * 0.5f + xMoveInterpolation * 0.5f
                    yScale = 1f - xMoveInterpolation * 0.5f + yMoveInterpolation * 0.5f
                }

                if ((board[x, y] == BLOCK || board[x, y] == STICKY_BLOCK) && board[x, y] == targetBoard[x, y])
                    color = GREEN

                surface.setDrawColor(color.red, color.green, color.blue, color.alpha)
                surface.drawQuad(
                    x = x * cellSize + xOffset + (1 - xScale) * cellSize / 2f,
                    y = y * cellSize + (1 - yScale) * cellSize / 2f,
                    width = cellSize * xScale,
                    height = cellSize * yScale)
            }
        }

        if (juiceMode)
        {
            addLighting()
            if (screenShake > 0)
            {
                engine.gfx.mainCamera.xPos = (Random.nextFloat() * 2 - 1) * screenShake * shakeMagnitude
                engine.gfx.mainCamera.yPos = (Random.nextFloat() * 2 - 1) * screenShake * shakeMagnitude
            }
        }

        if (winningFade > 0f)
        {
            val surface = engine.gfx.getSurface2D("blocks")
            val fade = (cos(winningFade * PI*2f + PI).toFloat() + 1f) / 2f
            surface.setDrawColor(0.0f, 0.0f, 0.0f, fade)
            surface.drawQuad(xOffset, 0f, cellSize * 8f, cellSize * 8f)
        }
    }

    private fun addLighting()
    {
        val alpha = 0.1f + 0.9f * ((sin(angle / 45) + 1f) / 2f)
        val cellSize = engine.window.height / 8f
        val xOffset = (engine.window.width - cellSize * 8) / 2

        for (y in 0 until board.height)
        {
            for (x in 0 until board.width)
            {
                val color = board[x, y].color
                val targetColor = targetBoard[x, y].color
                val type = board[x, y]
                val targetType = targetBoard[x, y]
                var xPos = (x + 0.5f) * cellSize + xOffset
                var yPos = (y + 0.5f) * cellSize

                if (type == BLOCK || type == STICKY_BLOCK)
                {
                    if (type == targetType)
                        lightingEffect.addLight(xPos, yPos, 400f, 1.2f, 0f, GREEN.red, GREEN.green, GREEN.blue)
                    else
                        lightingEffect.addLight(xPos, yPos, 400f, 1.2f, 0f, color.red, color.green, color.blue)
                }
                else if (type == PLAYER)
                {
                    lightingEffect.addLight(xPos, yPos, 400f, 1.2f, 0f, color.red, color.green, color.blue)
                }
                else if (type == WALL)
                {
                    xPos = x * cellSize + xOffset
                    yPos = y * cellSize
                    lightingEffect.addEdge(xPos, yPos, xPos + cellSize, yPos)
                    lightingEffect.addEdge(xPos, yPos + cellSize, xPos + cellSize, yPos + cellSize)
                    lightingEffect.addEdge(xPos, yPos, xPos, yPos + cellSize)
                    lightingEffect.addEdge(xPos + cellSize, yPos, xPos + cellSize, yPos + cellSize)
                }
                else if (targetType == STICKY_BLOCK || targetType == BLOCK)
                {
                    lightingEffect.addLight(xPos, yPos, 300f, alpha, 0f, targetColor.red, targetColor.green, targetColor.blue)
                }
            }
        }
    }

    override fun cleanup()
    {
        engine.data.save(LevelList.fromLevels(levels), "levels.dat")
    }

    enum class BlockType(val color: Color)
    {
        GROUND(Color(0.0f, 0.0f, 0.0f, 0f)),
        PLAYER(Color(0.7f, 0.1f, 0.1f)),
        BLOCK(Color(36/255f, 95/255f, 166/255f)),
        STICKY_BLOCK(Color(250/255f, 196/255f, 35/255f)),
        WALL(Color(0.1f, 0.1f, 0.1f)),
        WINNING(Color(120/255f, 209/255f, 69/255f))
    }

    data class Level(
        val targetBoard: Array2D<BlockType>,
        val board: Array2D<BlockType>,
        var xPlayer: Int,
        var yPlayer: Int
    )

    data class DiskLevel(
        val targetBoard: List<String>,
        val board: List<String>,
        var xPlayer: Int,
        var yPlayer: Int
    ) {
        companion object {
            fun fromLevel(level: Level): DiskLevel =
                DiskLevel(
                    board = level.board.map { it.name },
                    targetBoard = level.targetBoard.map { it.name },
                    xPlayer = level.xPlayer,
                    yPlayer = level.yPlayer
                )

            fun toLevel(level: DiskLevel): Level =
                Level(
                    board = Array2D(8, 8) { x, y -> valueOf(level.board[y * 8 + x]) },
                    targetBoard = Array2D(8, 8) { x, y -> valueOf(level.targetBoard[y * 8 + x]) },
                    xPlayer = level.xPlayer,
                    yPlayer = level.yPlayer
                )
        }
    }

    data class LevelList(
        val levels: List<DiskLevel>
    ) {
        companion object
        {
            fun fromLevels(levels: List<Level>): LevelList =
                LevelList(levels.map { DiskLevel.fromLevel(it) })
        }
    }

    fun LevelList.toLevels(): List<Level> =
        this.levels.map { DiskLevel.toLevel(it) }

    companion object
    {
        val GREEN = Color(120/255f, 209/255f, 69/255f)
    }
}




