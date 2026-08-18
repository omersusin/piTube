package com.omersusin.pitube.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeSessionStateTest {

    @Test
    fun noteResponseState_reportsLiveSessionAndAdoptsEchoedDataSyncId() {
        var sessionLoggedIn = false
        var healCount = 0
        var heardDataSyncId: String? = null
        val innerTube = InnerTube().apply {
            dataSyncId = null
            sessionStateListener = { sessionLoggedIn = it }
            dataSyncIdListener = {
                healCount++
                heardDataSyncId = it
            }
        }

        innerTube.noteResponseState(
            """
            {
              "trackedParams": [{"key":"logged_in","value":"1"}],
              "responseContext": {
                "mainAppWebResponseContext": {"datasyncId":"abc123","loggedOut":false}
              }
            }
            """.trimIndent(),
        )

        assertTrue(sessionLoggedIn)
        assertEquals(1, healCount)
        assertEquals("abc123", heardDataSyncId)
        assertEquals("abc123", innerTube.dataSyncId)
    }

    @Test
    fun noteResponseState_doesNotAdoptDataSyncIdOnDeadSession() {
        var sessionExpired = true
        var healCount = 0
        val innerTube = InnerTube().apply {
            dataSyncId = "stale"
            sessionStateListener = { sessionExpired = it }
            dataSyncIdListener = { healCount++ }
        }

        innerTube.noteResponseState(
            """{"key":"logged_in","value":"0","datasyncId":"should-not-heal"}""",
        )

        assertFalse(sessionExpired)
        assertEquals(0, healCount)
        assertEquals("stale", innerTube.dataSyncId)
    }

    @Test
    fun noteResponseState_bodyWithoutVerdict_stillAdoptsEchoedDataSyncId() {
        var sessionHeard = false
        var healCount = 0
        val innerTube = InnerTube().apply {
            dataSyncId = null
            sessionStateListener = { sessionHeard = true }
            dataSyncIdListener = { healCount++ }
        }

        innerTube.noteResponseState(
            """{"responseContext":{"mainAppWebResponseContext":{"datasyncId":"xyz","loggedOut":false}}}""",
        )

        assertFalse(sessionHeard)
        assertEquals(1, healCount)
        assertEquals("xyz", innerTube.dataSyncId)
    }

    @Test
    fun noteResponseState_repeatedSameDataSyncId_doesNotRefire() {
        var healCount = 0
        val innerTube = InnerTube().apply {
            dataSyncId = null
            dataSyncIdListener = { healCount++ }
        }
        val body =
            """{"key":"logged_in","value":"1","datasyncId":"abc123"}"""

        innerTube.noteResponseState(body)
        innerTube.noteResponseState(body)

        assertEquals(1, healCount)
        assertEquals("abc123", innerTube.dataSyncId)
    }
}