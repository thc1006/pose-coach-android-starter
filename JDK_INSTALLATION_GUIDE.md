# 📦 OpenJDK 21 Installation Guide for Windows

## Quick Installation Methods

### Option 1: Using Chocolatey (Recommended) 🍫
Run in **Administrator PowerShell or Command Prompt**:

```powershell
# Install Microsoft OpenJDK 21
choco install microsoft-openjdk21 -y

# OR install Temurin OpenJDK 21
choco install temurin21 -y
```

### Option 2: Using the Provided Script 🚀
1. Open **PowerShell as Administrator**
2. Navigate to project directory:
   ```powershell
   cd C:\Users\thc1006\Desktop\dev\pose-coach-android-starter
   ```
3. Run the installation script:
   ```powershell
   Set-ExecutionPolicy Bypass -Process
   .\scripts\install_jdk21.ps1
   ```

### Option 3: Manual Download and Install 📥

1. **Download OpenJDK 21** from one of these sources:
   - [Microsoft OpenJDK](https://www.microsoft.com/openjdk)
   - [Eclipse Temurin (Adoptium)](https://adoptium.net/temurin/releases/?version=21)
   - [Oracle OpenJDK](https://jdk.java.net/21/)

2. **Choose the installer**:
   - Windows x64 MSI Installer (easiest)
   - Windows x64 ZIP (manual setup required)

3. **Install JDK**:
   - Run the MSI installer
   - Default installation path: `C:\Program Files\Eclipse Adoptium\jdk-21.x.x`
   - ✅ Check "Add to PATH" if available
   - ✅ Check "Set JAVA_HOME" if available

## 🔧 Configure Environment Variables

### Set JAVA_HOME
1. Open **System Properties** → **Environment Variables**
2. Click **New** under System variables
3. Variable name: `JAVA_HOME`
4. Variable value: `C:\Program Files\Eclipse Adoptium\jdk-21.0.x` (your JDK path)
5. Click **OK**

### Update PATH
1. Find **Path** in System variables
2. Click **Edit**
3. Click **New**
4. Add: `%JAVA_HOME%\bin`
5. Click **OK**

## ✅ Verify Installation

Open a **new** Command Prompt or PowerShell and run:

```bash
# Check Java version
java -version

# Check Java compiler (this should now work!)
javac -version

# Check environment variable
echo %JAVA_HOME%
```

Expected output:
```
openjdk version "21.0.x"
OpenJDK Runtime Environment Temurin-21.0.x
OpenJDK 64-Bit Server VM Temurin-21.0.x

javac 21.0.x
```

## 🏗️ Build Your APK

After installation, in a **new terminal**:

```bash
cd C:\Users\thc1006\Desktop\dev\pose-coach-android-starter

# Clean and build
.\gradlew clean app:assembleDebug

# The APK will be generated at:
# app\build\outputs\apk\debug\app-debug.apk
```

## 🔍 Troubleshooting

### Issue: 'javac' is not recognized
**Solution**: Ensure you installed JDK (not just JRE) and PATH is set correctly

### Issue: JAVA_HOME not set
**Solution**: Set JAVA_HOME environment variable to JDK installation directory

### Issue: Build still fails
**Solution**:
1. Close all terminals/IDEs
2. Reopen terminal
3. Verify with `javac -version`
4. Try: `.\gradlew --stop` then rebuild

### Issue: Wrong Java version detected
**Solution**:
1. Check PATH order - JDK 21 should come before other Java installations
2. Uninstall old Java versions if not needed
3. Use `where java` to see which Java is being used

## 📱 Testing the APK

Once built successfully:
1. APK location: `app\build\outputs\apk\debug\app-debug.apk`
2. Install on device: `adb install app\build\outputs\apk\debug\app-debug.apk`
3. Or copy APK to device and install manually

## ✨ Verification for 16KB Alignment

After building, verify 16KB compatibility:
```bash
# Check APK alignment
.\gradlew verify16KBAlignment
```

The app should now work on Android 15+ devices with 16KB page sizes!

---

**Need help?** The JDK installation is crucial for Android development. Once installed, you'll be able to build APKs for testing and deployment.