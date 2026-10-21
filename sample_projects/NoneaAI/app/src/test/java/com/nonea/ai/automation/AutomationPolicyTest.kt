package com.nonea.ai.automation

import com.nonea.ai.automation.AutomationPolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Safety rules for screen automation. Run with: ./gradlew testDebugUnitTest */
class AutomationPolicyTest {
    private val own = "com.nonea.ai"
    private fun tap(pkg: String? = "com.example.app", label: String? = "Search", pw: Boolean = false, ad: Boolean = false,
                    all: Boolean = false, xy: Boolean = false, n: Int = 1) =
        AutomationPolicy.judgeTap(pkg, own, label, pw, ad, all, xy, n)

    @Test fun plainTapIsAllowed() = assertEquals(Verdict.Allow, tap())

    @Test fun neverActsInsideNonea() = assertTrue(tap(pkg = own) is Verdict.Refuse)

    @Test fun blockedAppsAreRefused() {
        assertTrue(tap(pkg = "com.android.settings") is Verdict.Refuse)
        assertTrue(tap(pkg = "com.android.permissioncontroller") is Verdict.Refuse)
        assertTrue(tap(pkg = "com.google.android.apps.authenticator2") is Verdict.Refuse)
        assertTrue(tap(pkg = "com.android.systemui") is Verdict.Refuse)
        assertTrue(AutomationPolicy.judgeScrollOrType("com.bkash.customerapp", own) is Verdict.Refuse)
    }

    @Test fun unknownPackageIsRefused() = assertTrue(tap(pkg = null) is Verdict.Refuse)

    @Test fun passwordFieldsAreRefused() = assertTrue(tap(pw = true) is Verdict.Refuse)
    @Test fun typingIntoPasswordIsRefused() = assertTrue(AutomationPolicy.judgeScrollOrType("com.example.app", own, isPassword = true) is Verdict.Refuse)

    @Test fun adsAreRefused() = assertTrue(tap(label = "Install now", ad = true) is Verdict.Refuse)

    @Test fun skipAdAsksFirst() = assertTrue(tap(label = "Skip ad", ad = true) is Verdict.Confirm)

    @Test fun sensitiveLabelsAskFirst() {
        listOf("Pay now", "Buy", "Place order", "Send", "Delete", "Install", "Sign in", "Allow", "Confirm payment",
            "পেমেন্ট করুন", "মুছুন", "ইনস্টল").forEach {
            assertTrue("expected confirm for $it", tap(label = it) is Verdict.Confirm)
        }
    }

    @Test fun harmlessLabelsAreNotSensitive() {
        listOf("Search", "Subscriptions tab", "Home", "Next", "Play").forEach { assertFalse(it, AutomationPolicy.isSensitiveLabel(it)) }
    }

    @Test fun coordinateTapsAlwaysAsk() = assertTrue(tap(label = null, xy = true) is Verdict.Confirm)
    @Test fun storeAppAsksOnEveryTap() = assertTrue(tap(pkg = "com.android.vending", label = "Open") is Verdict.Confirm)
    @Test fun ambiguousMatchAsks() = assertTrue(tap(n = 3) is Verdict.Confirm)
    @Test fun confirmAllSettingAsks() = assertTrue(tap(all = true) is Verdict.Confirm)

    @Test fun refusalBeatsConfirmation() = assertTrue(tap(pkg = "com.android.settings", label = "Pay", all = true) is Verdict.Refuse)

    @Test fun adDetection() {
        assertTrue(AutomationPolicy.looksLikeAd(listOf("Ad · Visit site"), emptyList()))
        assertTrue(AutomationPolicy.looksLikeAd(listOf("Sponsored"), emptyList()))
        assertTrue(AutomationPolicy.looksLikeAd(listOf("বিজ্ঞাপন"), emptyList()))
        assertTrue(AutomationPolicy.looksLikeAd(emptyList(), listOf("com.example:id/native_ad_container")))
        assertTrue(AutomationPolicy.looksLikeAd(emptyList(), listOf("com.example:id/ad_view")))
        assertFalse(AutomationPolicy.looksLikeAd(listOf("Add to playlist", "Read more", "Download"), listOf("com.example:id/header", "com.example:id/pad_left")))
    }

    @Test fun onlyPlainWebLinksAreOpened() {
        assertTrue(AutomationPolicy.isSafeWebUrl("https://example.com/a?b=c"))
        assertTrue(AutomationPolicy.isSafeWebUrl("http://example.com"))
        assertFalse(AutomationPolicy.isSafeWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(AutomationPolicy.isSafeWebUrl("javascript:alert(1)"))
        assertFalse(AutomationPolicy.isSafeWebUrl("file:///sdcard/x"))
        assertFalse(AutomationPolicy.isSafeWebUrl("https://a.com with space"))
    }
}
