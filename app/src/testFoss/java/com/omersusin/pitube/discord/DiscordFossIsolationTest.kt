package com.omersusin.pitube.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordFossIsolationTest {
    @Test
    fun `foss classpath excludes functional Discord implementation`() {
        val forbiddenClasses = listOf(
            "com.omersusin.pitube.discord.DiscordTokenStore",
            "com.omersusin.pitube.discord.DiscordAuthTokens",
            "com.omersusin.pitube.discord.DiscordPlaybackSource",
            "com.omersusin.pitube.discord.DiscordPresenceCoordinator",
            "com.omersusin.pitube.discord.KizzyDiscordPresenceTransport",
            "com.omersusin.pitube.discord.KizzyGatewayProtocol",
        )

        forbiddenClasses.forEach { className ->
            assertThat(runCatching { Class.forName(className) }.isFailure).isTrue()
        }
    }

    @Test
    fun `foss runtime reports Discord unavailable`() {
        assertThat(DiscordPresenceRuntime.settingsState.value.isAvailable).isFalse()
        assertThat(DiscordPresenceRuntime.settingsState.value.isEnabled).isFalse()
        assertThat(DiscordPresenceRuntime.settingsState.value.summary)
            .isEqualTo(DiscordSettingsSummary.UNAVAILABLE)
    }
}
