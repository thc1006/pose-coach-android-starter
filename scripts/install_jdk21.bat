@echo off
echo ========================================
echo Installing OpenJDK 21 for APK Generation
echo ========================================
echo.

REM Check if running as administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: This script requires administrator privileges.
    echo Please run Command Prompt as Administrator and try again.
    pause
    exit /b 1
)

echo Step 1: Installing OpenJDK 21 via Chocolatey...
echo ------------------------------------------------
choco install microsoft-openjdk21 -y

if %errorLevel% neq 0 (
    echo.
    echo Alternative: Trying Temurin OpenJDK 21...
    choco install temurin21 -y
)

echo.
echo Step 2: Setting up environment variables...
echo -------------------------------------------

REM Find Java installation path
set JAVA_INSTALL_PATH=
if exist "C:\Program Files\Microsoft\jdk-21*" (
    for /d %%i in ("C:\Program Files\Microsoft\jdk-21*") do set JAVA_INSTALL_PATH=%%i
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21*" (
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set JAVA_INSTALL_PATH=%%i
) else if exist "C:\Program Files\Java\jdk-21*" (
    for /d %%i in ("C:\Program Files\Java\jdk-21*") do set JAVA_INSTALL_PATH=%%i
)

if "%JAVA_INSTALL_PATH%"=="" (
    echo ERROR: Could not find JDK 21 installation path
    echo Please install manually from: https://adoptium.net/
    pause
    exit /b 1
)

echo Found JDK at: %JAVA_INSTALL_PATH%

REM Set JAVA_HOME system environment variable
setx /M JAVA_HOME "%JAVA_INSTALL_PATH%" >nul 2>&1
echo JAVA_HOME set to: %JAVA_INSTALL_PATH%

REM Add to PATH if not already there
echo %PATH% | findstr /i "%JAVA_INSTALL_PATH%\bin" >nul
if %errorLevel% neq 0 (
    setx /M PATH "%JAVA_INSTALL_PATH%\bin;%PATH%" >nul 2>&1
    echo Added to PATH: %JAVA_INSTALL_PATH%\bin
) else (
    echo JDK bin already in PATH
)

echo.
echo Step 3: Verifying installation...
echo ----------------------------------
"%JAVA_INSTALL_PATH%\bin\java" -version
echo.
"%JAVA_INSTALL_PATH%\bin\javac" -version

echo.
echo ========================================
echo Installation Complete!
echo ========================================
echo.
echo IMPORTANT: Please close and reopen your terminal/IDE for changes to take effect.
echo.
echo Next steps:
echo 1. Close this terminal
echo 2. Open a new terminal
echo 3. Run: gradlew app:assembleDebug
echo.
pause