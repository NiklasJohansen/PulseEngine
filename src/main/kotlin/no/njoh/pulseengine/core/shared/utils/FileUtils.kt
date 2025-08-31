package no.njoh.pulseengine.core.shared.utils

fun openFile(path: String)
{
    val os = System.getProperty("os.name").lowercase()
    val commands = when
    {
        "win" in os -> arrayOf(arrayOf("cmd", "/c", "start", "", path))
        "mac" in os -> arrayOf(arrayOf("open", path))
        else -> arrayOf(
            arrayOf("xdg-open", path),
            arrayOf("gio", "open", path),
            arrayOf("kde-open", path),
            arrayOf("gnome-open", path)
        )
    }
    for (cmd in commands)
    {
        try { ProcessBuilder(*cmd).inheritIO().start()?.let { if (it.isAlive || it.waitFor() == 0) return } }
        catch (_: Exception) { }
    }
}