param(
    [string]$Host = "0.0.0.0",
    [int]$Port = 8090,
    [string]$EnvFile = ".env",
    [switch]$NoReload
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$venvPython = Join-Path $scriptDir ".venv\Scripts\python.exe"
$python = $venvPython

if (-not (Test-Path $python)) {
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if ($pythonCmd) {
        $python = $pythonCmd.Source
    } else {
        throw "Python interpreter not found. Create .venv first or install Python."
    }
}

if (-not (Test-Path $EnvFile)) {
    throw "Env file '$EnvFile' was not found in $scriptDir."
}

$args = @(
    "-m",
    "uvicorn",
    "app.main:app",
    "--host",
    $Host,
    "--port",
    $Port,
    "--env-file",
    $EnvFile
)

if (-not $NoReload) {
    $args += "--reload"
}

Write-Host "Starting backend-agent with $python"
& $python @args
exit $LASTEXITCODE
