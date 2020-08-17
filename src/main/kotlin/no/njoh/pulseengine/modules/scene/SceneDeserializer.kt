package no.njoh.pulseengine.modules.scene

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import no.njoh.pulseengine.util.Logger

internal class SceneDeserializer : StdDeserializer<Scene>(Scene::class.java)
{
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Scene
    {
        val scenePropName = Scene::name.name
        val layerPropName = Scene::layers.name
        val entitiesPropName = SceneLayer::entities.name
        val layerNamePropName = SceneLayer::name.name
        val typePropName = SceneEntity::className.name
        val sceneLayers = mutableListOf<SceneLayer>()
        val tree = parser.readValueAsTree<ObjectNode>()

        val sceneName = if(tree.has(scenePropName)) tree.get(scenePropName).textValue() else "scene"

        if (!tree.has(layerPropName))
            return Scene(sceneName, mutableListOf())

        for (layerNode in tree.get(layerPropName))
        {
            if (!layerNode.has(entitiesPropName))
                continue

            val layerName = layerNode.get(layerNamePropName).textValue()
            val sceneEntities = mutableListOf<SceneEntity>()
            for (entityNode in layerNode.get(entitiesPropName))
            {
                val typeString = entityNode.get(typePropName).textValue()
                SceneEntity.REGISTERED_TYPES
                    .find { it.simpleName == typeString }
                    .also { if (it == null) Logger.error("Scene entity of type: $typeString has not been registered.") }
                    ?.let { type ->
                        val entityParser = entityNode.traverse()
                        entityParser.nextToken()
                        runCatching { context.readValue(entityParser, type.java) }
                            .onSuccess { sceneEntities.add(it) }
                            .onFailure { Logger.error("Failed to deserialize scene entity of type: $typeString, reason: ${it.message}") }
                    }
            }
            sceneLayers.add(SceneLayer(layerName, sceneEntities))
        }

        return Scene(sceneName, sceneLayers)
    }
}