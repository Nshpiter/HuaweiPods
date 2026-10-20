param(
    [string]$AdbPath = "",
    [string]$SdkDir = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

function Resolve-Adb {
    param([string]$ExplicitAdbPath, [string]$ExplicitSdkDir)

    $candidates = @()
    if ($ExplicitAdbPath) { $candidates += $ExplicitAdbPath }
    if ($ExplicitSdkDir) { $candidates += (Join-Path $ExplicitSdkDir "platform-tools/adb.exe") }
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools/adb.exe") }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb.exe") }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe") }
    $candidates += "adb.exe"

    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $resolved = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($resolved) { return $resolved.Source }
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    throw "adb.exe not found. Pass -AdbPath or -SdkDir."
}

function Invoke-AdbText {
    param([string]$Adb, [string[]]$Arguments)

    $result = & $Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Arguments -join ' ')`n$($result -join [Environment]::NewLine)"
    }
    return @($result)
}

$adb = Resolve-Adb -ExplicitAdbPath $AdbPath -ExplicitSdkDir $SdkDir
$devices = Invoke-AdbText -Adb $adb -Arguments @("devices")
$authorized = @($devices | Where-Object { $_ -match "\sdevice$" })
if ($authorized.Count -ne 1) {
    throw "Connect exactly one authorized phone. 'adb devices' must show one device."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
if (-not $OutputDir) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "captures/coloros-$stamp"
}
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

$propertyNames = @(
    "ro.product.manufacturer",
    "ro.product.brand",
    "ro.product.model",
    "ro.build.display.id",
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.build.version.oplusrom",
    "ro.build.version.opporom"
)
$propertyLines = foreach ($propertyName in $propertyNames) {
    $value = (Invoke-AdbText -Adb $adb -Arguments @("shell", "getprop", $propertyName)) -join ""
    "$propertyName=$($value.Trim())"
}
Set-Content -LiteralPath (Join-Path $resolvedOutput "device.properties") -Value $propertyLines -Encoding utf8

$packages = @(
    "com.android.bluetooth",
    "com.android.settings",
    "com.heytap.accessory",
    "com.heytap.mydevices",
    "com.oplus.melody",
    "com.oplus.wirelesssettings"
)

$packageSummary = [System.Collections.Generic.List[string]]::new()
foreach ($packageName in $packages) {
    $paths = @(
        @(Invoke-AdbText -Adb $adb -Arguments @("shell", "pm", "path", $packageName)) |
            Where-Object { $_ -like "package:*" } |
            ForEach-Object { $_.Substring("package:".Length).Trim() }
    )
    if ($paths.Count -eq 0) {
        $packageSummary.Add("[$packageName] not installed")
        continue
    }

    $packageSummary.Add("[$packageName]")
    $packageInfo = Invoke-AdbText -Adb $adb -Arguments @("shell", "dumpsys", "package", $packageName)
    $packageInfo |
        Select-String -Pattern "versionCode=|versionName=|codePath=|primaryCpuAbi=" |
        ForEach-Object { $packageSummary.Add($_.Line.Trim()) }

    $packageDir = Join-Path $resolvedOutput $packageName
    New-Item -ItemType Directory -Path $packageDir -Force | Out-Null
    for ($index = 0; $index -lt $paths.Count; $index++) {
        $remotePath = $paths[$index]
        if ($remotePath -notmatch '^/(system|system_ext|product|vendor|data/app)/.+\.apk$') {
            throw "Refusing unexpected package path for $packageName`: $remotePath"
        }
        $fileName = Split-Path -Leaf $remotePath
        if ($paths.Count -gt 1) { $fileName = "$index-$fileName" }
        $localPath = Join-Path $packageDir $fileName
        Write-Host "Pulling $packageName`: $remotePath"
        & $adb pull $remotePath $localPath | Out-Host
        if ($LASTEXITCODE -ne 0) {
            $packageSummary.Add("pull failed: $remotePath")
        }
    }
}

Set-Content -LiteralPath (Join-Path $resolvedOutput "packages.txt") -Value $packageSummary -Encoding utf8
Get-ChildItem -LiteralPath $resolvedOutput -Recurse -File |
    Where-Object { $_.Extension -eq ".apk" } |
    ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "$($hash.Hash)  $($_.FullName.Substring($resolvedOutput.Length + 1))"
    } |
    Set-Content -LiteralPath (Join-Path $resolvedOutput "SHA256SUMS.txt") -Encoding ascii

Write-Host "ColorOS environment capture saved to: $resolvedOutput"
Write-Host "This capture contains build properties, package versions, and system host APKs only."
Write-Host "It intentionally does not collect logcat, bugreport, accounts, or Bluetooth addresses."
