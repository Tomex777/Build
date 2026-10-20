package com.night.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeekNavigationPolicyTest {
    @Test fun `normal web links and redirects are permitted`() {
        assertTrue(PeekNavigationPolicy.allows("https://example.org/article?q=2"))
        assertTrue(PeekNavigationPolicy.allows("http://www.example.net"))
    }

    @Test fun `preview cannot trigger Android intent or local scheme navigation`() {
        for (url in listOf(
            "intent://scan/#Intent;scheme=zxing;package=com.demo;end",
            "market://details?id=com.demo", "tel:+18005550100",
            "mailto:a@example.org", "file:///storage/emulated/0/private.txt",
            "javascript:alert(1)", "content://provider/file",
            "about:config", "data:text/html,test", "https://user:pass@host.test"
        )) {
            assertFalse("Unexpected permitted Peek scheme: $url",
                PeekNavigationPolicy.allows(url))
        }
    }
}
