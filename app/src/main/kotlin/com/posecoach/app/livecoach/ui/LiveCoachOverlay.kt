package com.posecoach.app.livecoach.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.posecoach.app.R
import com.posecoach.app.livecoach.models.ConnectionState
import kotlinx.coroutines.*

class LiveCoachOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface OnLiveCoachListener {
        fun onStartSession()
        fun onStopSession()
        fun onRetryConnection()
    }

    private var listener: OnLiveCoachListener? = null
    private var currentConnectionState = ConnectionState.DISCONNECTED

    // UI Components
    private lateinit var pushToTalkButton: PushToTalkButton
    private lateinit var statusCard: CardView
    private lateinit var statusText: TextView
    private lateinit var responseCard: CardView
    private lateinit var responseText: TextView
    private lateinit var transcriptionCard: CardView
    private lateinit var transcriptionText: TextView

    // Animation and coroutines
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var hideResponseJob: Job? = null

    init {
        setupView()
    }

    private fun setupView() {
        // Create main layout
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        // Status card (top)
        statusCard = CardView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 32
                topMargin = 32
                rightMargin = 32
            }
            cardBackgroundColor = Color.parseColor("#E0000000") // Semi-transparent black
            radius = 12f
            cardElevation = 8f
        }

        statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 16, 24, 16)
            }
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "AI Coach: Disconnected"
        }

        statusCard.addView(statusText)

        // Response card (center-top)
        responseCard = CardView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 32
                topMargin = 120
                rightMargin = 32
            }
            cardBackgroundColor = Color.parseColor("#E0000000")
            radius = 12f
            cardElevation = 8f
            isVisible = false
        }

        responseText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 20, 24, 20)
            }
            setTextColor(Color.WHITE)
            textSize = 16f
            lineSpacing = 4f, 1.2f
        }

        responseCard.addView(responseText)

        // Transcription card (center-bottom)
        transcriptionCard = CardView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 32
                rightMargin = 32
                bottomMargin = 200 // Space for push-to-talk button
                gravity = android.view.Gravity.BOTTOM
            }
            cardBackgroundColor = Color.parseColor("#B0000000") // More transparent
            radius = 12f
            cardElevation = 4f
            isVisible = false
        }

        transcriptionText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(20, 16, 20, 16)
            }
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 14f
            maxLines = 3
        }

        transcriptionCard.addView(transcriptionText)

        // Push-to-talk button (bottom center)
        pushToTalkButton = PushToTalkButton(context).apply {
            layoutParams = LayoutParams(
                200,
                200
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 50
            }
        }

        setupPushToTalkListener()

        // Add all views
        addView(statusCard)
        addView(responseCard)
        addView(transcriptionCard)
        addView(pushToTalkButton)

        updateConnectionState(ConnectionState.DISCONNECTED)
    }

    private fun setupPushToTalkListener() {
        pushToTalkButton.setOnPushToTalkListener(object : PushToTalkButton.OnPushToTalkListener {
            override fun onStartTalking() {
                when (currentConnectionState) {
                    ConnectionState.DISCONNECTED -> {
                        listener?.onStartSession()
                    }
                    ConnectionState.CONNECTED -> {
                        // Already handled by button, session should be active
                    }
                    ConnectionState.ERROR -> {
                        listener?.onRetryConnection()
                    }
                    else -> {
                        // Connecting states - do nothing
                    }
                }
            }

            override fun onStopTalking() {
                // For now, we keep the session open even after releasing
                // User can manually disconnect if needed
            }

            override fun onConnectionStateChanged(isConnected: Boolean) {
                // This could trigger additional UI changes if needed
            }
        })
    }

    fun setOnLiveCoachListener(listener: OnLiveCoachListener?) {
        this.listener = listener
    }

    fun updateConnectionState(state: ConnectionState) {
        currentConnectionState = state
        pushToTalkButton.updateConnectionState(state)

        val statusMessage = when (state) {
            ConnectionState.DISCONNECTED -> "AI Coach: Disconnected"
            ConnectionState.CONNECTING -> "AI Coach: Connecting..."
            ConnectionState.CONNECTED -> "AI Coach: Ready"
            ConnectionState.RECONNECTING -> "AI Coach: Reconnecting..."
            ConnectionState.ERROR -> "AI Coach: Connection Error"
        }

        statusText.text = statusMessage

        val statusColor = when (state) {
            ConnectionState.CONNECTED -> Color.parseColor("#E0388E3C") // Green
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color.parseColor("#E0FF9800") // Orange
            ConnectionState.ERROR -> Color.parseColor("#E0F44336") // Red
            else -> Color.parseColor("#E0000000") // Black
        }

        statusCard.setCardBackgroundColor(statusColor)
    }

    fun setRecording(isRecording: Boolean) {
        pushToTalkButton.setRecording(isRecording)

        if (isRecording) {
            showTranscription("Listening...")
        }
    }

    fun showCoachingResponse(response: String) {
        responseText.text = response

        if (!responseCard.isVisible) {
            responseCard.isVisible = true
            animateCardIn(responseCard)
        }

        // Auto-hide after delay
        hideResponseJob?.cancel()
        hideResponseJob = uiScope.launch {
            delay(8000) // Hide after 8 seconds
            hideCoachingResponse()
        }
    }

    fun hideCoachingResponse() {
        if (responseCard.isVisible) {
            animateCardOut(responseCard) {
                responseCard.isVisible = false
            }
        }
    }

    fun showTranscription(text: String) {
        transcriptionText.text = text

        if (!transcriptionCard.isVisible) {
            transcriptionCard.isVisible = true
            animateCardIn(transcriptionCard)
        }

        // Auto-hide transcription after shorter delay
        uiScope.launch {
            delay(4000)
            hideTranscription()
        }
    }

    fun hideTranscription() {
        if (transcriptionCard.isVisible) {
            animateCardOut(transcriptionCard) {
                transcriptionCard.isVisible = false
            }
        }
    }

    fun showError(error: String) {
        responseText.text = "Error: $error"
        responseCard.setCardBackgroundColor(Color.parseColor("#E0F44336")) // Red

        if (!responseCard.isVisible) {
            responseCard.isVisible = true
            animateCardIn(responseCard)
        }

        // Auto-hide error after longer delay
        hideResponseJob?.cancel()
        hideResponseJob = uiScope.launch {
            delay(6000)
            hideCoachingResponse()
            // Reset color
            responseCard.setCardBackgroundColor(Color.parseColor("#E0000000"))
        }
    }

    private fun animateCardIn(card: View) {
        card.alpha = 0f
        card.scaleX = 0.8f
        card.scaleY = 0.8f

        val fadeIn = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(card, "scaleX", 0.8f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(card, "scaleY", 0.8f, 1f)

        AnimatorSet().apply {
            playTogether(fadeIn, scaleXIn, scaleYIn)
            duration = 300
            start()
        }
    }

    private fun animateCardOut(card: View, onComplete: () -> Unit) {
        val fadeOut = ObjectAnimator.ofFloat(card, "alpha", 1f, 0f)
        val scaleXOut = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.8f)
        val scaleYOut = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.8f)

        AnimatorSet().apply {
            playTogether(fadeOut, scaleXOut, scaleYOut)
            duration = 200
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
            start()
        }
    }

    fun clear() {
        hideCoachingResponse()
        hideTranscription()
        updateConnectionState(ConnectionState.DISCONNECTED)
        setRecording(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideResponseJob?.cancel()
        uiScope.cancel()
    }
}