# Start frontend (Next.js dev server on port 3000)
$port = 3000
$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1

if ($listener) {
    $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    $processName = if ($process) { $process.ProcessName } else { "unknown" }

    Write-Host "Port $port is already in use by PID $($listener.OwningProcess) ($processName)." -ForegroundColor Yellow
    Write-Host "Stop that process first, then run this script again." -ForegroundColor Yellow
    Write-Host "Example: Stop-Process -Id $($listener.OwningProcess)" -ForegroundColor Yellow
    exit 1
}

Set-Location "$PSScriptRoot/frontend"
$env:NEXT_TELEMETRY_DISABLED = "1"
npm run dev
