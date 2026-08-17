package com.omersusin.pitube.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-lifetime scope for work that must survive a ViewModel's death.
 *
 * The watch-history reporter's final `state=ended` beacon runs here: when the
 * player ViewModel is cleared (activity config change, low-memory kill) its
 * own scope is already cancelled, and the beacon would otherwise never fire —
 * leaving "in progress" entries in the official YouTube history that appear
 * late or never commit.
 */
object HistoryReportScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
