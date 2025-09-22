package com.posecoach.app.multimodal.processors

import com.posecoach.app.multimodal.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.*

/**
 * Confidence Weighting Processor
 *
 * Manages confidence-based weighting and fusion across modalities:
 * - Calculates dynamic confidence weights based on quality metrics
 * - Applies adaptive weighting based on context and reliability
 * - Handles confidence decay and temporal consistency
 * - Provides uncertainty quantification for fusion decisions
 */
class ConfidenceWeightingProcessor {

    companion object {
        private const val MIN_CONFIDENCE_THRESHOLD = 0.1f
        private const val CONFIDENCE_DECAY_RATE = 0.95f
        private const val TEMPORAL_CONSISTENCY_WINDOW = 3
        private const val ADAPTIVE_LEARNING_RATE = 0.1f
        private const val UNCERTAINTY_AMPLIFICATION_FACTOR = 1.5f
    }

    // Confidence tracking
    private val modalityHistory = mutableMapOf<String, MutableList<Float>>()
    private val adaptiveWeights = mutableMapOf<String, Float>()
    private val reliabilityScores = mutableMapOf<String, Float>()

    init {
        initializeWeights()
    }

    /**
     * Weight insights based on confidence and reliability
     */
    suspend fun weightInsights(
        validatedAnalyses: List<CrossModalValidationProcessor.ValidatedModalityAnalysis>
    ): List<WeightedModalityAnalysis> = withContext(Dispatchers.Default) {

        try {
            val weightedAnalyses = mutableListOf<WeightedModalityAnalysis>()

            validatedAnalyses.forEach { validatedAnalysis ->
                val analysis = validatedAnalysis.originalAnalysis
                // Update confidence history
                updateConfidenceHistory(analysis)

                // Calculate dynamic weight
                val dynamicWeight = calculateDynamicWeight(analysis)

                // Calculate reliability adjustment
                val reliabilityAdjustment = calculateReliabilityAdjustment(analysis.modality)

                // Calculate temporal consistency bonus
                val temporalConsistency = calculateTemporalConsistency(analysis.modality)

                // Calculate uncertainty quantification
                val uncertainty = calculateUncertainty(analysis)

                // Combine all factors for final weight
                val finalWeight = combineWeightingFactors(
                    baseConfidence = analysis.confidence,
                    dynamicWeight = dynamicWeight,
                    reliabilityAdjustment = reliabilityAdjustment,
                    temporalConsistency = temporalConsistency,
                    uncertainty = uncertainty
                )

                weightedAnalyses.add(
                    WeightedModalityAnalysis(
                        originalAnalysis = analysis,
                        weight = finalWeight,
                        confidence = analysis.confidence,
                        reliability = reliabilityScores[analysis.modality] ?: 0.5f,
                        temporalConsistency = temporalConsistency,
                        uncertainty = uncertainty,
                        qualityMetrics = calculateQualityMetrics(analysis)
                    )
                )

                // Update adaptive weights based on performance
                updateAdaptiveWeights(analysis, finalWeight)
            }

            // Normalize weights across all modalities
            val normalizedWeights = normalizeWeights(weightedAnalyses)

            Timber.d("Weighted ${normalizedWeights.size} modality analyses")
            normalizedWeights

        } catch (e: Exception) {
            Timber.e(e, "Error weighting insights")
            // Return analyses with equal weights as fallback
            validatedAnalyses.map { validatedAnalysis ->
                val analysis = validatedAnalysis.originalAnalysis
                WeightedModalityAnalysis(
                    originalAnalysis = analysis,
                    weight = 1.0f / validatedAnalyses.size,
                    confidence = analysis.confidence,
                    reliability = 0.5f,
                    temporalConsistency = 0.5f,
                    uncertainty = 0.5f,
                    qualityMetrics = ModalityQualityMetrics(
                        dataQuality = 0.5f,
                        processingQuality = 0.5f,
                        consistencyScore = 0.5f,
                        overallQuality = 0.5f
                    )
                )
            }
        }
    }

