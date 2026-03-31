param(
    [Alias("Host")]
    [string]$BindHost = "0.0.0.0",
    [int]$Port = 8090,
    [string]$EnvFile = ".env",
    [switch]$NoReload
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw "uv command not found. Install uv first: https://docs.astral.sh/uv/"
}

# Use a project-local uv cache to avoid machine-level cache conflicts.
$env:UV_CACHE_DIR = Join-Path $scriptDir ".uv-cache"
$env:http_proxy = "http://127.0.0.1:7897"
$env:https_proxy = "http://127.0.0.1:7897"
$env:HTTP_PROXY = "http://127.0.0.1:7897"
$env:HTTPS_PROXY = "http://127.0.0.1:7897"

if (-not (Test-Path $EnvFile)) {
    throw "Env file '$EnvFile' was not found in $scriptDir."
}

$args = @(
    "run",
    "python",
    "-m",
    "uvicorn",
    "app.main:app",
    "--host",
    $BindHost,
    "--port",
    $Port,
    "--env-file",
    $EnvFile
)

if (-not $NoReload) {
    $args += "--reload"
}

Write-Host "Starting backend-agent with uv"
& uv @args
exit $LASTEXITCODE
