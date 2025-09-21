package com.posecoach.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.posecoach.R

/**
 * Main Live Coaching Activity
 * Referenced in AndroidManifest.xml but previously missing
 */
class LiveCoachingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_coaching)

        // TODO: Implement live coaching functionality
        // This activity is registered in AndroidManifest.xml but was missing
        // It uses the existing live coaching layout
    }
}
