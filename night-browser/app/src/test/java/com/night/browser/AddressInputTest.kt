package com.night.browser

import org.junit.Assert.*
import org.junit.Test

class AddressInputTest {
    @Test fun domainsUseHttps() {
        assertEquals("https://example.com", AddressInput.resolve("example.com"))
    }
    @Test fun queriesAreEncoded() {
        assertEquals("https://www.google.com/search?q=ringed+planet", AddressInput.resolve("ringed planet"))
    }
    @Test fun dangerousSchemesAreNotOpened() {
        assertNull(AddressInput.resolve("javascript://example.com/alert(1)"))
        assertNull(AddressInput.resolve("file:///private/data"))
    }
    @Test fun localhostWithPortWorksForDevelopment() {
        assertEquals("https://localhost:8080", AddressInput.resolve("localhost:8080"))
        assertEquals("https://localhost/path", AddressInput.resolve("localhost/path"))
    }
    @Test fun upperCaseWebSchemesAreAccepted() {
        assertEquals("HTTPS://example.com", AddressInput.resolve("HTTPS://example.com"))
    }
    @Test fun credentialsAreNotAcceptedInAddressBar() {
        assertNull(AddressInput.resolve("https://user:password@example.com"))
    }
    @Test fun explicitHttpIsPreserved() {
        assertEquals("http://example.com", AddressInput.resolve("http://example.com"))
    }
}
