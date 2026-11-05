# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Windows)
$ErrorActionPreference = "Stop"

$HubUrl = if ($env:NEXUS_HUB) { $env:NEXUS_HUB } else { "https://faizal.uk" }
$TargetDir = "$env:ProgramFiles\NexusAgent"

Write-Host "=== Installing Nexus Fleet Agent for Windows ===" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$CacheBuster = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$BinUrl = "$HubUrl/releases/nexus-agent-windows-amd64.exe?v=$CacheBuster"
$DestExe = "$TargetDir\nexus-agent.exe"

Write-Host "Downloading Nexus Agent from $BinUrl..." -ForegroundColor Gray
Invoke-WebRequest -Uri $BinUrl -OutFile $DestExe -UseBasicParsing

if (-not (Test-Path $DestExe) -or (Get-Item $DestExe).Length -lt 100000) {
    Write-Error "Downloaded binary appears invalid or incomplete."
    exit 1
}

Write-Host "Configuring system PATH environment variable..." -ForegroundColor Gray
try {
    $MachinePath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
    if ($MachinePath -notlike "*$TargetDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$MachinePath;$TargetDir", [System.EnvironmentVariableTarget]::Machine)
    }
    $UserPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
    if ($UserPath -notlike "*$TargetDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$UserPath;$TargetDir", [System.EnvironmentVariableTarget]::User)
    }
} catch {
    $UserPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
    if ($UserPath -notlike "*$TargetDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$UserPath;$TargetDir", [System.EnvironmentVariableTarget]::User)
    }
}

if ($env:Path -notlike "*$TargetDir*") {
    $env:Path = "$env:Path;$TargetDir"
}

Write-Host "Registering Nexus Agent as automatic background service..." -ForegroundColor Gray
& $DestExe service install

Write-Host "Nexus Agent successfully installed and running!" -ForegroundColor Green
Write-Host "You can now run 'nexus-agent status' or 'nexus-agent chat' directly in any terminal (CMD / PowerShell)." -ForegroundColor Cyan
Write-Host "Your PC will automatically appear on your Personal Site Admin Panel within 5 seconds." -ForegroundColor Cyan

