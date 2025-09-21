package com.posecoach.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for MainActivity
 * Following CLAUDE.md TDD requirements
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    @Test
    fun testMainActivityLaunch() {
        // Test that MainActivity can be launched without crashing
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity).isNotNull()
                assertThat(activity.isFinishing).isFalse()
            }
        }
    }

    @Test
    fun testCameraPreviewInitialization() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Verify camera preview view exists
                val previewView = activity.findViewById<androidx.camera.view.PreviewView>(
                    com.posecoach.R.id.camera_preview
                )
                assertThat(previewView).isNotNull()
            }
        }
    }

    @Test
    fun testPoseOverlayInitialization() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Verify pose overlay view exists
                val overlayView = activity.findViewById<com.posecoach.app.overlay.PoseOverlayView>(
                    com.posecoach.R.id.pose_overlay
                )
                assertThat(overlayView).isNotNull()
            }
        }
    }

    @Test
    fun testSuggestionsPanel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Verify suggestions panel exists
                val suggestionsPanel = activity.findViewById<android.view.View>(
                    com.posecoach.R.id.suggestions_panel
                )
                assertThat(suggestionsPanel).isNotNull()

                // Verify it starts hidden as per design
                assertThat(suggestionsPanel.visibility).isEqualTo(android.view.View.GONE)
            }
        }
    }
}