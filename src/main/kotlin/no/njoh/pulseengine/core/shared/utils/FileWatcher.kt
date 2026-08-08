package no.njoh.pulseengine.core.shared.utils

import gnu.trove.map.hash.TObjectLongHashMap
import no.njoh.pulseengine.core.shared.utils.Extensions.anyMatches
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
     * @param intervalMillis The number of milliseconds between each file is checked. Minimum 100 ms.
     * @param callback The callback lambda triggered when a file changes.
     */
    fun setOnFileChanged(
        path: String,
        fileTypes: List<String> = emptyList(),
        maxSearchDepth: Int = 5,
        intervalMillis: Int = 5_000,
        callback: (filePath: String) -> Unit
    ) {
        watcherTask.watchers.add(Watcher(path, fileTypes, maxSearchDepth, intervalMillis, callback))
    }

    /**
     * Shuts down the watcher thread.
     */
    fun shutdown()
    {
        watcherTask.running = false
    }

    private inline fun forEachFile(path: String, fileTypes: List<String>, maxDepth: Int, action: (File) -> Unit)
    {
        val file = File(path)
        return when
        {
            file.isFile -> action(file)
            file.isDirectory -> file
                .walkTopDown()
                .maxDepth(maxDepth)
                .forEach { f -> if (f.isFile && (fileTypes.isEmpty() || fileTypes.anyMatches { f.name.endsWith(it) } )) action(f) }
            else -> {}
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
                watchers.forEachFiltered({ it.lastCheckTimeMillis + it.checkIntervalMillis < now })
                {
                    forEachFile(it.path, it.fileTypes, it.maxSearchDepth) { file ->

                        if (it.lastModifiedTimes[file.path] != file.lastModified())
                        {
                            it.lastModifiedTimes.put(file.path, file.lastModified())
                            it.onFileChanged(file.absolutePath.replace("\\", "/"))
                        }
                    }
                    it.lastCheckTimeMillis = now
                }
                Thread.sleep(100)
            }
        }
    }

    private data class Watcher(
        val path: String,
        val fileTypes: List<String>,
        val maxSearchDepth: Int,
        val checkIntervalMillis: Int,
        val onFileChanged: (filePath: String) -> Unit,
    ) {
        var lastCheckTimeMillis = 0L
        val lastModifiedTimes = TObjectLongHashMap<String>()

        init { forEachFile(path, fileTypes, maxSearchDepth) { lastModifiedTimes.put(it.path, it.lastModified()) } }
    }
}