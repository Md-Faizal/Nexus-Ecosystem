# Nexus Fleet Agent — Universal Zero-Token 1-Command Installer (Windows)
$ErrorActionPreference = "Stop"

$HubUrl = if ($env:NEXUS_HUB) { $env:NEXUS_HUB } else { "https://faizal.uk" }
$IsAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $TargetDir) {
    if ($IsAdmin) {
        if (Test-Path "${env:ProgramFiles(x86)}\NexusAgent") {
            $TargetDir = "${env:ProgramFiles(x86)}\NexusAgent"
        } else {
            $TargetDir = "$env:ProgramFiles\NexusAgent"
        }
        Write-Host "=== Installing Nexus Fleet Agent (System Administrator Mode) ===" -ForegroundColor Cyan
    } else {
        $TargetDir = "$env:LOCALAPPDATA\Programs\NexusAgent"
        Write-Host "=== Installing Nexus Fleet Agent (User Mode) ===" -ForegroundColor Yellow
        Write-Host "Note: Installing without admin privileges. Run 'nexus elevate' anytime to upgrade." -ForegroundColor Gray
    }
} else {
    Write-Host "=== Installing Nexus Fleet Agent into $TargetDir ===" -ForegroundColor Cyan
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$CacheBuster = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$BinUrl = "$HubUrl/releases/nexus-agent-windows-amd64.exe?v=$CacheBuster"
$DestExe = "$TargetDir\nexus-agent.exe"
$NexusAlias = "$TargetDir\nexus.exe"
$NxAlias = "$TargetDir\nx.exe"
$TempExe = "$TargetDir\nexus-agent.tmp.$CacheBuster.exe"

Write-Host "Downloading Nexus Agent from $BinUrl..." -ForegroundColor Gray
if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
    curl.exe -fsSL "$BinUrl" -o "$TempExe"
} else {
    Invoke-WebRequest -Uri $BinUrl -OutFile $TempExe -UseBasicParsing
}

# Unblock downloaded binary from Windows Mark-of-the-Web / Application Control
Unblock-File -Path $TempExe -ErrorAction SilentlyContinue
if (Test-Path "$TempExe:Zone.Identifier") {
    Remove-Item "$TempExe:Zone.Identifier" -ErrorAction SilentlyContinue
}

if (-not (Test-Path $TempExe) -or (Get-Item $TempExe).Length -lt 100000) {
    Write-Error "Downloaded binary appears invalid or incomplete."
    exit 1
}

# Terminate running instances before replacing files
Stop-Process -Name "nexus-agent" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "nexus" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "nx" -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 600

Move-Item -Path $TempExe -Destination $DestExe -Force
Copy-Item -Path $DestExe -Destination $NexusAlias -Force -ErrorAction SilentlyContinue
Copy-Item -Path $DestExe -Destination $NxAlias -Force -ErrorAction SilentlyContinue
Unblock-File -Path $DestExe -ErrorAction SilentlyContinue
Unblock-File -Path $NexusAlias -ErrorAction SilentlyContinue
Unblock-File -Path $NxAlias -ErrorAction SilentlyContinue
foreach ($f in @($DestExe, $NexusAlias, $NxAlias)) {
    if (Test-Path "$f:Zone.Identifier") {
        Remove-Item "$f:Zone.Identifier" -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Configuring system PATH environment variable..." -ForegroundColor Gray
if ($IsAdmin) {
    try {
        $MachinePath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
        if ($MachinePath -notlike "*$TargetDir*") {
            [Environment]::SetEnvironmentVariable("Path", "$MachinePath;$TargetDir", [System.EnvironmentVariableTarget]::Machine)
        }
    } catch {}
}

try {
    $UserPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
    if ($UserPath -notlike "*$TargetDir*") {
        [Environment]::SetEnvironmentVariable("Path", "$UserPath;$TargetDir", [System.EnvironmentVariableTarget]::User)
    }
} catch {}

if ($env:Path -notlike "*$TargetDir*") {
    $env:Path = "$env:Path;$TargetDir"
}

Write-Host "Registering Nexus background service and startup daemon..." -ForegroundColor Gray
if ($IsAdmin) {
    & $DestExe service install
} else {
    # Non-admin startup registry registration
    try {
        Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "NexusAgent" -Value "`"$NexusAlias`" run --foreground" -Force
    } catch {}
    # Launch detached background worker immediately so telemetry is active
    & "$NexusAlias" run
}

Write-Host "Nexus Agent successfully installed and running!" -ForegroundColor Green
Write-Host "Telemetry is streaming to $HubUrl." -ForegroundColor Green
Write-Host "You can now run 'nexus' or 'nx' directly in any terminal (CMD / PowerShell):" -ForegroundColor Cyan
Write-Host "  nexus run       - Start background telemetry daemon (if stopped)" -ForegroundColor White
Write-Host "  nexus status    - View live system health and telemetry" -ForegroundColor White
Write-Host "  nexus cluster   - Inspect the 3-VM cluster (my-mac, cos, pro)" -ForegroundColor White
Write-Host "  nexus services  - View running containers, web engines, and games" -ForegroundColor White
Write-Host "  nexus chat      - Interactive local AI session" -ForegroundColor White
if (-not $IsAdmin) {
    Write-Host "  nexus elevate   - Upgrade to Administrator System Service at any time" -ForegroundColor Yellow
}
Write-Host "Your PC will appear on your Personal Site Admin Panel (https://faizal.uk/admin.html) immediately." -ForegroundColor Cyan
