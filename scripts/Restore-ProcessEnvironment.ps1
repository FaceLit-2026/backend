function Restore-ProcessEnvironment {
    param([Parameter(Mandatory)][hashtable]$Snapshot)

    foreach ($key in $Snapshot.Keys) {
        $value = $Snapshot[$key]
        if ($null -eq $value) {
            [Environment]::SetEnvironmentVariable($key, [NullString]::Value, 'Process')
        } else {
            [Environment]::SetEnvironmentVariable($key, [string]$value, 'Process')
        }
    }
}
