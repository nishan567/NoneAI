package com.nonea.ai.actions

import com.nonea.ai.apps.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Section 7: YouTube, app launching, media keys, Settings screens and custom voice commands, as understood by the offline router. */
class LocalRouterAppsTest {
    private fun a(s: String, shortcuts: List<Shortcut> = emptyList()) = LocalRouter.parse(s, shortcuts)

    @Test fun youtubeLinksOpenThatVideo() {
        assertEquals(Action("youtube_play", "https://youtu.be/dQw4w9WgXcQ"), a("Open https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Action("youtube_play", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"), a("play https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun otherLinksAreJustLinks() {
        assertEquals(Action("open_url", "https://example.com/page"), a("open https://example.com/page"))
        assertEquals(Action("open_app", "javascript:alert(1)"), a("open javascript:alert(1)"))   // not a link: treated as an app name, found nowhere
    }

    @Test fun youtubeByWords() {
        assertEquals(Action("youtube_play", "lofi beats"), a("play lofi beats on youtube"))
        assertEquals(Action("youtube_play", "cats"), a("watch cats on YouTube"))
        assertEquals(Action("youtube_play", "cats"), a("open a video about cats"))
        assertEquals(Action("youtube_search", "lofi"), a("open youtube and search lofi"))
        assertEquals(Action("youtube_play", "lofi"), a("open YouTube and play lofi"))
        assertEquals(Action("youtube_play", "lofi"), a("ইউটিউবে lofi চালাও"))
    }

    @Test fun youtubeSearchStillWorks() {
        assertEquals(Action("youtube_search", "lofi music"), a("search lofi music on youtube"))
        assertEquals(Action("open_app", "youtube"), a("open youtube"))
    }

    @Test fun mediaKeys() {
        assertEquals(Action("media", "pause"), a("Pause"))
        assertEquals(Action("media", "play"), a("resume"))
        assertEquals(Action("media", "next"), a("next song"))
        assertEquals(Action("media", "previous"), a("previous track"))
        assertEquals(Action("media", "stop"), a("stop music"))
        assertEquals(Action("media", "pause"), a("গান থামাও"))
    }

    @Test fun bareStopIsStillTheEmergencyStop() {
        assertEquals(Action("stop_automation"), a("stop"))
        assertEquals(Action("stop_automation"), a("stop", listOf(Shortcut("stop", "show_apps"))))
    }

    @Test fun ordinarySentencesStillGoToGemini() {
        assertNull(a("how do I pause a video?"))
        assertNull(a("pause the meeting notes"))
        assertNull(a("play lofi beats"))
    }

    @Test fun appListAndInfo() {
        assertEquals(Action("show_apps"), a("show my apps"))
        assertEquals(Action("show_apps"), a("app launcher"))
        assertEquals(Action("show_apps"), a("open apps"))
        assertEquals(Action("appinfo", "chrome"), a("show chrome app info"))
        assertEquals(Action("appinfo", "whatsapp"), a("app info for whatsapp"))
    }

    @Test fun systemScreens() {
        assertEquals(Action("settings", "bluetooth"), a("open bluetooth settings"))
        assertEquals(Action("settings", "date"), a("open date and time settings"))
        assertEquals(Action("settings", "airplane"), a("go to flight mode settings"))
        assertEquals(Action("settings", "apps"), a("open apps settings"))
        assertEquals(Action("settings", "wifi"), a("open wi-fi settings"))
        assertEquals(Action("settings", ""), a("open settings"))
        // not a system page: stays an app name
        assertEquals(Action("open_app", "chrome settings"), a("open chrome settings"))
    }

    @Test fun builtInAliases() {
        assertEquals(Action("open_app", "facebook"), a("open fb"))
        assertEquals(Action("open_app", "youtube"), a("open yt"))
        assertEquals(Action("open_app", "youtube"), a("ইউটিউব খোলো"))
    }

    @Test fun customVoiceCommands() {
        val sc = listOf(Shortcut("Movie Night", "open_app", "netflix"), Shortcut("my tunes", "youtube_search", "lofi"))
        assertEquals(Action("open_app", "netflix"), a("movie night", sc))
        assertEquals(Action("open_app", "netflix"), a("Hey Nonea, movie night!", sc))
        assertEquals(Action("youtube_search", "lofi"), a("my tunes", sc))
        assertNull(a("movie night tomorrow", sc))
        // a user command may override a built-in phrase
        assertEquals(Action("show_apps"), a("torch", listOf(Shortcut("torch", "show_apps"))))
    }
}
