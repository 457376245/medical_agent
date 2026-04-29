# Start backend-agent (FastAPI on port 8090)
Set-Location "$PSScriptRoot/backend-agent"
uv run python -m app.main
