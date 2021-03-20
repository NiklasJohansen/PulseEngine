package no.njoh.pulseengine.modules.scene.systems

import no.njoh.pulseengine.PulseEngine
import no.njoh.pulseengine.modules.scene.Scene

interface SceneSystem
{
    /**
     * Runs one time when the scene starts
     */
    fun onStart(scene: Scene)

    /**
     * Runs at a fixed tick rate independent of frame rate
     */
    fun onFixedUpdate(scene: Scene, engine: PulseEngine)

    /**
     * Runs one time every frame
     */
    fun onUpdate(scene: Scene, engine: PulseEngine)

    /**
     * Runs one time every frame
     */
    fun onRender(scene: Scene, engine: PulseEngine)
}