    /**
     * Calculate cross-modal confidence validation
     */
    suspend fun calculateCrossModalConfidence(
        weightedAnalyses: List<WeightedModalityAnalysis>
    ): CrossModalConfidenceAnalysis = withContext(Dispatchers.Default) {

        try {
            if (weightedAnalyses.size < 2) {
                return@withContext CrossModalConfidenceAnalysis(
                    overallConfidence = weightedAnalyses.firstOrNull()?.confidence ?: 0f,
                    consensusLevel = 1.0f, // Perfect consensus with single modality
                    conflictLevel = 0f,
                    modalityAgreement = emptyMap(),
                    uncertaintyPropagation = 0f
                )
            }

            // Calculate pairwise agreement between modalities
            val modalityAgreement = calculateModalityAgreement(weightedAnalyses)

            // Calculate consensus level
            val consensusLevel = calculateConsensusLevel(modalityAgreement)

            // Calculate conflict level
            val conflictLevel = calculateConflictLevel(weightedAnalyses)

            // Calculate overall confidence with uncertainty propagation
            val overallConfidence = calculateOverallConfidence(weightedAnalyses, consensusLevel)

            // Calculate uncertainty propagation
            val uncertaintyPropagation = calculateUncertaintyPropagation(weightedAnalyses)

            CrossModalConfidenceAnalysis(
                overallConfidence = overallConfidence,
                consensusLevel = consensusLevel,
                conflictLevel = conflictLevel,
                modalityAgreement = modalityAgreement,
                uncertaintyPropagation = uncertaintyPropagation
            )

        } catch (e: Exception) {
            Timber.e(e, "Error calculating cross-modal confidence")
            CrossModalConfidenceAnalysis(
                overallConfidence = 0.3f,
                consensusLevel = 0.5f,
                conflictLevel = 0.5f,
                modalityAgreement = emptyMap(),
                uncertaintyPropagation = 0.7f
            )
        }
    }

