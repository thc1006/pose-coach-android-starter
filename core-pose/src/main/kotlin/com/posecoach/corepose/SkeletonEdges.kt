package com.posecoach.corepose

/** Default skeleton edges (pairs of landmark indices). */
object SkeletonEdges {
    val DEFAULT = listOf(
        PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.RIGHT_SHOULDER,
        PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_ELBOW,
        PoseLandmarks.LEFT_ELBOW to PoseLandmarks.LEFT_WRIST,
        PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_ELBOW,
        PoseLandmarks.RIGHT_ELBOW to PoseLandmarks.RIGHT_WRIST,
        PoseLandmarks.LEFT_HIP to PoseLandmarks.RIGHT_HIP,
        PoseLandmarks.LEFT_HIP to PoseLandmarks.LEFT_KNEE,
        PoseLandmarks.LEFT_KNEE to PoseLandmarks.LEFT_ANKLE,
        PoseLandmarks.RIGHT_HIP to PoseLandmarks.RIGHT_KNEE,
        PoseLandmarks.RIGHT_KNEE to PoseLandmarks.RIGHT_ANKLE,
        PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_HIP,
        PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_HIP
    )
}
