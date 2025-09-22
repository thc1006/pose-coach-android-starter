package com.posecoach.app.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.posecoach.app.R

/**
 * Enhanced Camera Activity for pose detection
 * Referenced in AndroidManifest.xml but previously missing
 */
class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // TODO: Implement enhanced camera functionality
        // This activity is registered in AndroidManifest.xml but was missing
        // For now, it uses the existing camera layout
    }
}
