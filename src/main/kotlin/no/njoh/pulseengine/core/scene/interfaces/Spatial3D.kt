package no.njoh.pulseengine.core.scene.interfaces

/**
 * Gives an entity an editable 3D position.
 */
interface Translatable3D
{
    var xPos: Float
    var yPos: Float
    var zPos: Float
}

/** 
 * Gives an entity an editable 3D position and Euler rotation, expressed in degrees. 
 */
interface Rotatable3D
{
    var xRot: Float
    var yRot: Float
    var zRot: Float
}

/**
 * Gives an entity an editable 3D scale.
 */
interface Scalable3D
{
    var xScale: Float
    var yScale: Float
    var zScale: Float
}

/** 
 * Gives an entity a complete editable 3D transform. 
 */
interface Spatial3D : Translatable3D, Scalable3D, Rotatable3D