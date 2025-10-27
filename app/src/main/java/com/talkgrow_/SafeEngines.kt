// SafeEngines.kt
package com.talkgrow_.inference

object SafeEngines {
    inline fun <T> tryCreate(block: () -> T): T? {
        return try { block() } catch (_: Throwable) { null }
    }
}
