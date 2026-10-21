package com.nonea.ai.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun watchLink() {
        val t = YouTubeLinks.parse("https://www.youtube.com/watch?v=$id")!!
        assertEquals(YouTubeLinks.Target(YouTubeLinks.Kind.VIDEO, id), t)
        assertEquals("https://www.youtube.com/watch?v=$id", t.url)
    }

    @Test fun shortLinkWithStartTime() {
        assertEquals(90, YouTubeLinks.parse("https://youtu.be/$id?t=90")!!.startSeconds)
        assertEquals(90, YouTubeLinks.parse("https://youtu.be/$id?t=1m30s")!!.startSeconds)
        assertEquals("https://www.youtube.com/watch?v=$id&t=90s", YouTubeLinks.parse("youtu.be/$id?t=90")!!.url)
    }

    @Test fun shortsPlaylistLiveEmbed() {
        assertEquals(YouTubeLinks.Kind.SHORT, YouTubeLinks.parse("https://m.youtube.com/shorts/abcdefghijk")!!.kind)
        assertEquals("https://www.youtube.com/playlist?list=PLabcdefghijklmnop",
            YouTubeLinks.parse("https://www.youtube.com/playlist?list=PLabcdefghijklmnop")!!.url)
        assertEquals(id, YouTubeLinks.parse("https://www.youtube.com/live/$id")!!.id)
        assertEquals(id, YouTubeLinks.parse("https://www.youtube.com/embed/$id")!!.id)
    }

    @Test fun refusesWhatItCannotValidate() {
        assertNull(YouTubeLinks.parse("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeLinks.parse("https://www.youtube.com/@somechannel"))
        assertNull(YouTubeLinks.parse("https://music.youtube.com/watch?v=$id"))       // different app
        assertNull(YouTubeLinks.parse("https://notyoutube.com/watch?v=$id"))
        assertNull(YouTubeLinks.parse("http://evil.example/?u=youtube.com/watch?v=$id")) // a link inside another link
        assertNull(YouTubeLinks.parse("just some words"))
    }

    @Test fun neverPassesTheOriginalUrlThrough() {
        val t = YouTubeLinks.parse("https://www.youtube.com/watch?v=$id&list=x&autoplay=1&redirect=http://evil.example")!!
        assertEquals("https://www.youtube.com/watch?v=$id", t.url)
    }

    @Test fun findUrlKeepsCaseAndTrimsPunctuation() {
        assertEquals("https://youtu.be/$id", YouTubeLinks.findUrl("open (https://youtu.be/$id), please"))
        assertEquals("https://youtu.be/$id", YouTubeLinks.findUrl("watch https://youtu.be/$id."))
    }

    @Test fun startTimes() {
        assertEquals(3723, YouTubeLinks.parseStart("1h2m3s"))
        assertEquals(45, YouTubeLinks.parseStart("45s"))
        assertNull(YouTubeLinks.parseStart("abc"))
        assertNull(YouTubeLinks.parseStart("0"))
    }

    @Test fun searchUrlIsEncoded() {
        assertEquals("https://www.youtube.com/results?search_query=lofi%20music", YouTubeLinks.searchUrl(" lofi music "))
        assertEquals("https://www.youtube.com/results?search_query=a%26b%3Dc", YouTubeLinks.searchUrl("a&b=c"))
        assertTrue(YouTubeLinks.searchUrl("গান").startsWith("https://www.youtube.com/results?search_query=%E0%A6"))
    }
}

class AppNamesTest {
    private fun app(label: String, pkg: String) = AppEntry(label, pkg)
    private val yt = app("YouTube", "com.google.android.youtube")
    private val ytm = app("YouTube Music", "com.google.android.apps.youtube.music")
    private val wa = app("WhatsApp", "com.whatsapp")
    private val fb = app("Facebook", "com.facebook.katana")
    private val calc = app("Calculator", "com.android.calculator2")
    private val cal = app("Calendar", "com.google.android.calendar")
    private val cam = app("Camera", "com.android.camera")
    private val installed = listOf(yt, ytm, wa, fb, calc, cal, cam)

    private fun r(q: String, apps: List<AppEntry> = installed, aliases: List<AppAlias> = emptyList()) = AppNames.resolve(q, apps, aliases)

    @Test fun knownNamesAndBuiltInAliases() {
        assertEquals(Resolution.Found(yt), r("youtube"))
        assertEquals(Resolution.Found(yt), r("YouTube"))
        assertEquals(Resolution.Found(yt), r("yt"))
        assertEquals(Resolution.Found(yt), r("ইউটিউব"))
        assertEquals(Resolution.Found(ytm), r("youtube music"))
        assertEquals(Resolution.Found(wa), r("whats app"))
        assertEquals(Resolution.Found(fb), r("fb"))
    }

    @Test fun aWellKnownAppThatIsMissingIsNotReplacedByALookAlike() {
        assertEquals(Resolution.NotInstalled("youtube"), r("youtube", apps = listOf(ytm, wa)))
    }

    @Test fun userAliasesWinAndPointAtPackages() {
        assertEquals(Resolution.Found(wa), r("work chat", aliases = listOf(AppAlias("Work Chat", "com.whatsapp"))))
        assertEquals(Resolution.Found(wa), r("fb", aliases = listOf(AppAlias("fb", "com.whatsapp"))))
        assertEquals(Resolution.NotInstalled("gone"), r("gone", aliases = listOf(AppAlias("gone", "com.not.installed"))))
    }

    @Test fun packageNameAndLabelMatching() {
        assertEquals(Resolution.Found(wa), r("com.whatsapp"))
        assertEquals(Resolution.Found(calc), r("calc"))
        assertEquals(Resolution.Found(cam), r("CAMERA"))
    }

    @Test fun ambiguousAndMissing() {
        val amb = r("ca") as Resolution.Ambiguous
        assertEquals(setOf(calc, cal, cam), amb.options.toSet())
        assertEquals(Resolution.NotInstalled("zzzz"), r("zzzz"))
        assertEquals(Resolution.NotInstalled("c"), r("c"))   // one letter is never a match
        assertEquals(Resolution.NotInstalled(""), r("  "))
    }

    @Test fun canonicalNames() {
        assertEquals("youtube", AppNames.canonical("ইউটিউব"))
        assertEquals("facebook", AppNames.canonical(" FB "))
        assertEquals("Spotify Lite", AppNames.canonical("Spotify Lite"))
    }

    @Test fun settingsPages() {
        assertEquals("wifi", SettingsPages.normalize("Wi-Fi"))
        assertEquals("wifi", SettingsPages.normalize("wi fi"))
        assertEquals("airplane", SettingsPages.normalize("flight mode"))
        assertEquals("date", SettingsPages.normalize("date and time"))
        assertEquals("", SettingsPages.normalize("settings"))
        assertNull(SettingsPages.normalize("banana"))
    }
}

class MediaCommandsTest {
    @Test fun recognised() {
        assertEquals("pause", MediaCommands.parse("pause"))
        assertEquals("pause", MediaCommands.parse("pause the music"))
        assertEquals("play", MediaCommands.parse("resume"))
        assertEquals("play", MediaCommands.parse("play"))
        assertEquals("play", MediaCommands.parse("play music"))
        assertEquals("next", MediaCommands.parse("next song"))
        assertEquals("next", MediaCommands.parse("skip this track"))
        assertEquals("previous", MediaCommands.parse("previous track"))
        assertEquals("stop", MediaCommands.parse("stop the video"))
        assertEquals("toggle", MediaCommands.parse("play/pause"))
        assertEquals("pause", MediaCommands.parse("গান থামাও"))
        assertEquals("play", MediaCommands.parse("গান বাজাও"))
        assertEquals("next", MediaCommands.parse("পরের গান"))
        assertEquals("pause", MediaCommands.parse("gan thamao"))
    }

    @Test fun ordinarySentencesAreNotMediaCommands() {
        assertNull(MediaCommands.parse("stop"))                       // emergency stop, not a media key
        assertNull(MediaCommands.parse("next"))
        assertNull(MediaCommands.parse("pause the meeting notes"))
        assertNull(MediaCommands.parse("play lofi beats"))
        assertNull(MediaCommands.parse("how do i pause a video"))
    }
}

class ShortcutsTest {
    @Test fun validation() {
        assertNull(Shortcuts.validate(Shortcut("movie night", "open_app", "netflix")))
        assertNull(Shortcuts.validate(Shortcut("pause it", "media", "pause")))
        assertNull(Shortcuts.validate(Shortcut("my apps", "show_apps")))
        assertNull(Shortcuts.validate(Shortcut("net", "settings", "wifi")))
        assertNull(Shortcuts.validate(Shortcut("main", "settings", "")))
        assertEquals(Problem.PHRASE_EMPTY, Shortcuts.validate(Shortcut("  ", "open_app", "x")))
        assertEquals(Problem.PHRASE_RESERVED, Shortcuts.validate(Shortcut("Stop", "open_app", "x")))
        assertEquals(Problem.PHRASE_RESERVED, Shortcuts.validate(Shortcut("emergency stop", "open_app", "x")))
        assertEquals(Problem.PHRASE_LONG, Shortcuts.validate(Shortcut("a".repeat(61), "open_app", "x")))
        assertEquals(Problem.TYPE_NOT_ALLOWED, Shortcuts.validate(Shortcut("hi", "call", "mom")))
        assertEquals(Problem.TYPE_NOT_ALLOWED, Shortcuts.validate(Shortcut("hi", "tap_text", "buy")))
        assertEquals(Problem.TYPE_NOT_ALLOWED, Shortcuts.validate(Shortcut("hi", "sms", "mom;hi")))
        assertEquals(Problem.ARG_REQUIRED, Shortcuts.validate(Shortcut("hi", "open_app", " ")))
        assertEquals(Problem.MEDIA_UNKNOWN, Shortcuts.validate(Shortcut("hi", "media", "rewind")))
        assertEquals(Problem.PAGE_UNKNOWN, Shortcuts.validate(Shortcut("hi", "settings", "banana")))
        assertEquals(Problem.URL_UNSAFE, Shortcuts.validate(Shortcut("hi", "open_url", "javascript:alert(1)")))
        assertEquals(Problem.URL_UNSAFE, Shortcuts.validate(Shortcut("hi", "open_url", "intent://x#Intent;end")))
        assertNull(Shortcuts.validate(Shortcut("hi", "open_url", "https://example.com/a?b=c")))
    }

    private val list = listOf(Shortcut("Movie Night", "open_app", "netflix"), Shortcut("my tunes", "youtube_search", "lofi"))

    @Test fun matchingIgnoresCasePunctuationAndAddressing() {
        assertEquals("netflix", Shortcuts.match("movie night", list)!!.arg)
        assertEquals("netflix", Shortcuts.match("Movie night!", list)!!.arg)
        assertEquals("netflix", Shortcuts.match("hey nonea, movie night", list)!!.arg)
        assertEquals("netflix", Shortcuts.match("nonea please movie night", list)!!.arg)
        assertEquals("youtube_search", Shortcuts.match("my tunes", list)!!.type)
        assertNull(Shortcuts.match("movie night tomorrow", list))     // whole phrase only
        assertNull(Shortcuts.match("movie", list))
        assertNull(Shortcuts.match("movie night", emptyList()))
    }

    @Test fun invalidStoredShortcutsNeverMatch() {
        assertNull(Shortcuts.match("call mom", listOf(Shortcut("call mom", "call", "mom"))))
    }

    @Test fun upsertReplacesSamePhrase() {
        val next = Shortcuts.upsert(list, Shortcut(" movie night ", "open_app", "youtube"))
        assertEquals(2, next.size)
        assertEquals("youtube", next.first { it.phrase == "movie night" }.arg)
    }
}

class ConfigCodecTest {
    @Test fun aliasesRoundTrip() {
        val l = listOf(AppAlias("fb", "com.facebook.katana"), AppAlias("ইউটিউব ২", "com.google.android.youtube"))
        assertEquals(l, ConfigCodec.decodeAliases(ConfigCodec.encodeAliases(l)))
    }

    @Test fun shortcutsRoundTripWithAwkwardCharacters() {
        val l = listOf(Shortcut("a b", "youtube_search", "tab\there\nnew line and back\\slash"), Shortcut("x", "show_apps", ""))
        assertEquals(l, ConfigCodec.decodeShortcuts(ConfigCodec.encodeShortcuts(l)))
    }

    @Test fun damagedOrUnsafeRowsAreDropped() {
        assertEquals(emptyList<Shortcut>(), ConfigCodec.decodeShortcuts(""))
        assertEquals(emptyList<Shortcut>(), ConfigCodec.decodeShortcuts("only-one-field"))
        assertEquals(emptyList<Shortcut>(), ConfigCodec.decodeShortcuts("call mom\tcall\tmom"))
        assertEquals(emptyList<AppAlias>(), ConfigCodec.decodeAliases("fb\tnot a package"))
    }
}
