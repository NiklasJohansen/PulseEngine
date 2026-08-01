package no.njoh.pulseengine.modules.editor

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.camera.Camera
import no.njoh.pulseengine.core.input.FocusArea
import no.njoh.pulseengine.core.scene.SceneEntity

class ViewportContext(
    private val editor: SceneEditor,
    val camera: Camera,
    val focusArea: FocusArea
) {
    val selection     get() = editor.selectedEntities()
    val isGridVisible get() = editor.isGridVisible()
    val iconFontName  get() = editor.uiFactory.style.iconFontName

    fun selectEntities(engine: PulseEngine, entities: List<SceneEntity>) = editor.selectEntities(engine, entities)

    fun clearSelection() = editor.clearViewportSelection()

    fun notifyTransformChanged(engine: PulseEngine, entity: SceneEntity, vararg propertyNames: String) = editor.notifyTransformChanged(engine, entity, propertyNames)

    fun iconCharacter(name: String): String? = editor.uiFactory.style.icons[name]
}