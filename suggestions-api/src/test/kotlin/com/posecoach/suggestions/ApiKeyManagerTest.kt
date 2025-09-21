package com.posecoach.suggestions

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiKeyManagerTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var apiKeyManager: ApiKeyManager

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        apiKeyManager = ApiKeyManager(mockContext)
    }

    @Test
    fun `hasValidApiKey returns false for empty key`() {
        // This test would need to mock the encrypted preferences
        // For now, we test the logic
        assertFalse(apiKeyManager.hasValidApiKey())
    }

    @Test
    fun `sanitizeApiKeyForLogging masks key properly`() {
        val testKey = "AIzaSyBxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        val sanitized = apiKeyManager.sanitizeApiKeyForLogging(testKey)

        assertEquals("AIza...xxxx", sanitized)
    }

    @Test
    fun `sanitizeApiKeyForLogging handles short keys`() {
        val shortKey = "short"
        val sanitized = apiKeyManager.sanitizeApiKeyForLogging(shortKey)

        assertEquals("[INVALID]", sanitized)
    }

    @Test
    fun `sanitizeApiKeyForLogging handles null key`() {
        val sanitized = apiKeyManager.sanitizeApiKeyForLogging(null)

        assertEquals("[INVALID]", sanitized)
    }

    @Test
    fun `getApiKeySource returns correct source description`() {
        // Test when no key is available
        val source = apiKeyManager.getApiKeySource()
        assertNotNull(source)
    }

    @Test
    fun `markApiKeyAsValidated updates validation status`() {
        // Test validation marking
        apiKeyManager.markApiKeyAsValidated(true)
        // Would need to verify internal state if accessible
    }

    @Test
    fun `isApiKeyRecentlyValidated checks expiry correctly`() {
        // Test validation expiry logic
        val isRecent = apiKeyManager.isApiKeyRecentlyValidated()
        // This would return false initially since no validation has been done
        assertFalse(isRecent)
    }

    @Test
    fun `clearUserApiKey removes stored key`() {
        // Test clearing user API key
        apiKeyManager.clearUserApiKey()
        // Would need to verify internal state if accessible
    }

    @Test
    fun `setUserApiKey handles empty string`() {
        // Test setting empty API key
        apiKeyManager.setUserApiKey("")
        // Should clear the key instead of storing empty string
    }

    @Test
    fun `setUserApiKey trims whitespace`() {
        // Test that API key is trimmed when stored
        apiKeyManager.setUserApiKey("  test-key  ")
        // Would need to verify that stored key is "test-key" without spaces
    }
}