package no.njoh.pulseengine.modules.graphics.objects

import no.njoh.pulseengine.util.Logger
import org.lwjgl.opengl.ARBSync.glClientWaitSync
import org.lwjgl.opengl.GL32.*

/**
 * Used for placing fences on the GPU command buffer in order to synchronise buffer access.
 */
data class SyncObj(
    private var sync: Long = -1
) {
    fun waitSync()
    {
        var waits = 0
        while (sync != -1L)
        {
            val result = glClientWaitSync(sync, GL_SYNC_FLUSH_COMMANDS_BIT, 1)
            if (result == GL_ALREADY_SIGNALED || result == GL_CONDITION_SATISFIED)
            {
                if (LOG_SYNC_WAITS && waits > 0)
                    Logger.debug("Sync object: $sync waited $waits times")
                return
            }
            waits++
        }
    }

    fun lockSync()
    {
        if (sync != -1L)
            glDeleteSync(sync)
        sync = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
    }

    companion object
    {
        var LOG_SYNC_WAITS = false
    }
}