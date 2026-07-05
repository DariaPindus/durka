package com.durka.backend.telegram

import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import java.util.concurrent.atomic.AtomicBoolean

/** Loads the TDLight native library exactly once per JVM process. */
object TdlightNativeInit {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            Init.init()
            Log.setLogMessageHandler(1, Slf4JLogMessageHandler())
        }
    }
}
