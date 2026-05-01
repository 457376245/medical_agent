# Start backend-agent (FastAPI on port 8090)
Set-Location "$PSScriptRoot/backend-agent"
$env:UV_CACHE_DIR = ".uv-cache"
uv run python -m app.main
