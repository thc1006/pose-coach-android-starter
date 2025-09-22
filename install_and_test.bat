@echo off
echo === Uninstalling old version ===
adb uninstall com.posecoach.camera

echo.
echo === Installing new APK for Pixel 7 (arm64) ===
adb install app\build\outputs\apk\debug\app-arm64-v8a-debug.apk

echo.
echo === Clearing logcat ===
adb logcat -c

echo.
echo === Starting app ===
adb shell am start -n com.posecoach.camera/.app.MainActivity

echo.
echo === Waiting for app to initialize (5 seconds) ===
timeout /t 5 /nobreak > nul

echo.
echo === Checking MediaPipe initialization ===
adb logcat -d | findstr /I "SimplePoseDetector MediaPipe initialized"

echo.
echo === Checking for errors ===
adb logcat -d | findstr /I "error failed exception"

echo.
echo === Live monitoring (press Ctrl+C to stop) ===
adb logcat | findstr /I "Pose Landmark Timber detected"