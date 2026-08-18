package com.omersusin.pitube

import kotlinx.coroutines.CompletableDeferred

/**
 * Process-lifetime signal that the application's startup session restore has
 * finished (success or failure), so cold-start feed loads can wait for
 * [com.omersusin.pitube.innertube.YouTube.cookie] to be set before deciding
 * whether the request should be signed. Lives at the root package on purpose:
 * [com.omersusin.pitube.data.local.SessionManager] already owns the stored
 * session, and a `SessionManager.restored` reference must not collide with it.
 */
object SessionManager {
    val restored = CompletableDeferred<Boolean>()
}