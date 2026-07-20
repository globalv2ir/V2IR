# Builds libhev-socks5-tunnel.so into app/src/main/jniLibs/
# Requires Android NDK. Uses git if available, otherwise downloads GitHub zip archives.

param(
    [string]$NdkHome = $env:ANDROID_NDK_HOME
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$HevDir = Join-Path $Root "third_party\hev-socks5-tunnel"
$JniLibs = Join-Path $Root "app\src\main\jniLibs"

function Find-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "${env:ProgramFiles}\Git\bin\git.exe",
        "${env:ProgramFiles(x86)}\Git\bin\git.exe"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Expand-GithubZip($repo, $destDir, $subPath = "") {
    $zipUrl = "https://github.com/$repo/archive/refs/heads/main.zip"
    $tmpZip = Join-Path $env:TEMP ("gh-$($repo.Replace('/','-'))-" + [guid]::NewGuid().ToString() + ".zip")
    $tmpExtract = Join-Path $env:TEMP ("gh-extract-" + [guid]::NewGuid().ToString())
    Write-Host "Downloading $repo..."
    Invoke-WebRequest -Uri $zipUrl -OutFile $tmpZip -UseBasicParsing
    Expand-Archive -Path $tmpZip -DestinationPath $tmpExtract -Force
    $inner = Get-ChildItem $tmpExtract | Select-Object -First 1
    $target = if ($subPath) { Join-Path $destDir $subPath } else { $destDir }
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Move-Item $inner.FullName $target
    Remove-Item $tmpZip -Force
    Remove-Item $tmpExtract -Recurse -Force -ErrorAction SilentlyContinue
}

function Fix-Symlinks($path) {
    Get-ChildItem -Path $path -Recurse -File | Where-Object { $_.Length -lt 512 } | ForEach-Object {
        try {
            $content = (Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue).Trim()
            if ($content -match '^\.\./') {
                $target = Join-Path (Split-Path $_.FullName) $content
                if (Test-Path $target) {
                    Write-Host "Fixing symlink-file: $($_.Name) -> $content"
                    Copy-Item $target $_.FullName -Force
                }
            }
        } catch {}
    }
}

if (-not $NdkHome) {
    $SdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
    $ndkRoot = Join-Path $SdkRoot "ndk"
    if (Test-Path $ndkRoot) {
        $NdkHome = (Get-ChildItem $ndkRoot | Sort-Object Name -Descending | Select-Object -First 1).FullName
    }
}

Fix-Symlinks $HevDir

if (-not $NdkHome -or -not (Test-Path (Join-Path $NdkHome "ndk-build.cmd"))) {
    Write-Error "Android NDK not found. Install via Android Studio SDK Manager."
}

$git = Find-Git
if (-not (Test-Path $HevDir)) {
    if ($git) {
        Write-Host "Cloning hev-socks5-tunnel with git..."
        New-Item -ItemType Directory -Path (Split-Path $HevDir) -Force | Out-Null
        & $git clone --recursive --depth 1 https://github.com/heiher/hev-socks5-tunnel.git $HevDir
    } else {
        Write-Host "Git not found - downloading source archives..."
        New-Item -ItemType Directory -Path $HevDir -Force | Out-Null
        Expand-GithubZip "heiher/hev-socks5-tunnel" $HevDir
        Expand-GithubZip "heiher/hev-task-system" $HevDir "third-part\hev-task-system"
        Expand-GithubZip "heiher/yaml" $HevDir "third-part\yaml"
        Expand-GithubZip "heiher/lwip" $HevDir "third-part\lwip"
        Expand-GithubZip "heiher/hev-socks5-core" $HevDir "src\core"
    }
}

Fix-Symlinks $HevDir

$TmpDir = Join-Path $env:TEMP ("hev-build-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $TmpDir | Out-Null
$JniDir = Join-Path $TmpDir "jni"
New-Item -ItemType Directory -Path $JniDir | Out-Null
$include = 'include $(call all-subdir-makefiles)'
$include | Out-File -FilePath (Join-Path $JniDir "Android.mk") -Encoding ascii -NoNewline

$LinkTarget = Join-Path $JniDir "hev-socks5-tunnel"
cmd /c mklink /J "$LinkTarget" "$HevDir" | Out-Null

$PkgName = "com/smartxray/client/data/xray"
$ClsName = "HevTunnelBridge"

Write-Host "Building with NDK: $NdkHome"
Push-Location $TmpDir
& "$NdkHome\ndk-build.cmd" `
    NDK_PROJECT_PATH=. `
    APP_BUILD_SCRIPT=jni/Android.mk `
    "APP_ABI=armeabi-v7a arm64-v8a x86 x86_64" `
    APP_PLATFORM=android-26 `
    "NDK_LIBS_OUT=$TmpDir\libs" `
    "NDK_OUT=$TmpDir\obj" `
    "APP_CFLAGS=-O3 -DANDROID -DPKGNAME=$PkgName -DCLSNAME=$ClsName" `
    'APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu'
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Remove-Item $TmpDir -Recurse -Force
    Write-Error "ndk-build failed"
}
Pop-Location

foreach ($abi in @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
    $src = Join-Path $TmpDir "libs\$abi\libhev-socks5-tunnel.so"
    if (Test-Path $src) {
        $destDir = Join-Path $JniLibs $abi
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        Copy-Item $src (Join-Path $destDir "libhev-socks5-tunnel.so") -Force
        Write-Host "Installed $abi"
    }
}

Fix-Symlinks $HevDir

Remove-Item $TmpDir -Recurse -Force
Write-Host 'Success: libhev-socks5-tunnel.so installed to app/src/main/jniLibs/'
