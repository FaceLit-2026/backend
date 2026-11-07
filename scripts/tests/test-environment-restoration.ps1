$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\Restore-ProcessEnvironment.ps1')

$wrapper = Join-Path $PSScriptRoot '..\initialize-coordinator.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($wrapper, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw 'Coordinator initializer has PowerShell syntax errors.' }
$cleanupFinally = $ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.TryStatementAst] -and
        $null -ne $node.Finally -and $node.Finally.Extent.Text -match 'Restore-ProcessEnvironment'
}, $true)
if ($cleanupFinally.Count -eq 0) { throw 'Coordinator initializer does not restore variables from its finally block.' }

$keys = @('FACELIT_TEST_RESTORE_ABSENT', 'FACELIT_TEST_RESTORE_EXISTING')
$original = @{}
foreach ($key in $keys) { $original[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

try {
    foreach ($scenario in @('success', 'error')) {
        [Environment]::SetEnvironmentVariable($keys[0], [NullString]::Value, 'Process')
        [Environment]::SetEnvironmentVariable($keys[1], 'original-value', 'Process')
        $snapshot = @{}
        foreach ($key in $keys) { $snapshot[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

        try {
            [Environment]::SetEnvironmentVariable($keys[0], 'temporary-value', 'Process')
            [Environment]::SetEnvironmentVariable($keys[1], 'temporary-value', 'Process')
            if ($scenario -eq 'error') { throw 'synthetic initializer failure' }
        } catch {
            if ($scenario -ne 'error' -or $_.Exception.Message -ne 'synthetic initializer failure') { throw }
        } finally {
            Restore-ProcessEnvironment -Snapshot $snapshot
        }

        foreach ($key in $keys) {
            $actual = [Environment]::GetEnvironmentVariable($key, 'Process')
            if ($actual -cne $snapshot[$key]) { throw "Environment restoration failed for $key in the $scenario path." }
        }
    }
    'PASS: coordinator environment restoration on success and error, for absent and pre-existing variables.'
} finally {
    Restore-ProcessEnvironment -Snapshot $original
}
