package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Spatial
import no.njoh.pulseengine.core.scene.interfaces.Renderable
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.scene.interfaces.Initiable
import no.njoh.pulseengine.core.scene.interfaces.Updatable

/**
 * An abstract base class for scene entities with the most common functionality.
 */
abstract class CommonSceneEntity : SceneEntity(), Initiable, Updatable, Renderable, Spatial, Named
{
    @Prop(i = -1)         override var name = ""
    @Prop("Transform", 0) override var x = 0f
    @Prop("Transform", 1) override var y = 0f
    @Prop("Transform", 2) override var z = -0.1f
    @Prop("Transform", 3) override var width = 100f
    @Prop("Transform", 4) override var height = 100f
    @Prop("Transform", 5) override var rotation = 0f

    override fun onCreate() {}
    override fun onStart(engine: PulseEngine) {}
    override fun onUpdate(engine: PulseEngine) {}
    override fun onFixedUpdate(engine: PulseEngine) {}
    override fun onRender(engine: PulseEngine, surface: Surface) {}
}