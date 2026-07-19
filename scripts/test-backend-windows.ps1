[CmdletBinding()]
param(
    [switch]$IncludeDocker,
    [string]$WorkRoot
)

$ErrorActionPreference = 'Stop'

function Get-FreeDriveLetter([string[]]$Candidates) {
    foreach ($candidate in $Candidates) {
        if (-not (Get-PSDrive -Name $candidate -ErrorAction SilentlyContinue)) {
            return "${candidate}:"
        }
    }
    throw 'Gradle 테스트에 사용할 빈 드라이브 문자를 찾지 못했습니다.'
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryDrive = Get-FreeDriveLetter @('W', 'V', 'U', 'T')
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
$previous = @{
    JAVA_HOME = $env:JAVA_HOME
    GRADLE_USER_HOME = $env:GRADLE_USER_HOME
    TEMP = $env:TEMP
    TMP = $env:TMP
    PATH = $env:PATH
}

try {
    New-Item -ItemType Directory -Force -Path $gradleHome, $asciiTemp | Out-Null
    & subst.exe $repositoryDrive $repository

    $env:GRADLE_USER_HOME = $gradleHome
    $env:TEMP = $asciiTemp
    $env:TMP = $asciiTemp

    $jdk21 = Get-ChildItem (Join-Path $workRootPath 'tools\temurin21') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $jdk21) {
        $jdk21 = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1
    }
    if ($jdk21) {
        $env:JAVA_HOME = $jdk21.FullName
        $env:PATH = "$($jdk21.FullName)\bin;$($previous.PATH)"
    }
    elseif (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java 21을 찾지 못했습니다. Temurin 21을 설치하거나 '$workRootPath\tools\temurin21\jdk-21*'에 압축 해제하세요."
    }

    $arguments = @('test', '--no-daemon')
    if (-not $IncludeDocker) {
        $arguments += '-PskipDockerTests=true'
    }

    Push-Location "${repositoryDrive}\backend"
    try {
        & .\gradlew.bat @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle 테스트가 종료 코드 $LASTEXITCODE 로 실패했습니다."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    & subst.exe $repositoryDrive /D 2>$null
    foreach ($name in $previous.Keys) {
        if ($null -eq $previous[$name]) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$name" $previous[$name]
        }
    }
}
