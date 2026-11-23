param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9_-]{1,7}$')][string]$Run,
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$PreparationRun='sw2',
    [ValidateSet('PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED')][string[]]$Stages=@('PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED'),
    [ValidateSet('DROP','PICKUP')][string]$Direction='DROP',
    [string]$PythonPath=''
)
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$orderedStages=@('PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED')
if(@($Stages | Select-Object -Unique).Count -ne $Stages.Count) {throw 'Each checkpoint may be requested once.'}
if(-not $PythonPath) {
    $runtimePath=(Get-AppxPackage '*CodexPrimaryRuntime*' | Select-Object -First 1).InstallLocation
    if($runtimePath) {$PythonPath=Join-Path $runtimePath 'dependencies/python/python.exe'}
}
if(-not $PythonPath -or -not (Test-Path -LiteralPath $PythonPath)) {throw 'Pass -PythonPath to an existing Python3.11+ executable.'}
foreach($stage in $Stages) {
    $index=$orderedStages.IndexOf($stage)+1
    foreach($name in @("${Run}c$index","${Run}r$index")) {
        if(Test-Path -LiteralPath (Join-Path $repoPath "run-adventure-$name")) {throw "Fresh prefix required; preserve existing run $name."}
    }
}
$crashMode=if($Direction -eq 'PICKUP') {'SaberPickupCrash'} else {'SaberCrash'}
$pairs=[Collections.Generic.List[string]]::new()
foreach($stage in $Stages) {
    $index=$orderedStages.IndexOf($stage)+1;$crash="${Run}c$index";$recovery="${Run}r$index"
    Write-Output "RUNNING: $stage, crash $crash then recovery $recovery"
    & (Join-Path $PSScriptRoot 'test-adventure-preparation.ps1') -Run $crash -Mode $crashMode -PreparationRun $PreparationRun -TransferStage $stage -PythonPath $PythonPath
    if($LASTEXITCODE -ne 0) {throw "Crash checkpoint failed: $crash"}
    & (Join-Path $PSScriptRoot 'test-adventure-preparation.ps1') -Run $recovery -Mode SaberCheckpointRecover -PreparationRun $crash -PythonPath $PythonPath
    if($LASTEXITCODE -ne 0) {throw "Recovery failed: $recovery"}
    & $PythonPath (Join-Path $PSScriptRoot 'check-saber-transfer-checkpoints.py') $crash $recovery
    if($LASTEXITCODE -ne 0) {throw "Independent conservation check failed: $crash"}
    $pairs.Add($crash);$pairs.Add($recovery)
}
$checkArgs=@((Join-Path $PSScriptRoot 'check-saber-transfer-checkpoints.py'))+$pairs.ToArray()
if($Stages.Count -eq 5) {$checkArgs+=if($Direction -eq 'PICKUP') {'--all-pickup'} else {'--all-drop'}}
& $PythonPath @checkArgs
if($LASTEXITCODE -ne 0) {throw 'Final checkpoint coverage/conservation failed.'}
