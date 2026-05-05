# Start backend-java (Spring Boot on port 8080)
Set-Location "$PSScriptRoot/backend-java"

# Load .env file if present
$envFile = Join-Path $PSScriptRoot "backend-java/.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $key, $value = $line -split '=', 2
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
        }
    }
}

$env:Path = (($env:Path -split ';') |
    Where-Object { $_ -and $_ -ne 'D:\apache-tomcat-8.5.50\bin' }) -join ';'

mvn spring-boot:run
