package engine.util

import engine.modules.DataInterface

fun Float.interpolateFrom(lastState: Float): Float
{
    val i = DataInterface.INSTANCE.interpolation
    return this * i + lastState * (1f - i)
}
