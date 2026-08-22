package com.omersusin.pitube.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM-side logic tests. NOTE: these run on java.util.regex, NOT Android ICU —
 * they cannot catch ICU-specific pattern-syntax failures (the 2.3.0 crash).
 * They guard stripping BEHAVIOR only. ICU safety is ensured by construction:
 * patterns use only the two proven-safe shapes documented on the object.
 */
class TitleDecorationStripperTest {

    @Test
    fun `stripDecorations removes keyword decorations`() {
        assertThat(TitleDecorationStripper.stripDecorations("Song Name (Official Video)"))
            .isEqualTo("Song Name")
        assertThat(TitleDecorationStripper.stripDecorations("[HD] Song Name [Lyrics]"))
            .isEqualTo("Song Name")
        assertThat(TitleDecorationStripper.stripDecorations("Song Name (official music video)"))
            .isEqualTo("Song Name")
    }

    @Test
    fun `stripDecorations is repeated until stable`() {
        assertThat(TitleDecorationStripper.stripDecorations("(4K) [MV] Artist - Song (Official)"))
            .isEqualTo("Artist - Song")
    }

    @Test
    fun `stripDecorations keeps non-keyword brackets`() {
        // Real lyric content must survive: only decoration keywords are removed.
        assertThat(TitleDecorationStripper.stripDecorations("La la la (x2)"))
            .isEqualTo("La la la (x2)")
        assertThat(TitleDecorationStripper.stripDecorations("Live and Learn"))
            .isEqualTo("Live and Learn")
    }

    @Test
    fun `stripAll removes every bracketed segment`() {
        assertThat(TitleDecorationStripper.stripAll("Song Name (Official Video) [HD]"))
            .isEqualTo("Song Name")
        assertThat(TitleDecorationStripper.stripAll("Anything (Live 2019) else"))
            .isEqualTo("Anything else")
    }

    @Test
    fun `blank input stays blank`() {
        assertThat(TitleDecorationStripper.stripDecorations("   ")).isEmpty()
        assertThat(TitleDecorationStripper.stripAll("")).isEmpty()
    }
}
