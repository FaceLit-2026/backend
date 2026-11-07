param(
    [ValidateSet('Create','Rotate')][string]$Mode = 'Create',
    [Parameter(Mandatory)][string]$Document,
    [Parameter(Mandatory)][string]$Email,
    [string]$FirstName = '',
    [string]$LastName = '',
    [string]$MavenCommand = (Join-Path $PSScriptRoot '../mvnw.cmd')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Restore-ProcessEnvironment.ps1')
if ($Mode -eq 'Create' -and (!$FirstName.Trim() -or !$LastName.Trim())) { throw 'FirstName and LastName are required for creation.' }
if ($Document -notmatch '^[0-9]{6,15}$') { throw 'Document must contain 6 to 15 digits.' }
$secret = Read-Host 'Nueva contraseña (entrada oculta; no se guarda en el historial)' -AsSecureString
$confirmation = Read-Host 'Confirma la nueva contraseña' -AsSecureString
$secretPointer = [IntPtr]::Zero
$confirmationPointer = [IntPtr]::Zero
$previous = @{}
$settings = @{
    FACELIT_COORDINATOR_BOOTSTRAP = 'true'
    FACELIT_COORDINATOR_ROTATE_PASSWORD = $(if ($Mode -eq 'Rotate') { 'true' } else { 'false' })
    FACELIT_COORDINATOR_EXIT_AFTER = 'true'
    FACELIT_COORDINATOR_DOCUMENT = $Document
    FACELIT_COORDINATOR_EMAIL = $Email
    FACELIT_COORDINATOR_FIRST_NAME = $FirstName
    FACELIT_COORDINATOR_LAST_NAME = $LastName
    SERVER_PORT = '0'
    SERVER_ADDRESS = '127.0.0.1'
    SPRING_DEVTOOLS_RESTART_ENABLED = 'false'
}
foreach ($key in (@($settings.Keys) + 'FACELIT_COORDINATOR_PASSWORD')) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $confirmationPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmation)
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    if ($password -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmationPointer)) { throw 'Las contraseñas no coinciden.' }
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
    [Environment]::SetEnvironmentVariable('FACELIT_COORDINATOR_PASSWORD', $password, 'Process')
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        # DevTools restart can hide a failed ApplicationRunner behind a successful Maven exit.
        # Disable restart in the child JVM itself so failed initialization exits with an error.
        & $MavenCommand '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'La inicialización falló. Revisa el mensaje; no se imprime la contraseña.' }
    } finally { Pop-Location }
} finally {
    Restore-ProcessEnvironment -Snapshot $previous
    if ($secretPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    if ($confirmationPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmationPointer) }
    $password = $null
    $secret.Dispose(); $confirmation.Dispose()
}
