param(
    [int]$Port = 8080,
    [string]$Profile = "",
    [string]$EnvFile = "",
    [string]$JavaHome = "D:\JDK21",
    [switch]$UseLocalInfra,
    [switch]$DisableOss,
    [switch]$ForceUpdate,
    [switch]$Clean,
    [switch]$DebugSpring,
    [string]$JvmArgs = "",
    [string[]]$MavenArgs = @(),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

function Set-EnvVar {
    param(
        [string]$Name,
        [string]$Value
    )

    Set-Item -Path "Env:$Name" -Value $Value
}

function Import-EnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Env file '$Path' was not found."
    }

    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $name = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-EnvVar -Name $name -Value $value
    }
}

function Resolve-MavenCommand {
    $mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mavenCommand) {
        return $mavenCommand.Source
    }

    if ($env:MAVEN_HOME) {
        $mavenHomeCommand = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
        if (Test-Path $mavenHomeCommand) {
            return $mavenHomeCommand
        }
    }

    throw "Maven command not found. Configure Maven in PATH or set MAVEN_HOME correctly."
}

if (-not (Test-Path $JavaHome)) {
    throw "Configured JavaHome '$JavaHome' was not found."
}

$javaBinDir = Join-Path $JavaHome "bin"
$javaCommand = Join-Path $javaBinDir "java.exe"

if (-not (Test-Path $javaCommand)) {
    throw "Java executable was not found under '$javaBinDir'."
}

$env:JAVA_HOME = $JavaHome
$pathEntries = @($javaBinDir)
if ($env:PATH) {
    $pathEntries += $env:PATH
}
$env:PATH = ($pathEntries -join ";")

$javaVersionOutput = & $javaCommand --version 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to execute '$javaCommand --version'."
}

$javaMajorVersion = $null
if ($javaVersionOutput -and $javaVersionOutput[0] -match 'version "?(?<version>\d+)') {
    $javaMajorVersion = [int]$Matches.version
} elseif ($javaVersionOutput -and $javaVersionOutput[0] -match '^(?:openjdk|java)\s+(?<version>\d+)\.') {
    $javaMajorVersion = [int]$Matches.version
} elseif ($javaVersionOutput -and $javaVersionOutput[0] -match '^(?<version>\d+)\.') {
    $javaMajorVersion = [int]$Matches.version
}

if ($null -eq $javaMajorVersion) {
    throw "Unable to determine Java major version from '$($javaVersionOutput[0])'."
}

if ($javaMajorVersion -lt 21) {
    throw "Java 21 is required, but '$javaCommand' resolved to version '$($javaVersionOutput[0])'."
}

if ($EnvFile) {
    Import-EnvFile -Path $EnvFile
} else {
    $defaultEnvFile = Join-Path $scriptDir ".env"
    if (Test-Path $defaultEnvFile) {
        Import-EnvFile -Path $defaultEnvFile
    }
}

if ($UseLocalInfra) {
    # Match the repository docker-compose defaults for local development.
    Set-EnvVar -Name "DB_URL" -Value "jdbc:postgresql://localhost:5432/medical_agent"
    Set-EnvVar -Name "DB_USER" -Value "medical"
    Set-EnvVar -Name "DB_PASSWORD" -Value "medical"
    Set-EnvVar -Name "RABBITMQ_ADDRESSES" -Value "localhost:5672"
    Set-EnvVar -Name "RABBITMQ_USERNAME" -Value "guest"
    Set-EnvVar -Name "RABBITMQ_PASSWORD" -Value "guest"
    Set-EnvVar -Name "RABBITMQ_VHOST" -Value "/"
    Set-EnvVar -Name "APP_AGENT_BASE_URL" -Value "http://localhost:8090"
    Set-EnvVar -Name "APP_SECURITY_ENABLED" -Value "false"
    Set-EnvVar -Name "APP_OSS_ENABLED" -Value "false"
}

if ($DisableOss) {
    Set-EnvVar -Name "APP_OSS_ENABLED" -Value "false"
}

$mavenCommand = Resolve-MavenCommand

$effectiveJvmArgs = @()
if ($JvmArgs) {
    $effectiveJvmArgs += $JvmArgs
}
if ($DebugSpring) {
    $effectiveJvmArgs += "-Dlogging.level.org.springframework=DEBUG"
}

$commandArgs = @()
if ($Clean) {
    $commandArgs += "clean"
}
if ($ForceUpdate) {
    $commandArgs += "-U"
}

if ($Profile) {
    $commandArgs += "-Dspring-boot.run.profiles=$Profile"
}

$commandArgs += "-Dspring-boot.run.arguments=--server.port=$Port"

if ($effectiveJvmArgs.Count -gt 0) {
    $commandArgs += "-Dspring-boot.run.jvmArguments=$($effectiveJvmArgs -join ' ')"
}

if ($MavenArgs.Count -gt 0) {
    $commandArgs += $MavenArgs
}

$commandArgs += "spring-boot:run"

Write-Host "Starting backend-java from $scriptDir"
Write-Host "Port: $Port"
if ($Profile) {
    Write-Host "Profile: $Profile"
}
Write-Host "JAVA_HOME: $env:JAVA_HOME"
if ($UseLocalInfra) {
    Write-Host "Using local docker-compose infrastructure defaults."
}
if ((Get-Item "Env:APP_OSS_ENABLED" -ErrorAction SilentlyContinue)?.Value -eq "false") {
    Write-Host "OSS integration disabled for this session."
}
Write-Host "Maven command: $mavenCommand"
Write-Host "Command: $mavenCommand $($commandArgs -join ' ')"

if ($DryRun) {
    exit 0
}

& $mavenCommand @commandArgs
exit $LASTEXITCODE
