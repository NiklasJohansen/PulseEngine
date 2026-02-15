package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.shared.annotations.Prop
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.core.graphics.api.LightList
import no.njoh.pulseengine.modules.scene.systems.WorldLight

class WorldPointLight : SceneEntity(), WorldLight, Named
{
    @Prop(i=0) override var name      = "Light"
    @Prop(i=1)          var color     = Color(1f, 1f, 1f)
    @Prop(i=2,min=0f)   var intensity = 5f
    @Prop(i=3,min=0f)   var radius    = 50f
    @Prop(i=4)          var xPos      = 0f
    @Prop(i=5)          var yPos      = 0f
    @Prop(i=6)          var zPos      = 0f
    
    override fun onRender(engine: PulseEngine, list: LightList)
    {
        list.submitPointLight(xPos, yPos, zPos, radius, color, intensity)
    }
}