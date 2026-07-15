[CmdletBinding()]
param(
    [int]$Port = 8080,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$jarPath = Join-Path $backendRoot 'build\libs\finwatch-api.jar'

function Import-RequiredEnvironmentVariable([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'Machine')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable $Name is missing."
    }
    [Environment]::SetEnvironmentVariable($Name, $value, 'Process')
}

$required = @(
    'GEMINI_API_KEY',
    'KIS_APP_KEY',
    'KIS_APP_SECRET',
    'KIS_HTS_ID',
    'NAVER_API_HUB_CLIENT_ID',
    'NAVER_API_HUB_CLIENT_SECRET',
    'OPENDART_API_KEY',
    'FINNHUB_API_KEY'
)
$required | ForEach-Object { Import-RequiredEnvironmentVariable $_ }

$env:SPRING_PROFILES_ACTIVE = 'live'
$env:DATA_MODE = 'LIVE'
$env:AI_PROVIDER = 'gemini'
$env:SERVER_PORT = $Port.ToString()

if ($Build -or -not (Test-Path -LiteralPath $jarPath)) {
    & (Join-Path $backendRoot 'gradlew.bat') clean bootJar
    if ($LASTEXITCODE -ne 0) {
        throw 'Backend build failed.'
    }
}

Write-Host "Starting FinWatch LIVE on port $Port. Secret values will not be printed."
$javaCommand = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $javaFromHome) {
        $javaCommand = $javaFromHome
    }
}
if ($null -eq $javaCommand) {
    $javaCommand = (Get-Command java.exe -ErrorAction Stop).Source
}
& $javaCommand -jar $jarPath
