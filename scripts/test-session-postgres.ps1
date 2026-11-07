param(
    [string]$DatabaseRepository = (Join-Path $PSScriptRoot '../../FaceLit-DB'),
    [string]$MavenCommand = (Join-Path $PSScriptRoot '../mvnw.cmd')
)
$ErrorActionPreference = 'Stop'
$databasePath = (Resolve-Path -LiteralPath $DatabaseRepository).Path
if (!(Test-Path -LiteralPath (Join-Path $databasePath 'changelog-master.yaml'))) { throw 'Database changelog not found.' }
$container = "facelit-session-check-$PID"
$created = $false
$settings = @{
    FACELIT_SESSION_TEST_DB = 'jdbc:postgresql://127.0.0.1:55446/facelit_session_test'
    FACELIT_SESSION_TEST_DRIVER = 'org.postgresql.Driver'
    FACELIT_SESSION_TEST_USER = 'session_test'
    FACELIT_SESSION_TEST_PASSWORD = 'session-test-local-only'
    FACELIT_SESSION_TEST_DDL = 'validate'
    FACELIT_SESSION_TEST_DIALECT = 'org.hibernate.dialect.PostgreSQLDialect'
}
$previous = @{}
foreach ($key in $settings.Keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker failed: $($args[0])" }
}
try {
    # A fresh disposable container with tmpfs, no production volume or credentials.
    Invoke-Docker create --name $container --label facelit.test=session-flow --tmpfs /var/lib/postgresql/data `
        -p 127.0.0.1:55446:5432 -e POSTGRES_DB=facelit_session_test -e POSTGRES_USER=session_test `
        -e POSTGRES_PASSWORD=session-test-local-only pgvector/pgvector:pg16
    $created = $true
    Invoke-Docker start $container
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & docker exec $container pg_isready -U session_test -d facelit_session_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Temporary PostgreSQL did not become ready.' }
    Invoke-Docker build -t facelit-liquibase:5.0.2 -f (Join-Path $databasePath 'docker/liquibase/Dockerfile') $databasePath
    $liquibase = @('run','--rm','--network',"container:$container",'--mount',"type=bind,source=$databasePath,target=/workspace,readonly",'facelit-liquibase:5.0.2',
        '--search-path=/workspace','--changelog-file=changelog-master.yaml','--classpath=/liquibase/liquibase_libs/postgresql-42.7.8.jar',
        '--url=jdbc:postgresql://127.0.0.1:5432/facelit_session_test','--username=session_test','--password=session-test-local-only')
    Invoke-Docker @liquibase validate
    Invoke-Docker @liquibase update
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & $MavenCommand '-Dtest=EnvironmentSessionIntegrationTest' test
        if ($LASTEXITCODE -ne 0) { throw 'Session integration tests failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    if ($created) {
        & docker rm -f $container
        if ($LASTEXITCODE -ne 0) { throw "Could not remove temporary container $container" }
    }
}
