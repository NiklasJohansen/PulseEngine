package no.njoh.pulseengine.core

import no.njoh.pulseengine.core.asset.AssetManager
import no.njoh.pulseengine.core.asset.AssetManagerInternal
import no.njoh.pulseengine.core.audio.Audio
import no.njoh.pulseengine.core.audio.AudioInternal
import no.njoh.pulseengine.core.config.Configuration
import no.njoh.pulseengine.core.config.ConfigurationInternal
import no.njoh.pulseengine.core.console.Console
import no.njoh.pulseengine.core.console.ConsoleInternal
import no.njoh.pulseengine.core.data.Data
import no.njoh.pulseengine.core.graphics.Graphics
import no.njoh.pulseengine.core.graphics.GraphicsInternal
import no.njoh.pulseengine.core.input.Input
import no.njoh.pulseengine.core.input.InputInternal
import no.njoh.pulseengine.core.scene.SceneManager
import no.njoh.pulseengine.core.scene.SceneManagerInternal
import no.njoh.pulseengine.core.widget.WidgetManager
import no.njoh.pulseengine.core.widget.WidgetManagerInternal
import no.njoh.pulseengine.core.window.Window
import no.njoh.pulseengine.core.window.WindowInternal
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * The public, game facing, interface of the engine.
 */
interface PulseEngine
{
    /** Handles configuration files and engine config properties */
    val config: Configuration

    /** Handles window operations and stores state related to window context */
    val window: Window

    /** Handles all operations related to rendering and graphics */
    val gfx: Graphics

    /** Handles playback of audio sources */
    val audio: Audio

    /** Handles all operations related to mouse, keyboard and gamepad input */
    val input: Input

    /** Handles saving and loading of data and keeps track of common engine metrics */
    val data: Data

    /** Handles console commands, scripts and logging */
    val console: Console

    /** Handles asset loading from disc and stores references to all loaded assets */
    val asset: AssetManager

    /** Handles all operations related to loading, saving, running and accessing a Scene and its data */
    val scene: SceneManager

    /** Handles updating and rendering of all active widgets */
    val widget: WidgetManager

    companion object
    {
        /**
         * Runs a [PulseEngineGame] with the default [PulseEngineImpl] implementation
         */
        fun run(game: KClass<out PulseEngineGame>) =
            PulseEngineImpl().run(game.createInstance())

        /** Holds a global reference to the current engine instance */
        lateinit var INSTANCE: PulseEngine; internal set
    }
}

/**
 * The internal engine interface.
 */
interface PulseEngineInternal : PulseEngine
{
    override val config: ConfigurationInternal
    override val window: WindowInternal
    override val gfx: GraphicsInternal
    override val audio: AudioInternal
    override val input: InputInternal
    override val data: Data
    override val console: ConsoleInternal
    override val asset: AssetManagerInternal
    override val scene: SceneManagerInternal
    override val widget: WidgetManagerInternal
}