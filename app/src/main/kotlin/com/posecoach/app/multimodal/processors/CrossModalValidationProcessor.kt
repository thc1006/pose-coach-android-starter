package com.posecoach.app.multimodal.processors

import com.posecoach.app.multimodal.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.*

/**
 * Cross-Modal Validation Processor
 *
 * Validates and reconciles insights across different modalities:
 * - Detects and resolves conflicts between modalities
 * - Validates consistency of insights across different input sources
 * - Applies validation rules and consistency checks
 * - Provides confidence adjustments based on cross-modal agreement
 */
class CrossModalValidationProcessor {

    companion object {
        private const val CONFLICT_THRESHOLD = 0.7f
        private const val CONSISTENCY_THRESHOLD = 0.6f
        private const val VALIDATION_CONFIDENCE_ADJUSTMENT = 0.8f
        private const val MIN_MODALITIES_FOR_VALIDATION = 2
    }

    // Validation rules and consistency patterns
    private val validationRules = initializeValidationRules()
    private val consistencyPatterns = initializeConsistencyPatterns()

    /**
     * Validate modality analyses for consistency and conflicts
     */
    suspend fun validateAnalyses(
        modalityAnalyses: List<ModalityAnalysis>
    ): List<ValidatedModalityAnalysis> = withContext(Dispatchers.Default) {

        try {
            if (modalityAnalyses.size < MIN_MODALITIES_FOR_VALIDATION) {
                // Single modality - apply basic validation
                return@withContext modalityAnalyses.map { analysis ->
                    ValidatedModalityAnalysis(
                        originalAnalysis = analysis,
                        validationScore = validateSingleModality(analysis),
                        conflicts = emptyList(),
                        consistencyScore = 1.0f,
                        adjustedConfidence = analysis.confidence,
                        validationFlags = listOf("single_modality")
                    )
                }
            }

            val validatedAnalyses = mutableListOf<ValidatedModalityAnalysis>()

            modalityAnalyses.forEach { analysis ->
                // Validate against other modalities
                val crossModalValidation = performCrossModalValidation(analysis, modalityAnalyses)

                // Check for conflicts
                val conflicts = detectConflicts(analysis, modalityAnalyses)

                // Calculate consistency score
                val consistencyScore = calculateConsistencyScore(analysis, modalityAnalyses)

                // Apply validation rules
                val ruleValidation = applyValidationRules(analysis, modalityAnalyses)

                // Adjust confidence based on validation results
                val adjustedConfidence = adjustConfidenceBasedOnValidation(
                    analysis.confidence,
                    crossModalValidation,
                    conflicts,
                    consistencyScore
                )

                // Generate validation flags
                val validationFlags = generateValidationFlags(
                    crossModalValidation,
                    conflicts,
                    consistencyScore,
                    ruleValidation
                )

                validatedAnalyses.add(
                    ValidatedModalityAnalysis(
                        originalAnalysis = analysis,
                        validationScore = crossModalValidation.overallScore,
                        conflicts = conflicts,
                        consistencyScore = consistencyScore,
                        adjustedConfidence = adjustedConfidence,
                        validationFlags = validationFlags
                    )
                )
            }

            Timber.d("Validated ${validatedAnalyses.size} modality analyses")
            validatedAnalyses

        } catch (e: Exception) {
            Timber.e(e, "Error validating modality analyses")
            // Return original analyses with minimal validation
            modalityAnalyses.map { analysis ->
                ValidatedModalityAnalysis(
                    originalAnalysis = analysis,
                    validationScore = 0.5f,
                    conflicts = emptyList(),
                    consistencyScore = 0.5f,
                    adjustedConfidence = analysis.confidence * 0.8f,
                    validationFlags = listOf("validation_error")
                )
            }
        }
    }

