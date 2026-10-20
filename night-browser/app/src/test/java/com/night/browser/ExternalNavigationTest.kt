package com.night.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalNavigationTest {
    @Test fun normalWebNavigationRemainsInEngine() {
        for (url in listOf("https://mozilla.org/", "http://example.org/",
                "about:blank", "blob:https://example.org/uuid", "data:text/plain,hi")) {
            assertEquals(ExternalNavigation.Decision.ENGINE,
                ExternalNavigation.decide(url, false, false, true))
        }
    }

    @Test fun userInitiatedExternalAppLinksRequireConfirmation() {
        assertEquals(ExternalNavigation.Decision.CONFIRM_EXTERNAL,
            ExternalNavigation.decide("mailto:team@example.org", true, false, false))
        assertEquals(ExternalNavigation.Decision.CONFIRM_EXTERNAL,
            ExternalNavigation.decide("tel:+1234567", false, true, false))
    }

    @Test fun scriptRedirectsAndUnknownSchemesAreBlocked() {
        for (url in listOf("tel:+123", "market://details?id=org.test")) {
            assertEquals(ExternalNavigation.Decision.BLOCK,
                ExternalNavigation.decide(url, false, false, false))
            assertEquals(ExternalNavigation.Decision.BLOCK,
                ExternalNavigation.decide(url, true, false, true))
        }
        for (url in listOf("javascript:alert(1)", "file:///etc/passwd",
                "intent://scan/#Intent;scheme=app;end", "ftp://example.org/",
                "content://provider/private", "not-a-uri", "custom://test")) {
            assertEquals(ExternalNavigation.Decision.BLOCK,
                ExternalNavigation.decide(url, true, true, false))
        }
    }
}
