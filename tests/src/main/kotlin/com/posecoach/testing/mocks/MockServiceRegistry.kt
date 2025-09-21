package com.posecoach.testing.mocks

import com.posecoach.corepose.repository.PoseRepository
import com.posecoach.suggestions.PoseSuggestionClient
import com.posecoach.testing.mocks.pose.MockPoseRepository
import com.posecoach.testing.mocks.suggestions.MockPoseSuggestionClient
import com.posecoach.testing.mocks.camera.MockCameraManager
import com.posecoach.testing.mocks.network.MockNetworkManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all mock implementations used in testing
 * Provides type-safe mock creation and management with automatic cleanup
 */
object MockServiceRegistry {

    private val mockInstances = ConcurrentHashMap<Class<*>, Any>()
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return

        registerDefaultMocks()
        isInitialized = true

        Timber.i("MockServiceRegistry initialized with ${mockInstances.size} mock services")
    }

    private fun registerDefaultMocks() {
        // Register core mock implementations
        register(PoseRepository::class.java, MockPoseRepository())
        register(PoseSuggestionClient::class.java, MockPoseSuggestionClient())
        register(MockCameraManager::class.java, MockCameraManager())
        register(MockNetworkManager::class.java, MockNetworkManager())
    }

    /**
     * Register a mock implementation for a given service type
     */
    fun <T> register(serviceClass: Class<T>, mockImplementation: T) {
        mockInstances[serviceClass] = mockImplementation as Any
        Timber.d("Registered mock for ${serviceClass.simpleName}")
    }

    /**
     * Get a mock implementation for a given service type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMock(serviceClass: Class<T>): T {
        val mock = mockInstances[serviceClass]
            ?: throw IllegalArgumentException("No mock registered for ${serviceClass.simpleName}")
        return mock as T
    }

    /**
     * Get a mock implementation with type inference
     */
    inline fun <reified T> getMock(): T = getMock(T::class.java)

    /**
     * Check if a mock is registered for a given service type
     */
    fun <T> hasMock(serviceClass: Class<T>): Boolean {
        return mockInstances.containsKey(serviceClass)
    }

    /**
     * Clear all registered mocks
     */
    fun clearAll() {
        mockInstances.values.forEach { mock ->
            if (mock is AutoCloseable) {
                try {
                    mock.close()
                } catch (e: Exception) {
                    Timber.w(e, "Error closing mock ${mock::class.simpleName}")
                }
            }
        }

        mockInstances.clear()
        Timber.i("All mocks cleared")
    }

    /**
     * Get statistics about registered mocks
     */
    fun getStatistics(): MockRegistryStatistics {
        return MockRegistryStatistics(
            totalMocks = mockInstances.size,
            registeredTypes = mockInstances.keys.map { it.simpleName }
        )
    }

    data class MockRegistryStatistics(
        val totalMocks: Int,
        val registeredTypes: List<String>
    )
}

/**
 * Base interface for all mock implementations
 */
interface MockService {
    /**
     * Reset the mock to its initial state
     */
    fun reset()

    /**
     * Get interaction statistics for this mock
     */
    fun getInteractionCount(): Int
}