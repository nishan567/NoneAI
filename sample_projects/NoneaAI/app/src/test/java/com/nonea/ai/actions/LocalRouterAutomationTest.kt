package com.nonea.ai.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRouterAutomationTest {
    private fun a(s: String) = LocalRouter.parse(s)

    @Test fun backHomeRecents() {
        assertEquals(Action("system", "back"), a("Press the Back button"))
        assertEquals(Action("system", "home"), a("press home"))
        assertEquals(Action("system", "recents"), a("tap the recents button"))
    }

    @Test fun scrolling() {
        assertEquals(Action("scroll", "down"), a("Scroll down"))
        assertEquals(Action("scroll", "up"), a("scroll up a bit"))
        assertEquals(Action("scroll", "down"), a("scroll"))
        assertEquals(Action("scroll", "down"), a("নিচে স্ক্রল করো"))
        assertEquals(Action("scroll", "up"), a("upore scroll koro"))
    }

    @Test fun questionsAboutScrollingGoToGemini() {
        assertNull(a("how do I scroll in Excel?"))
        assertNull(a("why does my page scroll by itself"))
    }

    @Test fun tapping() {
        assertEquals(Action("tap", ""), a("Tap the screen."))
        assertEquals(Action("tap_text", "search"), a("tap search"))
        assertEquals(Action("tap_text", "subscribe"), a("click the subscribe button"))
        assertNull(a("tap into your creative side and write me a long story about dragons"))
    }

    @Test fun typingNeedsQuotes() {
        assertEquals(Action("type", "hello world"), a("type \"hello world\""))
        assertNull(a("type of cars in Bangladesh"))
    }

    @Test fun settingsAndApps() {
        assertEquals(Action("settings", ""), a("Open Settings"))
        assertEquals(Action("settings", "wifi"), a("open wi-fi settings"))
        assertEquals(Action("open_app", "youtube"), a("Open YouTube"))
        assertEquals(Action("open_app", "youtube"), a("ইউটিউব খোলো"))
    }

    @Test fun youtubeSearch() {
        assertEquals(Action("youtube_search", ""), a("Search for a video"))
        assertEquals(Action("youtube_search", "funny cats"), a("search for a video about funny cats"))
        assertEquals(Action("youtube_search", "lofi music"), a("search lofi music on youtube"))
        assertEquals(Action("youtube_search", "lofi music"), a("search youtube for lofi music"))
    }

    @Test fun installOnlyOpensTheStore() = assertEquals(Action("play_store", "telegram"), a("install telegram"))

    @Test fun stopWorks() {
        assertEquals(Action("stop_automation"), a("stop"))
        assertEquals(Action("stop_automation"), a("Emergency stop!"))
        assertEquals(Action("stop_automation"), a("থামো"))
        assertTrue(StopCommand.matches("cancel"))
        assertTrue(!StopCommand.matches("please stop being so slow at answering"))
    }

    @Test fun parserKeepsNewTags() {
        val (clean, acts) = ActionParser.extract("On it. [[ACTION:tap_text|Search]] [[ACTION:scroll|down]]")
        assertEquals("On it.", clean)
        assertEquals(listOf(Action("tap_text", "Search"), Action("scroll", "down")), acts)
    }
}
