package no.njoh.pulseengine.core.shared.utils

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

operator fun Vector2f.component1() = x
operator fun Vector2f.component2() = y

operator fun Vector3f.component1() = x
operator fun Vector3f.component2() = y
operator fun Vector3f.component3() = z

operator fun Vector4f.component1() = x
operator fun Vector4f.component2() = y
operator fun Vector4f.component3() = z
operator fun Vector4f.component4() = w