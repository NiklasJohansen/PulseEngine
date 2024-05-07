package no.njoh.pulseengine.core.shared.utils

import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NativeFileDialog.*

object FileChooser
{
    fun showSaveFileDialog(defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        stackPush().use { stack ->

            val savePath = stack.mallocPointer(1)
            val filters = NFDFilterItem.malloc(1)
            filters.get(0).name(stack.UTF8("Pulse Engine Scene")).spec(stack.UTF8("scn"))

            try
            {
                when (NFD_SaveDialog(savePath, filters, defaultPath, null))
                {
                    NFD_OKAY   -> onFileChosen(savePath.getStringUTF8(0))
                    NFD_CANCEL -> {}
                    NFD_ERROR  -> Logger.error("FileChooser error: ${NFD_GetError()}")
                }
            }
            finally
            {
                NFD_FreePath(savePath[0])
                filters.free()
            }
        }
    }

    fun showFileSelectionDialog(defaultPath: String? = null, onFileChosen: (String) -> Unit)
    {
        stackPush().use { stack ->

            val openPath = stack.mallocPointer(1)
            val filters = NFDFilterItem.malloc(1)
            filters.get(0).name(stack.UTF8("Pulse Engine Scene")).spec(stack.UTF8("scn"))

            try
            {
                when (NFD_OpenDialog(openPath, filters, defaultPath))
                {
                    NFD_OKAY   -> onFileChosen(openPath.getStringUTF8(0))
                    NFD_CANCEL -> {}
                    else       -> Logger.error("FileChooser error: ${NFD_GetError()}")
                }
            }
            finally
            {
                NFD_FreePath(openPath[0])
                filters.free()
            }
        }
    }
}