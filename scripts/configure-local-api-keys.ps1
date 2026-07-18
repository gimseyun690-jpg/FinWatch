[CmdletBinding()]
param(
    [ValidateSet('All', 'Live', 'Kakao', 'UserAgent')]
    [string]$Group = 'All',

    [ValidateSet(
        'GEMINI_API_KEY',
        'KIS_APP_KEY',
        'KIS_APP_SECRET',
        'KIS_HTS_ID',
        'NAVER_API_HUB_CLIENT_ID',
        'NAVER_API_HUB_CLIENT_SECRET',
        'FINNHUB_API_KEY',
        'OPENDART_API_KEY',
        'KAKAO_REST_API_KEY',
        'KAKAO_CLIENT_SECRET',
        'KAKAO_REDIRECT_URI',
        'KAKAO_LOGIN_ENABLED',
        'ARTICLE_USER_AGENT',
        'SEC_EDGAR_USER_AGENT'
    )]
    [string[]]$Names,

    [ValidateSet('User', 'Process')]
    [string]$Scope = 'User',

    [switch]$MissingOnly,

    [switch]$StatusOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$entries = @(
    [pscustomobject]@{ Group = 'Live'; Name = 'GEMINI_API_KEY'; Label = 'Gemini API key'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'KIS_APP_KEY'; Label = 'KIS app key'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'KIS_APP_SECRET'; Label = 'KIS app secret'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'KIS_HTS_ID'; Label = 'KIS HTS ID'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'NAVER_API_HUB_CLIENT_ID'; Label = 'Naver API Hub client ID'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'NAVER_API_HUB_CLIENT_SECRET'; Label = 'Naver API Hub client secret'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'FINNHUB_API_KEY'; Label = 'Finnhub API key'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Live'; Name = 'OPENDART_API_KEY'; Label = 'Open DART API key'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Kakao'; Name = 'KAKAO_REST_API_KEY'; Label = 'Kakao REST API key'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Kakao'; Name = 'KAKAO_CLIENT_SECRET'; Label = 'Kakao client secret'; Secret = $true; Default = $null },
    [pscustomobject]@{ Group = 'Kakao'; Name = 'KAKAO_REDIRECT_URI'; Label = 'Kakao redirect URI'; Secret = $false; Default = 'http://localhost:8080/api/v1/auth/kakao/callback' },
    [pscustomobject]@{ Group = 'Kakao'; Name = 'KAKAO_LOGIN_ENABLED'; Label = 'Enable Kakao login'; Secret = $false; Default = 'true' },
    [pscustomobject]@{ Group = 'UserAgent'; Name = 'ARTICLE_USER_AGENT'; Label = 'Article user agent (project/contact)'; Secret = $false; Default = $null },
    [pscustomobject]@{ Group = 'UserAgent'; Name = 'SEC_EDGAR_USER_AGENT'; Label = 'SEC EDGAR user agent (project/contact)'; Secret = $false; Default = $null }
)

if ($Names.Count -gt 0) {
    $entries = @($entries | Where-Object { $_.Name -in $Names })
}
elseif ($Group -ne 'All') {
    $entries = @($entries | Where-Object { $_.Group -eq $Group })
}

function Test-Present([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, $Scope)
    return -not [string]::IsNullOrWhiteSpace($value)
}

if ($MissingOnly) {
    $entries = @($entries | Where-Object { -not (Test-Present $_.Name) })
}

function Read-HiddenValue([string]$Prompt) {
    $secureValue = Read-Host $Prompt -AsSecureString
    if ($secureValue.Length -eq 0) {
        return $null
    }

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $secureValue.Dispose()
    }
}

function Show-Status {
    $entries |
        ForEach-Object {
            [pscustomobject]@{
                Group = $_.Group
                Name = $_.Name
                Configured = Test-Present $_.Name
                Scope = $Scope
            }
        } |
        Format-Table -AutoSize
}

if ($StatusOnly) {
    Show-Status
    exit 0
}

if ($entries.Count -eq 0) {
    Write-Host "All selected values are already configured ($Group / $Scope scope)." -ForegroundColor Green
    exit 0
}

Write-Host "FinWatch local API configuration ($Group / $Scope scope)"
Write-Host 'Secret input is hidden and values are never printed.'
Write-Host 'Press Enter to keep an existing value or skip a missing optional value.'
Write-Host ''

foreach ($entry in $entries) {
    $configured = Test-Present $entry.Name
    $state = if ($configured) { 'configured; Enter keeps it' } else { 'missing; Enter skips it' }
    $value = $null

    if ($entry.Secret) {
        $value = Read-HiddenValue "$($entry.Label) [$($entry.Name), $state]"
    }
    else {
        $nonSecretState = if ($configured) {
            'configured; Enter keeps it'
        }
        elseif (-not [string]::IsNullOrWhiteSpace($entry.Default)) {
            "missing; Enter uses $($entry.Default)"
        }
        else {
            'missing; Enter skips it'
        }
        $value = Read-Host "$($entry.Label) [$($entry.Name), $nonSecretState]"
        $useDefault = [string]::IsNullOrWhiteSpace($value) -and
            -not $configured -and
            -not [string]::IsNullOrWhiteSpace($entry.Default)
        if ($useDefault) {
            $value = $entry.Default
        }
    }

    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Host "  kept/skipped $($entry.Name)"
        continue
    }

    try {
        [Environment]::SetEnvironmentVariable($entry.Name, $value, $Scope)
        Write-Host "  saved $($entry.Name)"
    }
    finally {
        Remove-Variable value -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Show-Status

$liveRequired = @(
    'GEMINI_API_KEY',
    'KIS_APP_KEY',
    'KIS_APP_SECRET',
    'KIS_HTS_ID',
    'NAVER_API_HUB_CLIENT_ID',
    'NAVER_API_HUB_CLIENT_SECRET',
    'FINNHUB_API_KEY',
    'OPENDART_API_KEY'
)
$missingLive = @($liveRequired | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, $Scope))
    })

if ($Group -in @('All', 'Live')) {
    if ($missingLive.Count -eq 0) {
        Write-Host 'LIVE provider keys are complete.' -ForegroundColor Green
    }
    else {
        Write-Warning "LIVE mode is still missing: $($missingLive -join ', ')"
    }
}

if ($Group -in @('All', 'Kakao')) {
    $missingKakao = @(@('KAKAO_REST_API_KEY', 'KAKAO_CLIENT_SECRET', 'KAKAO_REDIRECT_URI') |
            Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, $Scope)) })
    if ($missingKakao.Count -eq 0) {
        Write-Host 'Kakao local variables are complete. Kakao Console redirect registration is still required.' -ForegroundColor Green
    }
    else {
        Write-Warning "Kakao login is still missing: $($missingKakao -join ', ')"
    }
}

if ($Scope -eq 'User') {
    Write-Host 'Saved values will be loaded by scripts/start-local-live.ps1 from the Windows user environment.'
}
