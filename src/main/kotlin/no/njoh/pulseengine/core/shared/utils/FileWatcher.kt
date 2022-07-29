package no.njoh.pulseengine.core.shared.utils

import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFast
import no.njoh.pulseengine.core.shared.utils.Extensions.forEachFiltered
import java.io.File

/**
 * Used to get notified when files on disk are changed.
 */
object FileWatcher
{
    private val watcherTask by lazy { WatcherTask().also { Thread(it, "FileWatcherTask").start() } }

    /**
     * Sets a callback that is triggered for each file that changes in the given path.
     *
     * @param path The absolute path of the file or directory to watch for file changes.
     * @param fileTypes The file types/extensions for the files being watched.
     * @param maxSearchDepth Max amount of directories to recursively search in.
     * @param intervalInSeconds The number of seconds between each file is checked. Minimum 1 second.
     * @param callback The callback lambda triggered when a file changes.
     */
    fun setOnFileChanged(
        path: String,
        fileTypes: List<String> = emptyList(),
        maxSearchDepth: Int = 5,
        intervalInSeconds: Int = 10,
        callback: (fileName: String) -> Unit
    ) {
        watcherTask.watchers.add(Watcher(path, fileTypes, maxSearchDepth, intervalInSeconds, callback))
    }

    /**
     * Shuts down the watcher thread.
     */
    fun shutdown()
    {
        watcherTask.running = false
    }

    private fun getFiles(path: String, fileTypes: List<String>, maxDepth: Int): List<File>
    {
        val file = File(path)
        return when
        {
            file.isFile -> listOf(file)
            file.isDirectory -> file
                .walkTopDown()
                .maxDepth(maxDepth)
                .filter { f -> f.isFile && (fileTypes.isEmpty() || fileTypes.any { f.name.endsWith(it) } ) }
                .toList()
            else -> emptyList()
        }
    }

    private class WatcherTask: Runnable
    {
        var running = true
        var watchers = mutableListOf<Watcher>()

        override fun run()
        {
            while (running)
            {
                val now = System.currentTimeMillis()
                watchers.forEachFiltered({ it.lastCheckTimeMillis + it.checkIntervalSeconds * 1000 < now })
                {
                    getFiles(it.path, it.fileTypes, it.maxSearchDepth).forEachFast { file ->
                        if (it.lastModifiedTimes[file] != file.lastModified())
                        {
                            it.lastModifiedTimes[file] = file.lastModified()
                            it.onFileChanged(file.absolutePath.replace("\\", "/"))
                        }
                    }
                    it.lastCheckTimeMillis = now
                }
                Thread.sleep(1000)
            }
        }
    }

    private data class Watcher(
        val path: String,
        val fileTypes: List<String>,
        val maxSearchDepth: Int,
        val checkIntervalSeconds: Int,
        val onFileChanged: (fileName: String) -> Unit,
    ) {
        var lastCheckTimeMillis: Long = 0
        val lastModifiedTimes = getFiles(path, fileTypes, maxSearchDepth)
            .associateWith { it.lastModified() }
            .toMutableMap()
    }
}