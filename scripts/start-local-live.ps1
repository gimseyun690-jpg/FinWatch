[CmdletBinding()]
param(
    [int]$Port = 8080,
    [switch]$Build,
    [string]$WorkRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$jarPath = Join-Path $backendRoot 'build\libs\finwatch-api.jar'

function Import-EnvironmentVariable([string]$Name, [bool]$Required) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'Machine')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        if ($Required) {
            throw "Required environment variable $Name is missing."
        }
        return
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
$required | ForEach-Object { Import-EnvironmentVariable $_ $true }

$optional = @(
    'ARTICLE_USER_AGENT',
    'SEC_EDGAR_USER_AGENT',
    'KAKAO_REST_API_KEY',
    'KAKAO_CLIENT_SECRET',
    'KAKAO_REDIRECT_URI',
    'KAKAO_LOGIN_ENABLED'
)
$optional | ForEach-Object { Import-EnvironmentVariable $_ $false }

if ([string]::IsNullOrWhiteSpace($WorkRoot)) {
    if (-not [string]::IsNullOrWhiteSpace($env:FINWATCH_TEST_WORK_ROOT)) {
        $WorkRoot = $env:FINWATCH_TEST_WORK_ROOT
    }
    elseif (Test-Path -LiteralPath 'D:\') {
        $WorkRoot = 'D:\FinWatchTest'
    }
    else {
        $WorkRoot = Join-Path $env:LOCALAPPDATA 'FinWatchTest'
    }
}
$workRootPath = [System.IO.Path]::GetFullPath($WorkRoot)
$gradleHome = Join-Path $workRootPath 'gradle'
$asciiTemp = Join-Path $workRootPath 'temp'
New-Item -ItemType Directory -Force -Path $gradleHome, $asciiTemp | Out-Null
$env:GRADLE_USER_HOME = $gradleHome
$env:TEMP = $asciiTemp
$env:TMP = $asciiTemp

$portableJdk = Get-ChildItem (Join-Path $workRootPath 'tools\temurin21') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -First 1
if ($portableJdk) {
    $env:JAVA_HOME = $portableJdk.FullName
    $env:PATH = "$($portableJdk.FullName)\bin;$env:PATH"
}

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