    /**
     * Apply temporal confidence decay
     */
    suspend fun applyTemporalDecay(
        weightedAnalyses: List<WeightedModalityAnalysis>,
        timeElapsed: Long
    ): List<WeightedModalityAnalysis> = withContext(Dispatchers.Default) {

        try {
            val decayFactor = calculateDecayFactor(timeElapsed)

            weightedAnalyses.map { analysis ->
                val decayedConfidence = analysis.confidence * decayFactor
                val decayedWeight = analysis.weight * decayFactor

                analysis.copy(
                    weight = decayedWeight.coerceAtLeast(MIN_CONFIDENCE_THRESHOLD),
                    confidence = decayedConfidence.coerceAtLeast(MIN_CONFIDENCE_THRESHOLD)
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Error applying temporal decay")
            weightedAnalyses
        }
    }

    /**
     * Calculate adaptive weight adjustments
     */
    private fun calculateDynamicWeight(analysis: ModalityAnalysis): Float {
        val baseWeight = adaptiveWeights[analysis.modality] ?: 0.5f
        val qualityBonus = calculateQualityBonus(analysis)
        val contextualRelevance = calculateContextualRelevance(analysis)

        return (baseWeight + qualityBonus + contextualRelevance) / 3f
    }

    private fun calculateReliabilityAdjustment(modality: String): Float {
        val reliability = reliabilityScores[modality] ?: 0.5f
        val history = modalityHistory[modality] ?: mutableListOf()

        // Calculate consistency from history
        val consistency = if (history.size >= 2) {
            calculateConsistencyFromHistory(history)
        } else 0.5f

        return (reliability + consistency) / 2f
    }

    private fun calculateTemporalConsistency(modality: String): Float {
        val history = modalityHistory[modality] ?: return 0.5f

        if (history.size < TEMPORAL_CONSISTENCY_WINDOW) {
            return 0.5f
        }

        val recentHistory = history.takeLast(TEMPORAL_CONSISTENCY_WINDOW)
        val mean = recentHistory.average().toFloat()
        val variance = recentHistory.map { (it - mean).pow(2) }.average().toFloat()

        // Lower variance = higher consistency
        return 1.0f - min(sqrt(variance), 1.0f)
    }

    private fun calculateUncertainty(analysis: ModalityAnalysis): Float {
        // Calculate uncertainty based on various factors
        val confidenceUncertainty = 1.0f - analysis.confidence
        val consistencyUncertainty = calculateConsistencyUncertainty(analysis.modality)
        val dataQualityUncertainty = calculateDataQualityUncertainty(analysis)

        return (confidenceUncertainty + consistencyUncertainty + dataQualityUncertainty) / 3f
    }

    private fun combineWeightingFactors(
        baseConfidence: Float,
        dynamicWeight: Float,
        reliabilityAdjustment: Float,
        temporalConsistency: Float,
        uncertainty: Float
    ): Float {
        // Weighted combination of all factors
        val combinedWeight = (
            baseConfidence * 0.3f +
            dynamicWeight * 0.25f +
            reliabilityAdjustment * 0.2f +
            temporalConsistency * 0.15f +
            (1.0f - uncertainty) * 0.1f
        )

        return combinedWeight.coerceIn(MIN_CONFIDENCE_THRESHOLD, 1.0f)
    }

    private fun calculateQualityMetrics(analysis: ModalityAnalysis): ModalityQualityMetrics {
        val dataQuality = calculateDataQuality(analysis)
        val processingQuality = calculateProcessingQuality(analysis)
        val consistencyScore = calculateConsistencyScore(analysis)
        val overallQuality = (dataQuality + processingQuality + consistencyScore) / 3f

        return ModalityQualityMetrics(
            dataQuality = dataQuality,
            processingQuality = processingQuality,
            consistencyScore = consistencyScore,
            overallQuality = overallQuality
        )
    }

    private fun normalizeWeights(
        analyses: List<WeightedModalityAnalysis>
    ): List<WeightedModalityAnalysis> {
        val totalWeight = analyses.sumOf { it.weight.toDouble() }.toFloat()

        if (totalWeight <= 0f) {
            // Equal weights if total is zero
            val equalWeight = 1.0f / analyses.size
            return analyses.map { it.copy(weight = equalWeight) }
        }

        return analyses.map { analysis ->
            analysis.copy(weight = analysis.weight / totalWeight)
        }
    }

    // Cross-modal confidence analysis methods

    private fun calculateModalityAgreement(
        analyses: List<WeightedModalityAnalysis>
    ): Map<Pair<String, String>, Float> {
        val agreement = mutableMapOf<Pair<String, String>, Float>()

        for (i in analyses.indices) {
            for (j in i + 1 until analyses.size) {
                val modality1 = analyses[i].originalAnalysis.modality
                val modality2 = analyses[j].originalAnalysis.modality

                val confidenceAgreement = calculateConfidenceAgreement(
                    analyses[i].confidence,
                    analyses[j].confidence
                )

                val insightAgreement = calculateInsightAgreement(
                    analyses[i].originalAnalysis.insights,
                    analyses[j].originalAnalysis.insights
                )

                val overallAgreement = (confidenceAgreement + insightAgreement) / 2f
                agreement[Pair(modality1, modality2)] = overallAgreement
            }
        }

        return agreement
    }

    private fun calculateConsensusLevel(modalityAgreement: Map<Pair<String, String>, Float>): Float {
        return if (modalityAgreement.isNotEmpty()) {
            modalityAgreement.values.average().toFloat()
        } else 1.0f
    }

    private fun calculateConflictLevel(analyses: List<WeightedModalityAnalysis>): Float {
        if (analyses.size < 2) return 0f

        val confidences = analyses.map { it.confidence }
        val mean = confidences.average().toFloat()
        val variance = confidences.map { (it - mean).pow(2) }.average().toFloat()

        // Higher variance indicates more conflict
        return min(sqrt(variance) * 2f, 1.0f)
    }

    private fun calculateOverallConfidence(
        analyses: List<WeightedModalityAnalysis>,
        consensusLevel: Float
    ): Float {
        val weightedConfidence = analyses.sumOf {
            (it.confidence * it.weight).toDouble()
        }.toFloat()

        // Adjust for consensus level
        return weightedConfidence * consensusLevel
    }

    private fun calculateUncertaintyPropagation(
        analyses: List<WeightedModalityAnalysis>
    ): Float {
        val weightedUncertainty = analyses.sumOf {
            (it.uncertainty * it.weight).toDouble()
        }.toFloat()

        // Amplify uncertainty when combining multiple sources
        return min(weightedUncertainty * UNCERTAINTY_AMPLIFICATION_FACTOR, 1.0f)
    }

    // Helper methods

    private fun initializeWeights() {
        adaptiveWeights["pose"] = 0.9f
        adaptiveWeights["audio"] = 0.7f
        adaptiveWeights["vision"] = 0.8f
        adaptiveWeights["environment"] = 0.6f
        adaptiveWeights["emotional"] = 0.7f

        reliabilityScores["pose"] = 0.9f
        reliabilityScores["audio"] = 0.7f
        reliabilityScores["vision"] = 0.8f
        reliabilityScores["environment"] = 0.8f
        reliabilityScores["emotional"] = 0.6f
    }

    private fun updateConfidenceHistory(analysis: ModalityAnalysis) {
        val history = modalityHistory.getOrPut(analysis.modality) { mutableListOf() }
        history.add(analysis.confidence)

        // Keep only recent history
        if (history.size > 10) {
            history.removeAt(0)
        }
    }

    private fun updateAdaptiveWeights(analysis: ModalityAnalysis, finalWeight: Float) {
        val currentWeight = adaptiveWeights[analysis.modality] ?: 0.5f
        val performance = analysis.confidence // Use confidence as performance indicator

        // Update weight based on performance
        val newWeight = currentWeight + ADAPTIVE_LEARNING_RATE * (performance - currentWeight)
        adaptiveWeights[analysis.modality] = newWeight.coerceIn(0.1f, 1.0f)
    }

    private fun calculateDecayFactor(timeElapsed: Long): Float {
        // Exponential decay based on time elapsed (in milliseconds)
        val timeInSeconds = timeElapsed / 1000f
        return CONFIDENCE_DECAY_RATE.pow(timeInSeconds)
    }

    private fun calculateQualityBonus(analysis: ModalityAnalysis): Float {
        // Bonus based on number and quality of insights
        val insightCount = analysis.insights.size
        val avgInsightLength = if (insightCount > 0) {
            analysis.insights.map { it.length }.average()
        } else 0.0

        val countBonus = min(insightCount / 5f, 0.2f) // Max 0.2 bonus for insight count
        val lengthBonus = min(avgInsightLength.toFloat() / 100f, 0.1f) // Max 0.1 bonus for insight length

        return countBonus + lengthBonus
    }

    private fun calculateContextualRelevance(analysis: ModalityAnalysis): Float {
        // Calculate how relevant this modality is in current context
        return when (analysis.modality) {
            "pose" -> 0.9f // Always highly relevant for fitness
            "audio" -> 0.7f // Important for breathing and vocal feedback
            "vision" -> 0.8f // Important for environment and safety
            "emotional" -> 0.6f // Moderately relevant for coaching adaptation
            "environment" -> 0.5f // Context-dependent relevance
            else -> 0.5f
        }
    }

    private fun calculateConsistencyFromHistory(history: List<Float>): Float {
        if (history.size < 2) return 0.5f

        val mean = history.average().toFloat()
        val variance = history.map { (it - mean).pow(2) }.average().toFloat()

        return 1.0f - min(sqrt(variance), 1.0f)
    }

    private fun calculateConsistencyUncertainty(modality: String): Float {
        val consistency = calculateTemporalConsistency(modality)
        return 1.0f - consistency
    }

    private fun calculateDataQualityUncertainty(analysis: ModalityAnalysis): Float {
        // Simple heuristic: lower confidence indicates higher uncertainty
        return 1.0f - analysis.confidence
    }

    private fun calculateDataQuality(analysis: ModalityAnalysis): Float {
        // Use confidence as a proxy for data quality
        return analysis.confidence
    }

    private fun calculateProcessingQuality(analysis: ModalityAnalysis): Float {
        // Quality based on insight richness and coherence
        val insightQuality = if (analysis.insights.isNotEmpty()) {
            val avgLength = analysis.insights.map { it.length }.average().toFloat()
            min(avgLength / 50f, 1.0f) // Normalize to 50 chars as "good" length
        } else 0.3f

        return insightQuality
    }

    private fun calculateConsistencyScore(analysis: ModalityAnalysis): Float {
        return calculateTemporalConsistency(analysis.modality)
    }

    private fun calculateConfidenceAgreement(confidence1: Float, confidence2: Float): Float {
        return 1.0f - abs(confidence1 - confidence2)
    }

    private fun calculateInsightAgreement(insights1: List<String>, insights2: List<String>): Float {
        // Simple heuristic: agreement based on insight count similarity
        if (insights1.isEmpty() && insights2.isEmpty()) return 1.0f
        if (insights1.isEmpty() || insights2.isEmpty()) return 0.3f

        val countSimilarity = 1.0f - abs(insights1.size - insights2.size).toFloat() /
                              max(insights1.size, insights2.size)

        return countSimilarity
    }

    // Data classes
    data class WeightedModalityAnalysis(
        val originalAnalysis: ModalityAnalysis,
        val weight: Float,
        val confidence: Float,
        val reliability: Float,
        val temporalConsistency: Float,
        val uncertainty: Float,
        val qualityMetrics: ModalityQualityMetrics
    )

    data class ModalityQualityMetrics(
        val dataQuality: Float,
        val processingQuality: Float,
        val consistencyScore: Float,
        val overallQuality: Float
    )

    data class CrossModalConfidenceAnalysis(
        val overallConfidence: Float,
        val consensusLevel: Float,
        val conflictLevel: Float,
        val modalityAgreement: Map<Pair<String, String>, Float>,
        val uncertaintyPropagation: Float
    )
}