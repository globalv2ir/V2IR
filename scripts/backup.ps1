<#
.SYNOPSIS
    V2IR Project Backup Script

.PARAMETER Label
    Short description for this backup (e.g. "before-scanner-refactor", "build-success")

.EXAMPLE
    .\scripts\backup.ps1 -Label "before-parallel-scanner"
    .\scripts\backup.ps1 -Label "build-success"
#>

param(
    [Parameter(Mandatory=$false)]
    [string]$Label = "manual"
)

$ProjectRoot = "e:\Desktop\SmartXrayClient"
$BackupBase  = "E:\V2IR_Backups"
$Timestamp   = Get-Date -Format "yyyyMMdd-HHmm"
$Dest        = "$BackupBase\$Timestamp-$Label"

Write-Host ""
Write-Host "[V2IR Backup] Starting backup..." -ForegroundColor Cyan
Write-Host "  Destination: $Dest" -ForegroundColor Gray

# Create destination folder
New-Item -ItemType Directory -Path $Dest -Force | Out-Null

# Folders to exclude
$ExcludeDirs = @(
    '.gradle',
    'build',
    '.cxx',
    '.kotlin',
    '.idea',
    '.artifacts',
    'jniLibs',
    '.git'
)

# Files to exclude
$ExcludeFiles = @(
    'local.properties',
    'geoip.dat',
    'geosite.dat',
    '*.so',
    '*.zip',
    '*.apk',
    '*.aab'
)

# Build robocopy arguments
$RobocopyArgs = @($ProjectRoot, $Dest, '/E', '/XD') + $ExcludeDirs + @('/XF') + $ExcludeFiles + @('/NFL', '/NDL', '/NJH', '/NJS', '/NC', '/NS')

& robocopy @RobocopyArgs | Out-Null

# robocopy exit codes 0-7 = success
if ($LASTEXITCODE -le 7) {
    $Info = @"
========================================
  V2IR SmartXrayClient - Backup Info
========================================
Timestamp   : $Timestamp
Label       : $Label
Date        : $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Source      : $ProjectRoot
Destination : $Dest
----------------------------------------
Excluded (rebuild with gradle tasks):
  - jniLibs/     -> gradlew downloadBinaries
  - assets/xray/ -> gradlew downloadBinaries
  - geoip.dat    -> gradlew downloadBinaries
  - geosite.dat  -> gradlew downloadBinaries
  - hev tunnel   -> gradlew buildHevTunnel
========================================
"@
    $Info | Out-File "$Dest\BACKUP_INFO.txt" -Encoding UTF8

    $Size = (Get-ChildItem $Dest -Recurse | Measure-Object -Property Length -Sum).Sum
    $SizeMB = [math]::Round($Size / 1MB, 2)

    Write-Host ""
    Write-Host "[OK] Backup saved successfully!" -ForegroundColor Green
    Write-Host "  Path : $Dest" -ForegroundColor White
    Write-Host "  Size : $SizeMB MB" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "[ERROR] Backup failed (robocopy exit code: $LASTEXITCODE)" -ForegroundColor Red
    Write-Host "  Check that E:\V2IR_Backups is accessible." -ForegroundColor Yellow
    exit 1
}

# List existing backups
Write-Host "[Backups] Available backups in E:\V2IR_Backups:" -ForegroundColor Cyan
Get-ChildItem $BackupBase -Directory | Sort-Object Name | ForEach-Object {
    $bSize = (Get-ChildItem $_.FullName -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    $bSizeMB = [math]::Round($bSize / 1MB, 2)
    Write-Host "  $($_.Name)  ($bSizeMB MB)" -ForegroundColor Gray
}
Write-Host ""
