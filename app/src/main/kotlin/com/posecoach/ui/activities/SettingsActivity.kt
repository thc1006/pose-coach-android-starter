package com.posecoach.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.posecoach.app.R

/**
 * Settings Activity
 * Referenced in AndroidManifest.xml but previously missing
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Implement settings UI
        // This activity is registered in AndroidManifest.xml but was missing
        // For now, it's a minimal implementation

        // Set up action bar with parent activity
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}