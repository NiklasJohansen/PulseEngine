package no.njoh.pulseengine.core.scene.interfaces

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonSetter
import org.joml.Vector3f

/**
 * Gives an entity an editable 3D position.
 */
interface Translatable3D
{
    var position: Vector3f

    @JsonAlias("xPos") @JsonSetter("xpos") fun migrateXPos(value: Float) { position.x = value }
    @JsonAlias("yPos") @JsonSetter("ypos") fun migrateYPos(value: Float) { position.y = value }
    @JsonAlias("zPos") @JsonSetter("zpos") fun migrateZPos(value: Float) { position.z = value }
}

/**
 * Gives an entity an editable Euler rotation, expressed in degrees.
 */
interface Rotatable3D
{
    var rotation: Vector3f

    @JsonAlias("xRot") @JsonSetter("xrot") fun migrateXRot(value: Float) { rotation.x = value }
    @JsonAlias("yRot") @JsonSetter("yrot") fun migrateYRot(value: Float) { rotation.y = value }
    @JsonAlias("zRot") @JsonSetter("zrot") fun migrateZRot(value: Float) { rotation.z = value }
}

/**
 * Gives an entity an editable 3D scale.
 */
interface Scalable3D
{
    var scale: Vector3f

    @JsonAlias("xScale") @JsonSetter("xscale") fun migrateXScale(value: Float) { scale.x = value }
    @JsonAlias("yScale") @JsonSetter("yscale") fun migrateYScale(value: Float) { scale.y = value }
    @JsonAlias("zScale") @JsonSetter("zscale") fun migrateZScale(value: Float) { scale.z = value }
}

/**
 * Gives an entity a complete editable 3D transform.
 */
interface Spatial3D : Translatable3D, Scalable3D, Rotatable3D