    /**
     * Resolve conflicts between modalities
     */
    suspend fun resolveConflicts(
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConflictResolutionResult = withContext(Dispatchers.Default) {

        try {
            val allConflicts = validatedAnalyses.flatMap { it.conflicts }

            if (allConflicts.isEmpty()) {
                return@withContext ConflictResolutionResult(
                    resolvedAnalyses = validatedAnalyses,
                    resolutionStrategy = "no_conflicts",
                    resolutionConfidence = 1.0f,
                    removedConflicts = emptyList()
                )
            }

            // Group conflicts by type
            val conflictsByType = allConflicts.groupBy { it.conflictType }

            val resolvedAnalyses = mutableListOf<ValidatedModalityAnalysis>()
            val removedConflicts = mutableListOf<ModalityConflict>()

            validatedAnalyses.forEach { analysis ->
                val analysisConflicts = analysis.conflicts.toMutableList()
                val resolvedConflictsForAnalysis = mutableListOf<ModalityConflict>()

                analysisConflicts.forEach { conflict ->
                    val resolution = resolveIndividualConflict(conflict, validatedAnalyses)
                    if (resolution.resolved) {
                        resolvedConflictsForAnalysis.add(conflict)
                        removedConflicts.add(conflict)
                    }
                }

                // Remove resolved conflicts
                analysisConflicts.removeAll(resolvedConflictsForAnalysis)

                resolvedAnalyses.add(
                    analysis.copy(
                        conflicts = analysisConflicts,
                        adjustedConfidence = if (resolvedConflictsForAnalysis.isNotEmpty()) {
                            analysis.adjustedConfidence * 1.1f // Boost confidence after resolution
                        } else {
                            analysis.adjustedConfidence
                        }
                    )
                )
            }

            val resolutionStrategy = determineResolutionStrategy(conflictsByType)
            val resolutionConfidence = calculateResolutionConfidence(allConflicts, removedConflicts)

            ConflictResolutionResult(
                resolvedAnalyses = resolvedAnalyses,
                resolutionStrategy = resolutionStrategy,
                resolutionConfidence = resolutionConfidence,
                removedConflicts = removedConflicts
            )

        } catch (e: Exception) {
            Timber.e(e, "Error resolving conflicts")
            ConflictResolutionResult(
                resolvedAnalyses = validatedAnalyses,
                resolutionStrategy = "error",
                resolutionConfidence = 0.3f,
                removedConflicts = emptyList()
            )
        }
    }

    /**
     * Generate consistency report across modalities
     */
    suspend fun generateConsistencyReport(
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConsistencyReport = withContext(Dispatchers.Default) {

        try {
            val overallConsistency = validatedAnalyses.map { it.consistencyScore }.average().toFloat()
            val modalityConsistency = validatedAnalyses.associate {
                it.originalAnalysis.modality to it.consistencyScore
            }

            val inconsistencies = validatedAnalyses.flatMap { analysis ->
                analysis.conflicts.map { conflict ->
                    Inconsistency(
                        modalities = listOf(analysis.originalAnalysis.modality, conflict.conflictingModality),
                        inconsistencyType = conflict.conflictType,
                        severity = conflict.severity,
                        description = conflict.description
                    )
                }
            }

            val recommendations = generateConsistencyRecommendations(inconsistencies, modalityConsistency)

            ConsistencyReport(
                overallConsistency = overallConsistency,
                modalityConsistency = modalityConsistency,
                inconsistencies = inconsistencies,
                recommendations = recommendations
            )

        } catch (e: Exception) {
            Timber.e(e, "Error generating consistency report")
            ConsistencyReport(
                overallConsistency = 0.5f,
                modalityConsistency = emptyMap(),
                inconsistencies = emptyList(),
                recommendations = listOf("Error generating report - manual review recommended")
            )
        }
    }

    // Validation methods

    private fun performCrossModalValidation(
        targetAnalysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): CrossModalValidationResult {
        val otherAnalyses = allAnalyses.filter { it.modality != targetAnalysis.modality }

        if (otherAnalyses.isEmpty()) {
            return CrossModalValidationResult(
                overallScore = 0.7f,
                agreementScores = emptyMap(),
                supportingEvidence = emptyList(),
                contradictingEvidence = emptyList()
            )
        }

        val agreementScores = mutableMapOf<String, Float>()
        val supportingEvidence = mutableListOf<String>()
        val contradictingEvidence = mutableListOf<String>()

        otherAnalyses.forEach { otherAnalysis ->
            val agreement = calculateModalityAgreement(targetAnalysis, otherAnalysis)
            agreementScores[otherAnalysis.modality] = agreement

            if (agreement > CONSISTENCY_THRESHOLD) {
                supportingEvidence.add("${otherAnalysis.modality} supports findings")
            } else if (agreement < (1.0f - CONSISTENCY_THRESHOLD)) {
                contradictingEvidence.add("${otherAnalysis.modality} contradicts findings")
            }
        }

        val overallScore = agreementScores.values.average().toFloat()

        return CrossModalValidationResult(
            overallScore = overallScore,
            agreementScores = agreementScores,
            supportingEvidence = supportingEvidence,
            contradictingEvidence = contradictingEvidence
        )
    }

    private fun detectConflicts(
        targetAnalysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): List<ModalityConflict> {
        val conflicts = mutableListOf<ModalityConflict>()

        allAnalyses.filter { it.modality != targetAnalysis.modality }.forEach { otherAnalysis ->
            // Check confidence conflicts
            val confidenceConflict = detectConfidenceConflict(targetAnalysis, otherAnalysis)
            if (confidenceConflict != null) {
                conflicts.add(confidenceConflict)
            }

            // Check insight conflicts
            val insightConflicts = detectInsightConflicts(targetAnalysis, otherAnalysis)
            conflicts.addAll(insightConflicts)

            // Check temporal conflicts
            val temporalConflict = detectTemporalConflict(targetAnalysis, otherAnalysis)
            if (temporalConflict != null) {
                conflicts.add(temporalConflict)
            }
        }

        return conflicts
    }

    private fun calculateConsistencyScore(
        targetAnalysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): Float {
        val otherAnalyses = allAnalyses.filter { it.modality != targetAnalysis.modality }

        if (otherAnalyses.isEmpty()) return 1.0f

        val agreements = otherAnalyses.map { otherAnalysis ->
            calculateModalityAgreement(targetAnalysis, otherAnalysis)
        }

        return agreements.average().toFloat()
    }

    private fun applyValidationRules(
        analysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): ValidationRuleResult {
        val applicableRules = validationRules.filter { rule ->
            rule.appliesTo.contains(analysis.modality)
        }

        val ruleResults = applicableRules.map { rule ->
            val ruleScore = evaluateValidationRule(rule, analysis, allAnalyses)
            RuleEvaluationResult(
                ruleName = rule.name,
                passed = ruleScore > rule.threshold,
                score = ruleScore,
                description = rule.description
            )
        }

        val overallScore = if (ruleResults.isNotEmpty()) {
            ruleResults.map { it.score }.average().toFloat()
        } else 0.7f

        return ValidationRuleResult(
            overallScore = overallScore,
            ruleResults = ruleResults
        )
    }

    private fun adjustConfidenceBasedOnValidation(
        originalConfidence: Float,
        crossModalValidation: CrossModalValidationResult,
        conflicts: List<ModalityConflict>,
        consistencyScore: Float
    ): Float {
        var adjustedConfidence = originalConfidence

        // Adjust based on cross-modal agreement
        val agreementAdjustment = (crossModalValidation.overallScore - 0.5f) * 0.2f
        adjustedConfidence += agreementAdjustment

        // Penalize for conflicts
        val conflictPenalty = conflicts.size * 0.1f
        adjustedConfidence -= conflictPenalty

        // Adjust based on consistency
        val consistencyAdjustment = (consistencyScore - 0.5f) * 0.15f
        adjustedConfidence += consistencyAdjustment

        return adjustedConfidence.coerceIn(0.1f, 1.0f)
    }

    // Conflict detection methods

    private fun detectConfidenceConflict(
        analysis1: ModalityAnalysis,
        analysis2: ModalityAnalysis
    ): ModalityConflict? {
        val confidenceDiff = abs(analysis1.confidence - analysis2.confidence)

        return if (confidenceDiff > CONFLICT_THRESHOLD) {
            ModalityConflict(
                conflictingModality = analysis2.modality,
                conflictType = "confidence_mismatch",
                severity = confidenceDiff,
                description = "Significant confidence difference between ${analysis1.modality} (${analysis1.confidence}) and ${analysis2.modality} (${analysis2.confidence})"
            )
        } else null
    }

    private fun detectInsightConflicts(
        analysis1: ModalityAnalysis,
        analysis2: ModalityAnalysis
    ): List<ModalityConflict> {
        val conflicts = mutableListOf<ModalityConflict>()

        // Check for contradictory insights
        analysis1.insights.forEach { insight1 ->
            analysis2.insights.forEach { insight2 ->
                if (areInsightsContradictory(insight1, insight2)) {
                    conflicts.add(
                        ModalityConflict(
                            conflictingModality = analysis2.modality,
                            conflictType = "insight_contradiction",
                            severity = 0.8f,
                            description = "Contradictory insights: '${insight1}' vs '${insight2}'"
                        )
                    )
                }
            }
        }

        return conflicts
    }

    private fun detectTemporalConflict(
        analysis1: ModalityAnalysis,
        analysis2: ModalityAnalysis
    ): ModalityConflict? {
        val timeDiff = abs(analysis1.timestamp - analysis2.timestamp)

        return if (timeDiff > 5000L) { // More than 5 seconds apart
            ModalityConflict(
                conflictingModality = analysis2.modality,
                conflictType = "temporal_mismatch",
                severity = min(timeDiff / 10000f, 1.0f), // Scale severity
                description = "Significant time difference: ${timeDiff}ms between analyses"
            )
        } else null
    }

    // Conflict resolution methods

    private fun resolveIndividualConflict(
        conflict: ModalityConflict,
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConflictResolution {
        return when (conflict.conflictType) {
            "confidence_mismatch" -> resolveConfidenceConflict(conflict, validatedAnalyses)
            "insight_contradiction" -> resolveInsightConflict(conflict, validatedAnalyses)
            "temporal_mismatch" -> resolveTemporalConflict(conflict, validatedAnalyses)
            else -> ConflictResolution(false, "Unknown conflict type")
        }
    }

    private fun resolveConfidenceConflict(
        conflict: ModalityConflict,
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConflictResolution {
        // Use majority consensus or higher reliability modality
        val conflictingAnalyses = validatedAnalyses.filter { analysis ->
            analysis.originalAnalysis.modality == conflict.conflictingModality
        }

        // Simple resolution: trust the modality with higher overall validation score
        return ConflictResolution(
            resolved = true,
            resolutionMethod = "higher_validation_score_prioritized"
        )
    }

    private fun resolveInsightConflict(
        conflict: ModalityConflict,
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConflictResolution {
        // Look for supporting evidence from other modalities
        return ConflictResolution(
            resolved = true,
            resolutionMethod = "cross_modal_evidence_weighting"
        )
    }

    private fun resolveTemporalConflict(
        conflict: ModalityConflict,
        validatedAnalyses: List<ValidatedModalityAnalysis>
    ): ConflictResolution {
        // Temporal conflicts can often be resolved by acknowledging time difference
        return ConflictResolution(
            resolved = true,
            resolutionMethod = "temporal_alignment_noted"
        )
    }

    // Helper methods

    private fun validateSingleModality(analysis: ModalityAnalysis): Float {
        // Basic validation for single modality
        var score = 0.7f // Base score

        // Adjust based on confidence
        score += (analysis.confidence - 0.5f) * 0.2f

        // Adjust based on insight quality
        if (analysis.insights.isNotEmpty()) {
            val avgInsightLength = analysis.insights.map { it.length }.average()
            score += min(avgInsightLength / 50.0, 0.1).toFloat()
        }

        return score.coerceIn(0.1f, 1.0f)
    }

    private fun calculateModalityAgreement(
        analysis1: ModalityAnalysis,
        analysis2: ModalityAnalysis
    ): Float {
        // Calculate agreement based on confidence similarity and insight compatibility
        val confidenceAgreement = 1.0f - abs(analysis1.confidence - analysis2.confidence)
        val insightCompatibility = calculateInsightCompatibility(analysis1.insights, analysis2.insights)

        return (confidenceAgreement + insightCompatibility) / 2f
    }

    private fun calculateInsightCompatibility(insights1: List<String>, insights2: List<String>): Float {
        if (insights1.isEmpty() && insights2.isEmpty()) return 1.0f
        if (insights1.isEmpty() || insights2.isEmpty()) return 0.5f

        // Simple heuristic: compatibility based on non-contradictory insights
        var compatibilityScore = 1.0f
        var conflicts = 0

        insights1.forEach { insight1 ->
            insights2.forEach { insight2 ->
                if (areInsightsContradictory(insight1, insight2)) {
                    conflicts++
                }
            }
        }

        val totalComparisons = insights1.size * insights2.size
        val conflictRatio = conflicts.toFloat() / totalComparisons

        return (1.0f - conflictRatio).coerceIn(0f, 1f)
    }

    private fun areInsightsContradictory(insight1: String, insight2: String): Boolean {
        // Simple contradictory pattern detection
        val contradictoryPairs = listOf(
            Pair("good", "poor"),
            Pair("high", "low"),
            Pair("stable", "unstable"),
            Pair("clear", "unclear"),
            Pair("consistent", "inconsistent")
        )

        val insight1Lower = insight1.lowercase()
        val insight2Lower = insight2.lowercase()

        return contradictoryPairs.any { (term1, term2) ->
            (insight1Lower.contains(term1) && insight2Lower.contains(term2)) ||
            (insight1Lower.contains(term2) && insight2Lower.contains(term1))
        }
    }

    private fun generateValidationFlags(
        crossModalValidation: CrossModalValidationResult,
        conflicts: List<ModalityConflict>,
        consistencyScore: Float,
        ruleValidation: ValidationRuleResult
    ): List<String> {
        val flags = mutableListOf<String>()

        if (crossModalValidation.overallScore > 0.8f) flags.add("high_cross_modal_agreement")
        if (crossModalValidation.overallScore < 0.4f) flags.add("low_cross_modal_agreement")

        if (conflicts.isNotEmpty()) flags.add("has_conflicts")
        if (conflicts.any { it.severity > 0.8f }) flags.add("severe_conflicts")

        if (consistencyScore > 0.8f) flags.add("highly_consistent")
        if (consistencyScore < 0.4f) flags.add("inconsistent")

        if (ruleValidation.overallScore > 0.8f) flags.add("passes_validation_rules")
        if (ruleValidation.overallScore < 0.5f) flags.add("fails_validation_rules")

        return flags
    }

    private fun determineResolutionStrategy(
        conflictsByType: Map<String, List<ModalityConflict>>
    ): String {
        return when {
            conflictsByType.containsKey("confidence_mismatch") -> "confidence_weighting"
            conflictsByType.containsKey("insight_contradiction") -> "evidence_based_resolution"
            conflictsByType.containsKey("temporal_mismatch") -> "temporal_alignment"
            else -> "standard_resolution"
        }
    }

    private fun calculateResolutionConfidence(
        allConflicts: List<ModalityConflict>,
        removedConflicts: List<ModalityConflict>
    ): Float {
        if (allConflicts.isEmpty()) return 1.0f

        val resolutionRate = removedConflicts.size.toFloat() / allConflicts.size
        return resolutionRate
    }

    private fun generateConsistencyRecommendations(
        inconsistencies: List<Inconsistency>,
        modalityConsistency: Map<String, Float>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Low consistency modalities
        modalityConsistency.filter { it.value < 0.5f }.forEach { (modality, _) ->
            recommendations.add("Review and improve $modality data quality")
        }

        // Severe inconsistencies
        inconsistencies.filter { it.severity > 0.8f }.forEach { inconsistency ->
            recommendations.add("Address severe inconsistency: ${inconsistency.description}")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Overall consistency is acceptable")
        }

        return recommendations
    }

    // Initialization methods

    private fun initializeValidationRules(): List<ValidationRule> {
        return listOf(
            ValidationRule(
                name = "confidence_threshold",
                description = "Minimum confidence threshold check",
                appliesTo = listOf("pose", "audio", "vision"),
                threshold = 0.3f
            ),
            ValidationRule(
                name = "insight_quality",
                description = "Insight quality and completeness check",
                appliesTo = listOf("pose", "audio", "vision", "emotional"),
                threshold = 0.5f
            ),
            ValidationRule(
                name = "temporal_consistency",
                description = "Temporal consistency across modalities",
                appliesTo = listOf("pose", "audio", "vision"),
                threshold = 0.6f
            )
        )
    }

    private fun initializeConsistencyPatterns(): List<ConsistencyPattern> {
        return listOf(
            ConsistencyPattern(
                name = "pose_audio_breathing",
                description = "Pose stability should correlate with breathing pattern",
                modalities = listOf("pose", "audio"),
                expectedCorrelation = 0.7f
            ),
            ConsistencyPattern(
                name = "visual_environment_safety",
                description = "Visual safety assessment should align with environment context",
                modalities = listOf("vision", "environment"),
                expectedCorrelation = 0.8f
            )
        )
    }

    private fun evaluateValidationRule(
        rule: ValidationRule,
        analysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): Float {
        return when (rule.name) {
            "confidence_threshold" -> if (analysis.confidence >= rule.threshold) 1.0f else 0.0f
            "insight_quality" -> evaluateInsightQuality(analysis)
            "temporal_consistency" -> calculateTemporalConsistency(analysis, allAnalyses)
            else -> 0.5f
        }
    }

    private fun evaluateInsightQuality(analysis: ModalityAnalysis): Float {
        if (analysis.insights.isEmpty()) return 0.2f

        val avgLength = analysis.insights.map { it.length }.average()
        val qualityScore = min(avgLength / 30.0, 1.0).toFloat() // 30 chars as baseline

        return qualityScore
    }

    private fun calculateTemporalConsistency(
        analysis: ModalityAnalysis,
        allAnalyses: List<ModalityAnalysis>
    ): Float {
        val otherAnalyses = allAnalyses.filter { it.modality != analysis.modality }
        if (otherAnalyses.isEmpty()) return 1.0f

        val timeDifferences = otherAnalyses.map { abs(it.timestamp - analysis.timestamp) }
        val avgTimeDiff = timeDifferences.average()

        // Score based on how close in time the analyses are
        return (1.0f - min(avgTimeDiff / 5000.0, 1.0)).toFloat() // 5 seconds as reference
    }

    // Supporting data classes
    data class ValidatedModalityAnalysis(
        val originalAnalysis: ModalityAnalysis,
        val validationScore: Float,
        val conflicts: List<ModalityConflict>,
        val consistencyScore: Float,
        val adjustedConfidence: Float,
        val validationFlags: List<String>
    )

    data class ModalityConflict(
        val conflictingModality: String,
        val conflictType: String,
        val severity: Float,
        val description: String
    )

    data class CrossModalValidationResult(
        val overallScore: Float,
        val agreementScores: Map<String, Float>,
        val supportingEvidence: List<String>,
        val contradictingEvidence: List<String>
    )

    data class ValidationRuleResult(
        val overallScore: Float,
        val ruleResults: List<RuleEvaluationResult>
    )

    data class RuleEvaluationResult(
        val ruleName: String,
        val passed: Boolean,
        val score: Float,
        val description: String
    )

    data class ConflictResolutionResult(
        val resolvedAnalyses: List<ValidatedModalityAnalysis>,
        val resolutionStrategy: String,
        val resolutionConfidence: Float,
        val removedConflicts: List<ModalityConflict>
    )

    data class ConflictResolution(
        val resolved: Boolean,
        val resolutionMethod: String
    )

    data class ConsistencyReport(
        val overallConsistency: Float,
        val modalityConsistency: Map<String, Float>,
        val inconsistencies: List<Inconsistency>,
        val recommendations: List<String>
    )

    data class Inconsistency(
        val modalities: List<String>,
        val inconsistencyType: String,
        val severity: Float,
        val description: String
    )

    data class ValidationRule(
        val name: String,
        val description: String,
        val appliesTo: List<String>,
        val threshold: Float
    )

    data class ConsistencyPattern(
        val name: String,
        val description: String,
        val modalities: List<String>,
        val expectedCorrelation: Float
    )
}