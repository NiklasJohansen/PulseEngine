package no.njoh.pulseengine.core.shared.utils

import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NativeFileDialog.*

object FileChooser
{
    fun showSaveFileDialog(defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        stackPush().use { stack ->

            val savePath = stack.callocPointer(1)
            val filters = NFDFilterItem.malloc(1)
            filters.get(0).name(stack.UTF8("Pulse Engine Scene")).spec(stack.UTF8("scn"))

            try
            {
                when (NFD_SaveDialog(savePath, filters, defaultPath, null))
                {
                    NFD_OKAY  -> try { onFileChosen(savePath.getStringUTF8(0)) } finally { NFD_FreePath(savePath[0]) }
                    NFD_ERROR -> Logger.error { "FileChooser error: ${NFD_GetError()}" }
                }
            }
            finally { filters.free() }
        }
    }

    fun showOpenFileDialog(defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        stackPush().use { stack ->

            val openPath = stack.callocPointer(1)
            val filters = NFDFilterItem.malloc(1)
            filters.get(0).name(stack.UTF8("Pulse Engine Scene")).spec(stack.UTF8("scn"))

            try
            {
                when (NFD_OpenDialog(openPath, filters, defaultPath))
                {
                    NFD_OKAY  -> try { onFileChosen(openPath.getStringUTF8(0)) } finally { NFD_FreePath(openPath[0]) }
                    NFD_ERROR -> Logger.error { "FileChooser error: ${NFD_GetError()}" }
                }
            }
            finally { filters.free() }
        }
    }
    
    fun showMultipleFileSelectionDialog(defaultPath: String? = null, onFilesChosen: (List<String>) -> Unit)
    {
        stackPush().use { stack ->

            val pathSetPointer = stack.callocPointer(1)
            val filters = NFDFilterItem.malloc(1)
            filters.get(0).name(stack.UTF8("Pulse Engine Scene")).spec(stack.UTF8("scn"))

            try
            {
                when (NFD_OpenDialogMultiple(pathSetPointer, filters, defaultPath))
                {
                    NFD_OKAY -> 
                        {
                        val pathSet = pathSetPointer[0]
                        try
                        {
                            val pathCount = stack.mallocInt(1)
                            if (NFD_PathSet_GetCount(pathSet, pathCount) != NFD_OKAY)
                            {
                                Logger.error { "FileChooser error: ${NFD_GetError()}" }
                                return@use
                            }

                            val paths = ArrayList<String>(pathCount[0])
                            val pathPointer = stack.callocPointer(1)
                            for (index in 0 until pathCount[0])
                            {
                                if (NFD_PathSet_GetPath(pathSet, index, pathPointer) != NFD_OKAY)
                                {
                                    Logger.error { "FileChooser error: ${NFD_GetError()}" }
                                    return@use
                                }

                                try { paths.add(pathPointer.getStringUTF8(0)) }
                                finally
                                {
                                    NFD_PathSet_FreePath(pathPointer[0])
                                    pathPointer.put(0, 0L)
                                }
                            }
                            onFilesChosen(paths)
                        }
                        finally { NFD_PathSet_Free(pathSet) }
                    }
                    NFD_ERROR -> Logger.error { "FileChooser error: ${NFD_GetError()}" }
                }
            }
            finally { filters.free() }
        }
    }
}