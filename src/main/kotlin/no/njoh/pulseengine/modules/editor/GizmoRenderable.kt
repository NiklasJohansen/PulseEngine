package no.njoh.pulseengine.modules.editor

import com.fasterxml.jackson.annotation.JsonIgnore
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.interfaces.Translatable3D

interface GizmoRenderable : Translatable3D
{
    @get:JsonIgnore
    val gizmoMarkerRadius: Float get() = 7f

    fun renderGizmo(surface: Surface, context: GizmoRenderContext)
}