package no.njoh.pulseengine.util

import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.nfd.NFDPathSet
import org.lwjgl.util.nfd.NativeFileDialog.*

object FileChooser
{
    fun showSaveFileDialog(fileTypes: String, defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        val savePath = MemoryUtil.memAllocPointer(1)
        try
        {
            when (NFD_SaveDialog(fileTypes, defaultPath, savePath))
            {
                NFD_OKAY ->
                {
                    onFileChosen(savePath.getStringUTF8(0))
                    nNFD_Free(savePath[0])
                }
                NFD_CANCEL -> { }
                else -> Logger.error("FileChooser error: ${NFD_GetError()}")
            }
        }
        finally { MemoryUtil.memFree(savePath) }
    }

    fun showFileSelectionDialog(fileTypes: String, defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        val openPath = MemoryUtil.memAllocPointer(1)
        try
        {
            when (NFD_OpenDialog(fileTypes, defaultPath, openPath))
            {
                NFD_OKAY ->
                {
                    onFileChosen(openPath.getStringUTF8(0))
                    nNFD_Free(openPath[0])
                }
                NFD_CANCEL -> { }
                else -> Logger.error("FileChooser error: ${NFD_GetError()}")
            }
        }
        finally { MemoryUtil.memFree(openPath) }
    }

    fun showMultiFileSelectionDialog(fileTypes: String, defaultPath: String? = null, onFilesChosen: (List<String>) -> Unit)
    {
        NFDPathSet.calloc().use { pathSet ->
            when (NFD_OpenDialogMultiple(fileTypes, defaultPath, pathSet))
            {
                NFD_OKAY ->
                {
                    LongRange(0, NFD_PathSet_GetCount(pathSet))
                        .mapNotNull { NFD_PathSet_GetPath(pathSet, it) }
                        .let {
                            onFilesChosen(it)
                            NFD_PathSet_Free(pathSet)
                        }
                }
                NFD_CANCEL -> { }
                else -> System.err.format("Error: %s\n", NFD_GetError())
            }
        }
    }
}