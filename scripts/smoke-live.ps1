[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminEmail = 'admin@finwatch.local',
    [string]$AdminPassword = 'FinWatchAdmin123!',
    [switch]$SkipAi
)

$ErrorActionPreference = 'Stop'
$results = [System.Collections.Generic.List[object]]::new()
$adminHeaders = $null
$webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-SmokeStep {
    param(
        [string]$Name,
        [scriptblock]$Action,
        [bool]$Required = $true
    )
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $value = & $Action
        $watch.Stop()
        $script:results.Add([pscustomobject]@{
            Step = $Name
            Status = 'PASS'
            Required = $Required
            DurationMs = $watch.ElapsedMilliseconds
            Detail = if ($null -eq $value) { 'OK' } else { [string]$value }
        })
        return $value
    }
    catch {
        $watch.Stop()
        $message = $_.Exception.Message
        if ($_.ErrorDetails.Message) {
            $message = $_.ErrorDetails.Message
        }
        $script:results.Add([pscustomobject]@{
            Step = $Name
            Status = if ($Required) { 'FAIL' } else { 'WARN' }
            Required = $Required
            DurationMs = $watch.ElapsedMilliseconds
            Detail = $message
        })
        return $null
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 30
        WebSession = $webSession
    }
    if ($Method -notin @('GET', 'HEAD', 'OPTIONS')) {
        $xsrf = $webSession.Cookies.GetCookies($BaseUrl)['XSRF-TOKEN']
        if ($null -ne $xsrf) {
            $params.Headers['X-XSRF-TOKEN'] = [Uri]::UnescapeDataString($xsrf.Value)
        }
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    Invoke-RestMethod @params
}

Invoke-SmokeStep 'application health' {
    $response = Invoke-Api GET '/api/v1/health'
    if ($response.status -ne 'UP') { throw "status=$($response.status)" }
    $response.status
} | Out-Null

Invoke-SmokeStep 'liveness' {
    $response = Invoke-Api GET '/actuator/health/liveness'
    if ($response.status -ne 'UP') { throw "status=$($response.status)" }
    $response.status
} | Out-Null

Invoke-SmokeStep 'PostgreSQL + Redis readiness' {
    $response = Invoke-Api GET '/actuator/health/readiness'
    if ($response.status -ne 'UP') { throw "status=$($response.status)" }
    $response.status
} | Out-Null

Invoke-SmokeStep 'admin login' {
    $response = Invoke-Api POST '/api/v1/auth/login' @{
        email = $AdminEmail
        password = $AdminPassword
    }
    if (-not $response.data.authenticated) { throw 'HttpOnly session was not created.' }
    $sessionCookie = $webSession.Cookies.GetCookies($BaseUrl)['FW_SESSION']
    if ($null -eq $sessionCookie) { throw 'FW_SESSION cookie is missing.' }
    $script:adminHeaders = @{}
    "role=$($response.data.user.role)"
} | Out-Null

