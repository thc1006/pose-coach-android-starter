@echo off
echo === Testing ML Kit Pose Detection on Pixel 7 ===
echo.

echo === Building APK with ML Kit ===
call gradlew clean assembleDebug
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo.
echo === Checking APK for 16KB alignment ===
echo Expected: ML Kit libraries should be 16KB aligned
echo.

echo === Uninstalling old version ===
adb uninstall com.posecoach.camera

echo.
echo === Installing new APK ===
adb install app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
if errorlevel 1 (
    echo Installation failed!
    echo Checking for 16KB alignment issues...
    exit /b 1
)

echo.
echo === Clearing logcat ===
adb logcat -c

echo.
echo === Starting app ===
adb shell am start -n com.posecoach.camera/.app.MainActivity

echo.
echo === Waiting for ML Kit initialization (5 seconds) ===
timeout /t 5 /nobreak > nul

echo.
echo === Checking ML Kit initialization ===
adb logcat -d | findstr /I "MLKitPoseDetector ML Kit initialized successfully"

echo.
echo === Checking for pose detection ===
adb logcat -d | findstr /I "ML Kit detected pose landmarks emitted"

echo.
echo === Checking for errors ===
adb logcat -d | findstr /I "error failed exception 16KB"

echo.
echo === Live monitoring (press Ctrl+C to stop) ===
echo Looking for: ML Kit pose detection events
adb logcat *:S Timber:V | findstr /I "ML Kit Pose landmarks detected"