#!/bin/bash
# Script to check device status and MediaPipe issues

echo "=== Device Information ==="
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release

echo -e "\n=== Clearing old logs ==="
adb logcat -c

echo -e "\n=== Starting app (please open the Pose Coach app now) ==="
echo "Waiting 5 seconds for app to start..."
sleep 5

echo -e "\n=== MediaPipe Initialization Logs ==="
adb logcat -d | grep -E "MediaPipe|PoseLandmarker|PoseDetection" | head -30

echo -e "\n=== Error Messages ==="
adb logcat -d | grep -E "Error|Failed|Exception" | grep -E "mediapipe|pose|tensor" -i | head -20

echo -e "\n=== Library Loading Logs ==="
adb logcat -d | grep -E "ReLinker|loadLibrary|UnsatisfiedLinkError" | head -20

echo -e "\n=== Timber Logs ==="
adb logcat -d | grep "Timber" | grep -E "Pose|MediaPipe|initialized" | head -20