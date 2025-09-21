package com.posecoach.corepose.repository

import com.posecoach.corepose.models.PoseDetectionError
import com.posecoach.corepose.models.PoseLandmarkResult

interface PoseDetectionListener {
    fun onPoseDetected(result: PoseLandmarkResult)
    fun onPoseDetectionError(error: PoseDetectionError)
}