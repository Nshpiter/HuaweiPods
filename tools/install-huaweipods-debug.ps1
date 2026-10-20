param(
    [string]$AdbPath = "",
    [string]$SdkDir = "",
    [string]$ApkPath = "",
    [string]$PackageName = "moe.chenxy.huaweipods.coloros",
    [switch]$RestartBluetoothWithRoot
)

$ErrorActionPreference = "Stop"

function Resolve-Adb {
    param([string]$AdbPath, [string]$SdkDir)

    $candidates = @()
    if ($AdbPath) { $candidates += $AdbPath }
    if ($SdkDir) { $candidates += (Join-Path $SdkDir "platform-tools/adb.exe") }
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools/adb.exe") }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb.exe") }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe") }
    if ($env:USERPROFILE) { $candidates += (Join-Path $env:USERPROFILE "AppData/Local/Android/Sdk/platform-tools/adb.exe") }
    $repoRootFromScript = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
    $localProperties = Join-Path $repoRootFromScript "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        foreach ($line in Get-Content -LiteralPath $localProperties) {
            if ($line -match "^\s*sdk\.dir\s*=\s*(.+?)\s*$") {
                $sdkDirFromLocal = $Matches[1].Trim().Replace("\:", ":").Replace("\\", "\")
                $candidates += (Join-Path $sdkDirFromLocal "platform-tools/adb.exe")
                break
            }
        }
    }
    $candidates += "adb.exe"

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $resolved = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Source }
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    }

    throw "adb.exe not found. Pass -AdbPath or -SdkDir."
}

function Wait-AuthorizedDevice {
    param([string]$Adb)

    & $Adb start-server | Out-Host
    for ($i = 0; $i -lt 6; $i++) {
        $devicesOutput = & $Adb devices
        $readyDevices = $devicesOutput | Where-Object { $_ -match "\sdevice$" }
        if ($readyDevices) {
            $devicesOutput | Out-Host
            return
        }

        if ($i -eq 0) { $devicesOutput | Out-Host }
        Start-Sleep -Seconds 2
    }

    throw "No authorized Android device found. Keep the phone unlocked and confirm 'adb devices' shows 'device'."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
if (-not $ApkPath) { $ApkPath = Join-Path $repoRoot "app/build/outputs/apk/debug/app-debug.apk" }
if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }

$adb = Resolve-Adb -AdbPath $AdbPath -SdkDir $SdkDir
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$hash = Get-FileHash -LiteralPath $apk -Algorithm SHA256

# 尝试通过 aapt / aapt2 / apkanalyzer 验证 APK 中的真实 applicationId
$detectedPackage = $null
$tools = @("aapt.exe", "aapt2.exe", "apkanalyzer.bat", "apkanalyzer.exe")
foreach ($tool in $tools) {
    $cmd = Get-Command $tool -ErrorAction SilentlyContinue
    if ($cmd) {
        if ($tool -match "aapt") {
            $dump = & $cmd.Source dump badging $apk 2>$null
            if ($dump -match "package:\s*name='([^']+)'") {
                $detectedPackage = $Matches[1]
                break
            }
        } elseif ($tool -match "apkanalyzer") {
            $dump = & $cmd.Source manifest application-id $apk 2>$null
            if ($dump -and $dump.Trim()) {
                $detectedPackage = $dump.Trim()
                break
            }
        }
    }
}

if ($detectedPackage) {
    if ($detectedPackage -ne $PackageName) {
        throw "APK package name '$detectedPackage' does not match expected '$PackageName'. Please check if you selected the right APK."
    } else {
        Write-Host "Verified APK package name: $PackageName"
    }
} else {
    Write-Host "Target PackageName: $PackageName"
}

Write-Host "ADB: $adb"
Write-Host "APK: $apk"
Write-Host "SHA256: $($hash.Hash)"
Write-Host ""

Wait-AuthorizedDevice -Adb $adb

Write-Host "Installing HuaweiPods debug APK..."
& $adb install -r -d $apk | Out-Host

Write-Host "Stopping HuaweiPods app process..."
& $adb shell am force-stop $PackageName | Out-Null

if ($RestartBluetoothWithRoot) {
    Write-Host "Trying to force-stop com.android.bluetooth through su..."
    & $adb shell su -c "am force-stop com.android.bluetooth" | Out-Host
    Start-Sleep -Seconds 2
}

Write-Host "Installed package info:"
& $adb shell dumpsys package $PackageName |
    Select-String -Pattern "versionCode|versionName|firstInstallTime|lastUpdateTime|codePath" |
    ForEach-Object { $_.Line }

Write-Host ""
Write-Host "Next steps:"
Write-Host "1. In LSPosed, make sure HuaweiPods is enabled and scoped to com.android.bluetooth, com.android.settings, com.milink.service, com.xiaomi.bluetooth, com.heytap.accessory, and com.heytap.mydevices."
Write-Host "2. Reboot the phone, or disable/enable the module and restart the scoped apps. Reboot is the most reliable."
Write-Host "3. Test battery display, the notification ANC action, and gesture settings."