if ($null -ne $adminHeaders) {
    Invoke-SmokeStep 'stock search + PostgreSQL read' {
        $response = Invoke-Api GET '/api/v1/stocks/search?q=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90&market=KRX&page=0&size=5' $null $adminHeaders
        if ($response.data.items.Count -lt 1) { throw 'Stock search returned no items.' }
        "items=$($response.data.items.Count)"
    } | Out-Null

    Invoke-SmokeStep 'searchable unlimited watchlist add/remove' {
        $search = Invoke-Api GET '/api/v1/stocks/search?q=MSFT&market=NASDAQ&page=0&size=5' $null $adminHeaders
        $candidate = $search.data.items | Where-Object { $_.market -eq 'NASDAQ' -and $_.symbol -eq 'MSFT' } | Select-Object -First 1
        if ($null -eq $candidate) { throw 'MSFT was not found in the local instrument catalog.' }

        $before = Invoke-Api GET '/api/v1/watchlists' $null $adminHeaders
        $alreadyPresent = @($before.data | Where-Object { $_.market -eq 'NASDAQ' -and $_.symbol -eq 'MSFT' }).Count -gt 0
        if ($alreadyPresent) {
            return "already-present=true, count=$(@($before.data).Count)"
        }

        Invoke-Api POST '/api/v1/watchlists' @{ market = 'NASDAQ'; symbol = 'MSFT' } $adminHeaders | Out-Null
        $afterAdd = Invoke-Api GET '/api/v1/watchlists' $null $adminHeaders
        if (@($afterAdd.data | Where-Object { $_.market -eq 'NASDAQ' -and $_.symbol -eq 'MSFT' }).Count -ne 1) {
            throw 'Canonical watchlist add was not persisted.'
        }

        Invoke-Api DELETE '/api/v1/watchlists/NASDAQ/MSFT' $null $adminHeaders | Out-Null
        $afterDelete = Invoke-Api GET '/api/v1/watchlists' $null $adminHeaders
        if (@($afterDelete.data | Where-Object { $_.market -eq 'NASDAQ' -and $_.symbol -eq 'MSFT' }).Count -ne 0) {
            throw 'Canonical watchlist delete was not persisted.'
        }
        "countBefore=$(@($before.data).Count), countAfter=$(@($afterDelete.data).Count)"
    } | Out-Null

    Invoke-SmokeStep 'KIS quote' {
        $response = Invoke-Api GET '/api/v1/providers/kis/quotes/005930' $null $adminHeaders
        if ($response.data.price -le 0) { throw 'Quote price is not positive.' }
        "price=$($response.data.price)"
    } | Out-Null

    $today = Get-Date
    $from = $today.AddDays(-14).ToString('yyyy-MM-dd')
    $to = $today.ToString('yyyy-MM-dd')
    Invoke-SmokeStep 'KIS daily bars' {
        $response = Invoke-Api GET "/api/v1/providers/kis/bars/005930?from=$from&to=$to" $null $adminHeaders
        if ($response.data.items.Count -lt 1) { throw 'Daily bars returned no items.' }
        "bars=$($response.data.items.Count)"
    } | Out-Null

    Invoke-SmokeStep 'NAVER news' {
        $query = '%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90'
        $response = Invoke-Api GET "/api/v1/providers/naver/news?query=$query&display=3" $null $adminHeaders
        if ($response.data.items.Count -lt 1) { throw 'NAVER returned no news items.' }
        "items=$($response.data.items.Count)"
    } | Out-Null

    Invoke-SmokeStep 'Finnhub company news' {
        $response = Invoke-Api GET "/api/v1/providers/finnhub/news?symbol=AAPL&from=$from&to=$to" $null $adminHeaders
        if ($response.data.items.Count -lt 1) { throw 'Finnhub returned no company news items.' }
        "items=$($response.data.items.Count)"
    } | Out-Null

    Invoke-SmokeStep 'OpenDART disclosures' {
        $response = Invoke-Api POST '/api/v1/stocks/KRX/005930/data-loads' @{
            resources = @('DISCLOSURES')
        } $adminHeaders
        $job = $response.data
        for ($attempt = 0; $attempt -lt 20 -and $job.status -in @('PENDING', 'RUNNING', 'SYNCING'); $attempt++) {
            Start-Sleep -Milliseconds 500
            $poll = Invoke-Api GET "/api/v1/stocks/KRX/005930/data-loads/$($job.jobId)" $null $adminHeaders
            $job = $poll.data
        }
        $disclosure = $job.resources | Where-Object resource -eq 'DISCLOSURES' | Select-Object -First 1
        if ($null -eq $disclosure -or $disclosure.status -ne 'READY') {
            throw "status=$($disclosure.status), message=$($disclosure.message)"
        }
        "imported=$($disclosure.imported)"
    } | Out-Null

    Invoke-SmokeStep 'USD/KRW provider fallback' {
        $response = Invoke-Api GET '/api/v1/market/fx-rates/USD/KRW' $null $adminHeaders
        if ($response.data.rate -le 0) { throw 'FX rate is not positive.' }
        "rate=$($response.data.rate), source=$($response.data.source)"
    } | Out-Null

    if (-not $SkipAi) {
        Invoke-SmokeStep 'Gemini news summary' {
            $news = Invoke-Api GET '/api/v1/stocks/KRX/000660/news' $null $adminHeaders
            $candidate = $news.data | Where-Object aiAnalysisAllowed -eq $true | Select-Object -First 1
            if ($null -eq $candidate) { throw 'No stored news item allows AI analysis.' }
            $response = Invoke-Api POST '/api/v1/ai/news-summaries' @{ newsId = $candidate.id } $adminHeaders
            if ([string]::IsNullOrWhiteSpace($response.data.summary)) { throw 'AI summary is empty.' }
            if ($response.data.modelName -like 'finwatch-demo*') { throw 'Cached Mock AI result was returned instead of Gemini.' }
            "model=$($response.data.modelName), cacheHit=$($response.data.cacheHit)"
        } | Out-Null
    }
}

$results | Format-Table Step, Status, Required, DurationMs, Detail -AutoSize -Wrap
$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
if ($failed.Count -gt 0) {
    throw "$($failed.Count) required LIVE smoke checks failed."
}

Write-Host "LIVE smoke passed: $(@($results | Where-Object Status -eq 'PASS').Count), warnings: $(@($results | Where-Object Status -eq 'WARN').Count)"
