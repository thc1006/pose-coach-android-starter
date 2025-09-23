package com.posecoach.app.suggestions

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.posecoach.corepose.models.PoseLandmarkResult
import com.posecoach.gemini.live.LiveApiManager
import com.posecoach.gemini.live.models.LiveApiConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages Gemini integration for pose coaching suggestions
 * Following CLAUDE.md requirements:
 * - Uses responseSchema in all Gemini calls
 * - Returns exactly 3 suggestions
 * - Implements privacy-first architecture
 */
class SuggestionManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private val liveApiManager = LiveApiManager(
        context = context,
        config = LiveApiConfig(),
        scope = lifecycleScope
    )
    private val gson = Gson()

    private val _suggestions = MutableSharedFlow<List<String>>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val suggestions: SharedFlow<List<String>> = _suggestions.asSharedFlow()

    private val _analysisErrors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val analysisErrors: SharedFlow<String> = _analysisErrors.asSharedFlow()

    // Response schema as required by CLAUDE.md
    private val responseSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("suggestions", JsonObject().apply {
                addProperty("type", "array")
                addProperty("maxItems", 3)
                addProperty("minItems", 3)
                add("items", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("maxLength", 100)
                })
            })
            add("confidence", JsonObject().apply {
                addProperty("type", "number")
                addProperty("minimum", 0.0)
                addProperty("maximum", 1.0)
            })
            add("pose_quality", JsonObject().apply {
                addProperty("type", "string")
                add("enum", gson.toJsonTree(arrayOf("good", "fair", "poor")))
            })
        })
        add("required", gson.toJsonTree(arrayOf("suggestions", "confidence", "pose_quality")))
    }

    /**
     * Analyze pose landmarks and get coaching suggestions from Gemini
     * Following CLAUDE.md requirement: responseSchema usage
     */
    fun analyzePose(pose: PoseLandmarkResult) {
        lifecycleScope.launch {
            try {
                val poseAnalysis = createPoseAnalysisPrompt(pose)
                
                // FIXME: Critical - Gemini API integration missing
                // This is a mock implementation that needs to be replaced with actual Gemini API calls
                // using responseSchema for structured output as per CLAUDE.md requirements
                val mockSuggestions = generateMockSuggestions(pose)
                _suggestions.emit(mockSuggestions.take(3))
                return // Early return until actual API is implemented


                Timber.d("Generated ${suggestions.size} pose coaching suggestions")

            } catch (e: Exception) {
                Timber.e(e, "Error analyzing pose with Gemini")
                _analysisErrors.emit("Failed to analyze pose: ${e.message}")
                
                // Emit fallback suggestions to maintain flow
                _suggestions.emit(getFallbackSuggestions())
            }
        }
    }

    private fun createPoseAnalysisPrompt(pose: PoseLandmarkResult): String {
        val landmarksSummary = summarizePoseLandmarks(pose)
        
        return """
            Analyze this yoga/exercise pose and provide exactly 3 specific coaching suggestions.
            
            Pose Data:
            - Number of landmarks detected: ${pose.landmarks.size}
            - Average confidence: ${pose.landmarks.map { it.visibility }.average()}
            - Key body positions: $landmarksSummary
            
            Please provide:
            1. Exactly 3 actionable coaching suggestions
            2. Confidence level in your analysis (0.0 to 1.0)
            3. Overall pose quality assessment (good/fair/poor)
            
            Focus on:
            - Alignment corrections
            - Balance improvements  
            - Form optimization
            
            Keep suggestions concise (max 100 characters each) and actionable.
        """.trimIndent()
    }

    private fun summarizePoseLandmarks(pose: PoseLandmarkResult): String {
        val landmarks = pose.landmarks
        if (landmarks.size < 33) return "Incomplete pose data"

        // Analyze key pose characteristics
        val leftShoulder = landmarks.getOrNull(11)
        val rightShoulder = landmarks.getOrNull(12)
        val leftHip = landmarks.getOrNull(23)
        val rightHip = landmarks.getOrNull(24)

        val summary = mutableListOf<String>()

        // Shoulder alignment
        if (leftShoulder != null && rightShoulder != null) {
            val shoulderDiff = kotlin.math.abs(leftShoulder.y - rightShoulder.y)
            if (shoulderDiff > 0.05f) {
                summary.add("Shoulder misalignment detected")
            }
        }

        // Hip alignment  
        if (leftHip != null && rightHip != null) {
            val hipDiff = kotlin.math.abs(leftHip.y - rightHip.y)
            if (hipDiff > 0.05f) {
                summary.add("Hip misalignment detected")
            }
        }

        // Balance assessment
        val centerOfMass = calculateCenterOfMass(landmarks)
        summary.add("Center of mass: (${String.format("%.2f", centerOfMass.first)}, ${String.format("%.2f", centerOfMass.second)})")

        return summary.joinToString(", ")
    }

    private fun calculateCenterOfMass(landmarks: List<PoseLandmarkResult.Landmark>): Pair<Float, Float> {
        val visibleLandmarks = landmarks.filter { it.visibility > 0.5f }
        if (visibleLandmarks.isEmpty()) return Pair(0.5f, 0.5f)

        val avgX = visibleLandmarks.map { it.x }.average().toFloat()
        val avgY = visibleLandmarks.map { it.y }.average().toFloat()
        
        return Pair(avgX, avgY)
    }

    private fun extractSuggestionsFromResponse(response: String): List<String> {
        return try {
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val suggestionsArray = jsonResponse.getAsJsonArray("suggestions")
            
            suggestionsArray.map { it.asString }.take(3) // Ensure exactly 3
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Gemini response, using fallback")
            getFallbackSuggestions()
        }
    }

    private fun getFallbackSuggestions(): List<String> {
        return listOf(
            "Keep your spine straight and shoulders relaxed",
            "Engage your core muscles for better stability",
            "Focus on deep, steady breathing"
        )
    }

    /**
     * Generate mock suggestions for testing until Gemini API is implemented
     */
    private fun generateMockSuggestions(pose: PoseLandmarkResult): List<String> {
        val suggestions = mutableListOf<String>()

        // Analyze pose and generate contextual mock suggestions
        val landmarkCount = pose.landmarks.size
        val avgConfidence = pose.landmarks.map { it.visibility }.average()

        if (landmarkCount < 33) {
            suggestions.add("Move closer to camera for better pose detection")
        }

        if (avgConfidence < 0.7) {
            suggestions.add("Ensure good lighting for accurate pose analysis")
        }

        // Add pose-specific suggestions based on landmark analysis
        val leftShoulder = pose.landmarks.getOrNull(11)
        val rightShoulder = pose.landmarks.getOrNull(12)

        if (leftShoulder != null && rightShoulder != null) {
            val shoulderDiff = kotlin.math.abs(leftShoulder.y - rightShoulder.y)
            if (shoulderDiff > 0.05f) {
                suggestions.add("Level your shoulders for better alignment")
            } else {
                suggestions.add("Great shoulder alignment! Keep it up")
            }
        }

        // Always include at least one generic suggestion
        if (suggestions.isEmpty()) {
            suggestions.addAll(getFallbackSuggestions())
        }

        // Ensure exactly 3 suggestions
        while (suggestions.size < 3) {
            suggestions.add("Maintain steady breathing and focus")
        }

        return suggestions.take(3)
    }
}
