package com.nonea.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-Kotlin parts of the voice layer. Run with: ./gradlew testDebugUnitTest */
class VoiceTypesTest {
    @Test fun scriptDetection() {
        assertEquals("en", Lang.of("hello there"))
        assertEquals("bn", Lang.of("আমি ভালো আছি"))
        assertNull(Lang.of("123 ..."))
        assertEquals("bn", Lang.fromTag("bn-BD"))
        assertNull(Lang.fromTag("auto"))
    }

    @Test fun autoSplitsByLanguage() {
        val segs = Speakable.segments("Hello there. আমি ভালো আছি।", "auto")
        assertEquals(listOf("en", "bn"), segs.map { it.lang })
    }

    @Test fun forcedLanguageWins() {
        assertEquals(listOf("bn"), Speakable.segments("Hello", "bn-BD").map { it.lang })
    }

    @Test fun codeBlocksAreNotReadOut() {
        val segs = Speakable.segments("Here you go:\n```kotlin\nval x = 1\n```\nDone.", "auto")
        val all = segs.joinToString(" ") { it.text }
        assertTrue(all.contains("The code is on your screen"))
        assertFalse(all.contains("val x"))
    }

    @Test fun markdownAndEmojiAreStripped() {
        val all = Speakable.segments("**Bold** [link](https://example.com) 😀 `code`", "en-US").joinToString(" ") { it.text }
        assertEquals("Bold link code", all.trim())
    }

    @Test fun wakePhraseMatching() {
        assertEquals("open youtube", WakePhrase.match("hey nonea open youtube", "hey nonea"))
        assertEquals("", WakePhrase.match("Hey Nonea", "hey nonea"))
        val slip = WakePhrase.match("Hey, Nonia! what's up", "hey nonea")
        assertNotNull(slip)
        assertTrue(slip!!.startsWith("what"))
        assertNull(WakePhrase.match("hello there", "hey nonea"))
        assertEquals("খোলো ইউটিউব", WakePhrase.match("হ্যালো নোনিয়া খোলো ইউটিউব", "হ্যালো নোনিয়া"))
    }
}
