$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Get-RequiredEnv {
    param([string]$Name)

    $Value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "Missing required environment variable: $Name"
    }

    return $Value
}

function Get-OptionalEnv {
    param(
        [string]$Name,
        [string]$DefaultValue
    )

    $Value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    return $Value
}

$ArtifactDownloadUrl = Get-RequiredEnv 'KK_DEPLOY_ARTIFACT_URL'
$DeployRoot = Get-OptionalEnv 'KK_DEPLOY_ROOT' 'C:\kkFileView-5.0'
$HealthUrl = Get-OptionalEnv 'KK_DEPLOY_HEALTH_URL' 'http://127.0.0.1:8012/'
$DryRun = Get-OptionalEnv 'KK_DEPLOY_DRY_RUN' 'false'

$BinDir = Join-Path $DeployRoot 'bin'
$StartupScript = Join-Path $BinDir 'startup.bat'
$ReleaseDir = Join-Path $DeployRoot 'releases'
$DeployTmp = Join-Path $DeployRoot 'deploy-tmp'
$ArtifactZip = Join-Path $DeployTmp 'artifact.zip'
$ExtractDir = Join-Path $DeployTmp 'artifact'

if (-not (Test-Path $DeployRoot)) {
    throw "Deploy root not found: $DeployRoot"
}

if (-not (Test-Path $BinDir)) {
    throw "Bin directory not found: $BinDir"
}

if (-not (Test-Path $StartupScript)) {
    throw "Startup script not found: $StartupScript"
}

$CurrentJar = Get-ChildItem $BinDir -Filter 'kkFileView-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $CurrentJar) {
    throw "No kkFileView jar found in $BinDir"
}

$JarName = $CurrentJar.Name
$JarPath = $CurrentJar.FullName

Write-Step "Deploy root: $DeployRoot"
Write-Step "Current jar: $JarPath"
Write-Step "Startup script: $StartupScript"
Write-Step "Health url: $HealthUrl"

if ($DryRun -eq 'true') {
    Write-Step "Dry run enabled, remote validation finished"
    return
}

New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
New-Item -ItemType Directory -Force -Path $DeployTmp | Out-Null

if (Test-Path $ArtifactZip) {
    Remove-Item $ArtifactZip -Force
}

if (Test-Path $ExtractDir) {
    Remove-Item $ExtractDir -Recurse -Force
}

Write-Step 'Downloading workflow artifact via signed URL'
$PreviousProgressPreference = $ProgressPreference
$ProgressPreference = 'SilentlyContinue'
try {
    Invoke-WebRequest -Uri $ArtifactDownloadUrl -OutFile $ArtifactZip -UseBasicParsing -TimeoutSec 120
} finally {
    $ProgressPreference = $PreviousProgressPreference
}

if (-not (Test-Path $ArtifactZip)) {
    throw "Artifact zip was not created: $ArtifactZip"
}

$ArtifactZipInfo = Get-Item $ArtifactZip
if ($ArtifactZipInfo.Length -le 0) {
    throw "Downloaded artifact zip is empty: $ArtifactZip"
}

Expand-Archive -LiteralPath $ArtifactZip -DestinationPath $ExtractDir -Force

$DownloadedJars = Get-ChildItem $ExtractDir -Filter 'kkFileView-*.jar' -Recurse
if (-not $DownloadedJars) {
    throw 'No kkFileView jar found inside downloaded workflow artifact'
}

if ($DownloadedJars.Count -ne 1) {
    throw "Expected exactly one kkFileView jar inside downloaded workflow artifact, found $($DownloadedJars.Count)"
}

$DownloadedJar = $DownloadedJars[0]

$Timestamp = Get-Date -Format 'yyyyMMddHHmmss'
$BackupJar = Join-Path $ReleaseDir ("{0}.{1}.bak" -f $JarName, $Timestamp)

function Stop-KkFileView {
    $JarPattern = [regex]::Escape($JarName)
    $Processes = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -and $_.CommandLine -match $JarPattern
    }

    foreach ($Process in $Processes) {
        Write-Step "Stopping java process $($Process.ProcessId)"
        Stop-Process -Id $Process.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Wait-KkFileViewStopped {
    param([int]$TimeoutSeconds = 30)

    $JarPattern = [regex]::Escape($JarName)
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        $Processes = Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^java(\.exe)?$' -and $_.CommandLine -and $_.CommandLine -match $JarPattern
        }

        if (-not $Processes) {
            return $true
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Start-KkFileView {
    Write-Step "Starting kkFileView"
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$StartupScript`"" -WorkingDirectory $BinDir -WindowStyle Hidden
}

function Wait-Health {
    param([string]$Url)

    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        try {
            $Response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($Response.StatusCode -eq 200) {
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }

    return $false
}

Write-Step "Backing up current jar to $BackupJar"
Copy-Item $JarPath $BackupJar -Force

Stop-KkFileView
if (-not (Wait-KkFileViewStopped)) {
    throw "Timed out waiting for the previous kkFileView process to exit"
}

Write-Step "Replacing jar with artifact output"
Copy-Item $DownloadedJar.FullName $JarPath -Force

Start-KkFileView

if (-not (Wait-Health -Url $HealthUrl)) {
    Write-Step "Health check failed, rolling back"
    Stop-KkFileView
    if (-not (Wait-KkFileViewStopped)) {
        throw "Timed out waiting for the failed kkFileView process to exit during rollback"
    }
    Copy-Item $BackupJar $JarPath -Force
    Start-KkFileView

    if (-not (Wait-Health -Url $HealthUrl)) {
        throw "Deployment failed and rollback health check also failed"
    }

    throw "Deployment failed, rollback completed successfully"
}

Write-Step "Deployment completed successfully"
