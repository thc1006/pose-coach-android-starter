package com.posecoach.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.posecoach.R

/**
 * Real-time coaching suggestions overlay
 * Features:
 * - Voice-triggered coaching tips
 * - Pose correction suggestions
 * - Animated appearance/disappearance
 * - Priority-based display
 */
class CoachingSuggestionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SuggestionsAdapter
    private var currentSuggestions: List<CoachingSuggestion> = emptyList()

    // Animation durations
    private val fadeInDuration = 300L
    private val fadeOutDuration = 200L
    private val autoHideDuration = 5000L // 5 seconds

    // Auto-hide runnable
    private val autoHideRunnable = Runnable { hideSuggestions() }

    init {
        setupView()
    }

    private fun setupView() {
        // Configure card appearance
        radius = 16f
        elevation = 8f
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.suggestions_background))

        // Create RecyclerView
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SuggestionsAdapter()
            setPadding(16, 16, 16, 16)
        }

        this.adapter = recyclerView.adapter as SuggestionsAdapter
        addView(recyclerView)

        // Initially hidden
        visibility = View.GONE
        alpha = 0f
    }

    fun showSuggestions(suggestions: List<CoachingSuggestion>) {
        currentSuggestions = suggestions.sortedByDescending { it.priority }
        adapter.updateSuggestions(currentSuggestions)

        // Cancel any pending auto-hide
        removeCallbacks(autoHideRunnable)

        if (visibility == View.GONE) {
            // Animate in
            visibility = View.VISIBLE
            val fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 0.8f, 1f)
            val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 0.8f, 1f)

            AnimatorSet().apply {
                playTogether(fadeIn, scaleX, scaleY)
                duration = fadeInDuration
                start()
            }
        }

        // Auto-hide after delay
        postDelayed(autoHideRunnable, autoHideDuration)
    }

    fun hideSuggestions() {
        if (visibility == View.VISIBLE) {
            val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
            fadeOut.duration = fadeOutDuration
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = View.GONE
                }
            })
            fadeOut.start()
        }
    }

    fun addSuggestion(suggestion: CoachingSuggestion) {
        val updatedSuggestions = currentSuggestions + suggestion
        showSuggestions(updatedSuggestions)
    }

    fun clearSuggestions() {
        currentSuggestions = emptyList()
        adapter.updateSuggestions(currentSuggestions)
        hideSuggestions()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(autoHideRunnable)
    }

    /**
     * RecyclerView adapter for coaching suggestions
     */
    private class SuggestionsAdapter : RecyclerView.Adapter<SuggestionViewHolder>() {

        private var suggestions: List<CoachingSuggestion> = emptyList()

        fun updateSuggestions(newSuggestions: List<CoachingSuggestion>) {
            suggestions = newSuggestions
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_coaching_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            holder.bind(suggestions[position])
        }

        override fun getItemCount(): Int = suggestions.size
    }

    /**
     * ViewHolder for individual suggestions
     */
    private class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.suggestionMessage)
        private val typeIndicator: View = itemView.findViewById(R.id.typeIndicator)

        fun bind(suggestion: CoachingSuggestion) {
            messageText.text = suggestion.message

            // Set type indicator color
            val color = when (suggestion.type) {
                SuggestionType.POSE_CORRECTION -> ContextCompat.getColor(itemView.context, R.color.suggestion_pose)
                SuggestionType.BREATHING -> ContextCompat.getColor(itemView.context, R.color.suggestion_breathing)
                SuggestionType.FORM_IMPROVEMENT -> ContextCompat.getColor(itemView.context, R.color.suggestion_form)
                SuggestionType.ENCOURAGEMENT -> ContextCompat.getColor(itemView.context, R.color.suggestion_encouragement)
                SuggestionType.WARNING -> ContextCompat.getColor(itemView.context, R.color.suggestion_warning)
            }
            typeIndicator.setBackgroundColor(color)

            // Animate item appearance
            itemView.alpha = 0f
            itemView.translationY = 20f
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setStartDelay(adapterPosition * 50L)
                .start()
        }
    }
}

/**
 * Data class for coaching suggestions
 */
data class CoachingSuggestion(
    val message: String,
    val type: SuggestionType,
    val priority: Int = 0, // Higher numbers = higher priority
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 5000L, // How long to display
    val source: SuggestionSource = SuggestionSource.AI_COACH
)

/**
 * Types of coaching suggestions
 */
enum class SuggestionType {
    POSE_CORRECTION,    // Specific pose adjustments
    BREATHING,          // Breathing techniques
    FORM_IMPROVEMENT,   // General form improvements
    ENCOURAGEMENT,      // Motivational messages
    WARNING            // Safety warnings
}

/**
 * Source of the suggestion
 */
enum class SuggestionSource {
    AI_COACH,          // From Gemini Live API
    POSE_ANALYSIS,     // From pose detection system
    USER_PREFERENCE,   // Based on user settings
    SAFETY_SYSTEM      // Safety-related suggestions
}