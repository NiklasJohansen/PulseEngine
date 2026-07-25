package no.njoh.pulseengine.core.scene

/**
 * Determines what entities should be selected from a [Scene].
 */
sealed interface SceneEntityFilter
{
    /** 
     * Selects every entity in the source scene. 
     */
    data object All : SceneEntityFilter

    /** 
     * Selects the entity with [id]. 
     */
    data class Id(val id: Long) : SceneEntityFilter

    /** 
     * Selects every named entity whose name matches [name]. 
     */
    data class Name(val name: String) : SceneEntityFilter

    /**
     * Selects the given [entities].
     * Every entity must be the same instance held by the source scene.
     */
    data class Entities(val entities: List<SceneEntity>) : SceneEntityFilter
}