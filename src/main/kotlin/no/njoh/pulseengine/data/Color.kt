package no.njoh.pulseengine.data

data class Color(
    var red: Float,
    var green: Float,
    var blue: Float,
    var alpha: Float = 1f
) {
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) :
        this(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    companion object
    {
        val BLANK = Color(0f, 0f, 0f, 0f)
    }
}