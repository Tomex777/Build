package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class AddressInputTest {
    @Test fun domainsUseHttps() {
        assertEquals("https://example.com", AddressInput.resolve("example.com"))
        assertEquals("https://example.com/path", AddressInput.resolve("example.com/path"))
    }

    @Test fun queriesAreEncodedWithoutSendingEachKeystroke() {
        assertEquals("https://www.google.com/search?q=ringed+planet",
            AddressInput.resolve("ringed planet"))
        assertEquals("https://www.google.com/search?q=hello",
            AddressInput.resolve("hello"))
    }

    @Test fun localhostPortsWorkForDevelopment() {
        assertEquals("https://localhost:8080", AddressInput.resolve("localhost:8080"))
        assertEquals("https://localhost/path", AddressInput.resolve("localhost/path"))
    }

    @Test fun explicitHttpAndUppercaseSchemesArePreserved() {
        assertEquals("http://example.com", AddressInput.resolve("http://example.com"))
        assertEquals("HTTPS://example.com", AddressInput.resolve("HTTPS://example.com"))
    }

    @Test fun credentialBearingAndNonWebSchemesAreBlocked() {
        assertNull(AddressInput.resolve("https://user:password@example.com"))
        assertNull(AddressInput.resolve("user:password@example.com"))
        assertNull(AddressInput.resolve("javascript://example.com/alert(1)"))
        assertNull(AddressInput.resolve("javascript:alert(1)"))
        assertNull(AddressInput.resolve("data:text/html,hello"))
        assertNull(AddressInput.resolve("intent://example.com"))
        assertNull(AddressInput.resolve("file:///private/data"))
        assertNull(AddressInput.resolve("about:config"))
    }

    @Test fun unqualifiedEmailAddressesAreSearchesNotUrls() {
        assertEquals("https://www.google.com/search?q=hello%40example.com",
            AddressInput.resolve("hello@example.com"))
    }

    @Test fun malformedOrOversizedExplicitAddressesDoNotNavigate() {
        assertNull(AddressInput.resolve("https://"))
        assertNull(AddressInput.resolve("https://example.com:0"))
        assertNull(AddressInput.resolve("https://example.com/one two"))
        assertNull(AddressInput.resolve("https://example.com/\u0000test"))
        assertNull(AddressInput.resolve("a".repeat(8193)))
    }
}
