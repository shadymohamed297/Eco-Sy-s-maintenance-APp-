package com.markazsayana.app.data.local

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerAddressTest {
    @Test fun serverAddressKeepsDeploymentPrefixAndAddsTrailingSlash() {
        assertEquals("https://center.example/maintenance/", normalizeServerBaseUrl(" https://center.example/maintenance "))
    }

    @Test fun requestMovesToConfiguredServerAndPreservesQueryAndEscapedPath() {
        val actual = resolveServerRequestUrl(
            "https://center.example:8443/maintenance/".toHttpUrl(),
            "https://maintenance.invalid/api/work-orders/ABC%2F123?search=%D8%B9%D9%85%D9%8A%D9%84&limit=30".toHttpUrl(),
        )
        assertEquals("https://center.example:8443/maintenance/api/work-orders/ABC%2F123?search=%D8%B9%D9%85%D9%8A%D9%84&limit=30", actual.toString())
    }

    @Test fun serverAddressRejectsCredentialsAndNonBaseUrlParts() {
        listOf("not-a-url", "ftp://center.example", "https://user:password@center.example", "https://center.example/?token=secret", "https://center.example/#section").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { normalizeServerBaseUrl(invalid) }
        }
    }
}
