$ErrorActionPreference = 'Stop'
$offlineContainer = 'fasoecole-offline-test-' + [guid]::NewGuid().ToString('N').Substring(0,12)
try {
    docker run -d --name $offlineContainer --memory 256m --tmpfs /var/lib/postgresql/data:rw,size=128m -p '127.0.0.1::5432' -e POSTGRES_PASSWORD=disposable-test-only -e POSTGRES_DB=school_life_test postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable PostgreSQL' }
    $offlineReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $offlineContainer pg_isready -h 127.0.0.1 -U postgres -d school_life_test 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $offlineReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $offlineReady) { throw 'PostgreSQL did not become ready' }
    $offlinePort = (docker port $offlineContainer 5432/tcp).Trim().Split(':')[-1]
    Push-Location (Split-Path -Parent $PSScriptRoot)
    try {
        # Windows PowerShell must not treat harmless Maven/JVM stderr warnings as terminating errors.
        $ErrorActionPreference = 'Continue'
        & mvn "-DschoolLifeTestUrl=jdbc:postgresql://127.0.0.1:$offlinePort/school_life_test" '-Dtest=OfflineAttendanceServiceTest,MessagingHttpIntegrationTest#offline*' test
        $offlineExitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($offlineExitCode -ne 0) { throw 'Offline attendance checks failed' }
        [xml]$offlineReport = Get-Content -Raw 'target/surefire-reports/TEST-org.afritechinnovations.integration.MessagingHttpIntegrationTest.xml'
        $offlineHttpCases = @($offlineReport.testsuite.testcase | Where-Object { $_.name -like 'offline*' })
        if ($offlineHttpCases.Count -ne 2 -or @($offlineHttpCases | Where-Object { $_.skipped }).Count) {
            throw 'Both offline HTTP tests must actually execute; check compiled test classes and database configuration.'
        }
    } finally { Pop-Location }
} finally {
    # Remove only the uniquely named test container created above.
    docker rm -f $offlineContainer 2>$null | Out-Null
}
