package no.njoh.pulseengine.core.asset

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import no.njoh.pulseengine.core.asset.types.Asset

/**
 * Handle to an [Asset] that can be used to reference assets by a fast slot lookup.
 */
class AssetHandle<T : Asset> @JsonCreator(mode = DELEGATING) constructor(name: String = "")
{
    @get:JsonIgnore
    internal var slot = INVALID_ASSET_SLOT

    @get:JsonValue
    var name = name
        set(value)
        {
            if (field != value)
            {
                field = value
                slot = INVALID_ASSET_SLOT
            }
        }

    fun copy() = AssetHandle<T>(name)

    override fun toString() = name
}

internal const val INVALID_ASSET_SLOT = -1