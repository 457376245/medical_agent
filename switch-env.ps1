<#
.SYNOPSIS
    Switch development environment between home (192.168.31.101) and office (100.113.121.53).

.DESCRIPTION
    Copies the appropriate .env.{env} files to .env for both backend-agent and backend-java.
    After switching, restart the services to pick up the new config.

.PARAMETER Env
    Target environment: "home" or "office".

.EXAMPLE
    .\switch-env.ps1 home
    .\switch-env.ps1 office
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("home", "office")]
    [string]$Env
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$envMap = @{
    "home"   = "192.168.31.101"
    "office" = "100.113.121.53"
}

$serverIp = $envMap[$Env]

# --- backend-agent ---
$agentDir = Join-Path $rootDir "backend-agent"
$agentSource = Join-Path $agentDir ".env.$Env"
$agentTarget = Join-Path $agentDir ".env"

if (-not (Test-Path $agentSource)) {
    throw "Agent env file not found: $agentSource"
}

Copy-Item -Path $agentSource -Destination $agentTarget -Force
Write-Host "[backend-agent] .env -> .env.$Env ($serverIp)" -ForegroundColor Green

# --- backend-java ---
$javaDir = Join-Path $rootDir "backend-java"
$javaSource = Join-Path $javaDir ".env.$Env"
$javaTarget = Join-Path $javaDir ".env"

if (-not (Test-Path $javaSource)) {
    throw "Java env file not found: $javaSource"
}

Copy-Item -Path $javaSource -Destination $javaTarget -Force
Write-Host "[backend-java]  .env -> .env.$Env ($serverIp)" -ForegroundColor Green

Write-Host ""
Write-Host "Switched to '$Env' environment ($serverIp)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Start services:" -ForegroundColor Yellow
Write-Host "  backend-java:  cd backend-java && .\dev.ps1"
Write-Host "  backend-agent: cd backend-agent && uv run python -m app.main"
Write-Host "  frontend:      cd frontend && npm run dev"
