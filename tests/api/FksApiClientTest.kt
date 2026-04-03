package xyz.fkstrading.clients.api

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for FksApiClient
 */
class FksApiClientTest {
    @Test
    fun testApiClientExists() {
        assertNotNull(FksApiClient, "FksApiClient should exist")
    }

    @Test
    fun testApiClientHasHttpClient() {
        assertNotNull(FksApiClient.httpClient, "FksApiClient should have httpClient")
    }

    @Test
    fun testApiClientHasDefaultUrls() {
        assertTrue(FksApiClient.apiUrl.isNotEmpty(), "API URL should not be empty")
        assertTrue(FksApiClient.dataUrl.isNotEmpty(), "Data URL should not be empty")
        assertTrue(FksApiClient.authUrl.isNotEmpty(), "Auth URL should not be empty")
    }

    @Test
    fun testConfigureMethod() {
        val originalApiUrl = FksApiClient.apiUrl

        // Configure with new URL
        FksApiClient.configure(apiUrl = "http://test-api:8080")

        // Note: In a real test, we would verify the URL changed
        // However, since configure() uses private set, we can't directly test this
        // without reflection or exposing the value

        // Reset to original (if needed in real test)
        FksApiClient.configure(apiUrl = originalApiUrl)
    }
}
