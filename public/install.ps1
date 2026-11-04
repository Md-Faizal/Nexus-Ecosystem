# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Windows)
$ErrorActionPreference = "Stop"

$HubUrl = if ($env:NEXUS_HUB) { $env:NEXUS_HUB } else { "https://faizal.uk" }
$TargetDir = "$env:ProgramFiles\NexusAgent"

Write-Host "=== Installing Nexus Fleet Agent for Windows ===" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$BinUrl = "$HubUrl/releases/nexus-agent-windows-amd64.exe"
$DestExe = "$TargetDir\nexus-agent.exe"

Write-Host "Downloading Nexus Agent from $BinUrl..." -ForegroundColor Gray
Invoke-WebRequest -Uri $BinUrl -OutFile $DestExe

Write-Host "Registering Nexus Agent as automatic background service..." -ForegroundColor Gray
& $DestExe service install

Write-Host "Nexus Agent successfully installed and running!" -ForegroundColor Green
Write-Host "Your PC will automatically appear on your Personal Site Admin Panel within 5 seconds." -ForegroundColor Cyan

