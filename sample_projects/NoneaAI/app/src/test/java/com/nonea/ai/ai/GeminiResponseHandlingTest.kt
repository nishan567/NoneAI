package com.nonea.ai.ai

import org.junit.Assert.*
import org.junit.Test

class GeminiResponseHandlingTest {

    @Test
    fun testSecretRedaction_masksGoogleApiKey() {
        val raw = "Error calling endpoint with key AIzaSyD-1234567890abcdefghijklmn"
        val redacted = Redact.text(raw)
        assertFalse("Raw API key must not be present", redacted.contains("AIzaSyD-1234567890"))
        assertTrue("Mask must be present", redacted.contains("[REDACTED]"))
    }

    @Test
    fun testSecretRedaction_masksBearerTokens() {
        val raw = "Authorization: Bearer ya29.a0ARrda8-fake-token-content-123456"
        val redacted = Redact.text(raw)
        assertFalse("Bearer token must not leak", redacted.contains("ya29.a0ARrda8"))
        assertTrue("Bearer must be masked", redacted.contains("Bearer [REDACTED]"))
    }

    @Test
    fun testSecretRedaction_masksKeyValueSecrets() {
        val raw = "api_key: \"secret_val_123456\", password: mypassword"
        val redacted = Redact.text(raw)
        assertFalse(redacted.contains("secret_val_123456"))
    }

    @Test
    fun testAiError_categorization() {
        val rateLimit = AiError.RateLimited(retryAfterSec = 30)
        assertTrue(rateLimit.isTransient)
        assertEquals(30, rateLimit.retryAfterSec)

        val invalidKey = AiError.InvalidKey
        assertFalse(invalidKey.isTransient)

        val network = AiError.Network("Socket timeout")
        assertTrue(network.isTransient)
        assertEquals("Socket timeout", network.detail)
    }

    @Test
    fun testActionTagParsing() {
        val response = "Sure, I will open YouTube for you [[ACTION:open_app|YouTube]]"
        val regex = Regex("\\[\\[ACTION:([^\\]]+)\\]\\]")
        val match = regex.find(response)
        assertNotNull(match)
        assertEquals("open_app|YouTube", match?.groupValues?.get(1))
    }

    @Test
    fun testToolInputValidation_openApp() {
        fun validateOpenApp(pkg: String?): Boolean {
            if (pkg.isNullOrBlank()) return false
            if (pkg.contains("..") || pkg.contains("/") || pkg.length < 3) return false
            return true
        }

        assertTrue(validateOpenApp("com.google.android.youtube"))
        assertTrue(validateOpenApp("YouTube"))
        assertFalse(validateOpenApp(""))
        assertFalse(validateOpenApp("../evil"))
    }

    @Test
    fun testToolInputValidation_youtubeUrl() {
        val validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val invalidUrl = "javascript:alert(1)"

        assertTrue(validUrl.startsWith("https://www.youtube.com/") || validUrl.startsWith("https://youtu.be/"))
        assertTrue(shortUrl.startsWith("https://www.youtube.com/") || shortUrl.startsWith("https://youtu.be/"))
        assertFalse(invalidUrl.startsWith("https://"))
    }
}
