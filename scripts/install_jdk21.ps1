# PowerShell script to install OpenJDK 21
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installing OpenJDK 21 for APK Generation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if running as administrator
if (-NOT ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Write-Host "ERROR: This script requires administrator privileges." -ForegroundColor Red
    Write-Host "Please run PowerShell as Administrator and try again." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

# Function to test Java installation
function Test-JavaInstallation {
    try {
        $javaVersion = & java -version 2>&1 | Select-String "version"
        $javacVersion = & javac -version 2>&1
        if ($javacVersion) {
            return $true
        }
    } catch {
        return $false
    }
    return $false
}

Write-Host "Step 1: Checking current Java installation..." -ForegroundColor Yellow
if (Test-JavaInstallation) {
    Write-Host "JDK appears to be already installed. Checking version..." -ForegroundColor Green
    & java -version
    & javac -version
    $response = Read-Host "Do you want to continue with reinstallation? (y/n)"
    if ($response -ne 'y') {
        exit 0
    }
}

Write-Host ""
Write-Host "Step 2: Installing OpenJDK 21 via Chocolatey..." -ForegroundColor Yellow

# Check if Chocolatey is installed
if (!(Get-Command choco -ErrorAction SilentlyContinue)) {
    Write-Host "Chocolatey not found. Installing Chocolatey first..." -ForegroundColor Magenta
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
}

# Try Microsoft OpenJDK first
Write-Host "Installing Microsoft OpenJDK 21..." -ForegroundColor Cyan
$result = choco install microsoft-openjdk21 -y 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "Microsoft OpenJDK installation failed. Trying Temurin..." -ForegroundColor Yellow
    $result = choco install temurin21 -y 2>&1
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to install JDK via Chocolatey" -ForegroundColor Red
    Write-Host "Please download manually from: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Step 3: Configuring environment variables..." -ForegroundColor Yellow

# Find Java installation path
$javaPaths = @(
    "C:\Program Files\Microsoft\jdk-21*",
    "C:\Program Files\Eclipse Adoptium\jdk-21*",
    "C:\Program Files\Eclipse Adoptium\temurin-21*",
    "C:\Program Files\Java\jdk-21*",
    "C:\Program Files\OpenJDK\jdk-21*"
)

$javaHome = $null
foreach ($path in $javaPaths) {
    $found = Get-ChildItem -Path (Split-Path $path) -Filter (Split-Path $path -Leaf) -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        $javaHome = $found.FullName
        break
    }
}

if (!$javaHome) {
    Write-Host "ERROR: Could not find JDK 21 installation path" -ForegroundColor Red
    Write-Host "Please set JAVA_HOME manually to your JDK installation directory" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Found JDK at: $javaHome" -ForegroundColor Green

# Set JAVA_HOME
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, [System.EnvironmentVariableTarget]::Machine)
Write-Host "JAVA_HOME set to: $javaHome" -ForegroundColor Green

# Update PATH
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
$javaBin = "$javaHome\bin"

if ($currentPath -notlike "*$javaBin*") {
    $newPath = "$javaBin;$currentPath"
    [System.Environment]::SetEnvironmentVariable("Path", $newPath, [System.EnvironmentVariableTarget]::Machine)
    Write-Host "Added to PATH: $javaBin" -ForegroundColor Green
} else {
    Write-Host "JDK bin already in PATH" -ForegroundColor Green
}

# Refresh environment variables in current session
$env:JAVA_HOME = $javaHome
$env:Path = "$javaBin;$env:Path"

Write-Host ""
Write-Host "Step 4: Verifying installation..." -ForegroundColor Yellow
Write-Host ""

& "$javaHome\bin\java.exe" -version
Write-Host ""
& "$javaHome\bin\javac.exe" -version

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installation Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANT: Close and reopen your terminal/IDE for changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Close this terminal" -ForegroundColor White
Write-Host "2. Open a new terminal" -ForegroundColor White
Write-Host "3. Navigate to: C:\Users\thc1006\Desktop\dev\pose-coach-android-starter" -ForegroundColor White
Write-Host "4. Run: .\gradlew app:assembleDebug" -ForegroundColor White
Write-Host ""
Read-Host "Press Enter to exit"