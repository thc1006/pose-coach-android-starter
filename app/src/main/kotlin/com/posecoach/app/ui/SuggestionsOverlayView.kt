package com.posecoach.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.posecoach.suggestions.models.PoseSuggestion
import timber.log.Timber

class SuggestionsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val suggestionsCard: MaterialCardView
    private val suggestionsRecycler: RecyclerView
    private val retryButton: MaterialButton
    private val loadingView: View
    private val adapter: SuggestionsAdapter

    private var onRetryListener: (() -> Unit)? = null
    private var isLoading = false

    init {
        val rootView = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        suggestionsCard = MaterialCardView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(16, 16, 16, 16)
            }
            cardElevation = 8f
            radius = 16f
            setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            alpha = 0.95f
            visibility = View.GONE
        }

        val cardContent = FrameLayout(context).apply {
            layoutParams = LayoutParams(350, LayoutParams.WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
        }

        suggestionsRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }

        adapter = SuggestionsAdapter()
        suggestionsRecycler.adapter = adapter

        retryButton = MaterialButton(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = 8
            }
            text = "Retry"
            visibility = View.GONE
            setOnClickListener {
                onRetryListener?.invoke()
            }
        }

        loadingView = View(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                40
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            visibility = View.GONE
        }

        cardContent.addView(suggestionsRecycler)
        cardContent.addView(retryButton)
        cardContent.addView(loadingView)

        suggestionsCard.addView(cardContent)
        rootView.addView(suggestionsCard)

        addView(rootView)
    }

    fun showSuggestions(suggestions: List<PoseSuggestion>) {
        post {
            adapter.updateSuggestions(suggestions)
            suggestionsCard.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            loadingView.visibility = View.GONE
            isLoading = false

            val slideIn = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
            suggestionsCard.startAnimation(slideIn)

            Timber.d("Showing ${suggestions.size} suggestions")
        }
    }

    fun showLoading() {
        post {
            suggestionsCard.visibility = View.VISIBLE
            loadingView.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            isLoading = true

            loadingView.animate()
                .alpha(0.3f)
                .setDuration(500)
                .withEndAction {
                    if (isLoading) {
                        loadingView.animate()
                            .alpha(1.0f)
                            .setDuration(500)
                            .start()
                    }
                }
                .start()
        }
    }

    fun showError(message: String) {
        post {
            suggestionsCard.visibility = View.VISIBLE
            loadingView.visibility = View.GONE
            retryButton.visibility = View.VISIBLE
            isLoading = false

            adapter.showError(message)
            Timber.e("Showing error: $message")
        }
    }

    fun hide() {
        post {
            val fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
            suggestionsCard.startAnimation(fadeOut)
            suggestionsCard.visibility = View.GONE
            isLoading = false
        }
    }

    fun setOnRetryListener(listener: () -> Unit) {
        onRetryListener = listener
    }

    fun clear() {
        adapter.clear()
        hide()
    }

    private inner class SuggestionsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var suggestions = listOf<PoseSuggestion>()
        private var errorMessage: String? = null

        fun updateSuggestions(newSuggestions: List<PoseSuggestion>) {
            suggestions = newSuggestions.take(3)
            errorMessage = null
            notifyDataSetChanged()
        }

        fun showError(message: String) {
            errorMessage = message
            suggestions = emptyList()
            notifyDataSetChanged()
        }

        fun clear() {
            suggestions = emptyList()
            errorMessage = null
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = when {
            errorMessage != null -> 1
            else -> suggestions.size
        }

        override fun getItemViewType(position: Int): Int {
            return if (errorMessage != null) 1 else 0  // 1 for error, 0 for suggestion
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                1 -> ErrorViewHolder(createErrorView(parent))  // error view type
                else -> SuggestionViewHolder(createSuggestionView(parent))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is SuggestionViewHolder -> holder.bind(suggestions[position], position + 1)
                is ErrorViewHolder -> holder.bind(errorMessage ?: "Unknown error")
            }
        }

        private fun createSuggestionView(parent: android.view.ViewGroup): View {
            return FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                setPadding(12, 8, 12, 8)
            }
        }

        private fun createErrorView(parent: android.view.ViewGroup): View {
            return FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(12, 8, 12, 8)
            }
        }
    }

    private class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        private val instructionPaint = Paint().apply {
            color = Color.DKGRAY  // Using DKGRAY instead of DARK_GRAY
            textSize = 24f
            isAntiAlias = true
        }

        fun bind(suggestion: PoseSuggestion, number: Int) {
            val frameLayout = itemView as FrameLayout
            frameLayout.removeAllViews()

            frameLayout.setOnClickListener(null)
            frameLayout.isClickable = false

            frameLayout.post {
                val bitmap = Bitmap.createBitmap(
                    frameLayout.width.coerceAtLeast(1),
                    80,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)

                val circleRadius = 15f
                val circlePaint = Paint().apply {
                    color = Color.parseColor("#4CAF50")
                    isAntiAlias = true
                }
                canvas.drawCircle(circleRadius + 5, 25f, circleRadius, circlePaint)

                val numberPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 20f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText(number.toString(), circleRadius + 5, 32f, numberPaint)

                canvas.drawText(suggestion.title, 40f, 25f, titlePaint)

                val words = suggestion.instruction.split(" ")
                var line = ""
                var y = 55f
                words.forEach { word ->
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (instructionPaint.measureText(testLine) > frameLayout.width - 50) {
                        canvas.drawText(line, 40f, y, instructionPaint)
                        line = word
                        y += 25f
                    } else {
                        line = testLine
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, 40f, y, instructionPaint)
                }

                val imageView = android.widget.ImageView(frameLayout.context).apply {
                    setImageBitmap(bitmap)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                frameLayout.addView(imageView)
            }
        }
    }

    private class ErrorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(errorMessage: String) {
            val frameLayout = itemView as FrameLayout
            frameLayout.removeAllViews()

            val textView = android.widget.TextView(frameLayout.context).apply {
                text = "⚠️ $errorMessage"
                textSize = 14f
                setTextColor(Color.parseColor("#D32F2F"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            frameLayout.addView(textView)
        }
    }
}