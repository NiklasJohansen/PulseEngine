package no.njoh.pulseengine.modules.scene.entities

import no.njoh.pulseengine.core.scene.interfaces.Named
import no.njoh.pulseengine.core.scene.SceneEntity
import no.njoh.pulseengine.core.shared.annotations.ScnIcon

@ScnIcon("FOLDER")
class Folder : SceneEntity(), Named
{
    override var name = "Folder"

    init { setNot(DISCOVERABLE) }
}