param([ValidatePattern('^[a-z0-9_-]{1,9}$')][string]$Run='ac1', [string[]]$Checkpoints=@('SPAWN_INTENT','SPAWN_DURABLE','VICTORY_PREPARED','REWARD_GRANTED','LOOT_MEMORY','LOOT_DURABLE','EVENT','COMMITTED'))
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$results=[Collections.Generic.List[object]]::new()
function Start-Runner([string]$Name) {
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
    foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-boss-test-server.ps1'),'-Run',$Name)) { $info.ArgumentList.Add($arg) }
    $info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info;$started=Get-Date;$process.Start() | Out-Null
    return @{Process=$process;Out=$process.StandardOutput.ReadToEndAsync();Err=$process.StandardError.ReadToEndAsync();Started=$started;Log=(Join-Path $repoPath "run-boss-$Name/logs/latest.log")}
}
function Wait-Marker($Runner,[string]$Pattern,[int]$Seconds=120) {
    $deadline=(Get-Date).AddSeconds($Seconds)
    while(-not $Runner.Process.HasExited -and (Get-Date) -lt $deadline) {
        if((Test-Path -LiteralPath $Runner.Log) -and (Get-Item -LiteralPath $Runner.Log).LastWriteTime -ge $Runner.Started) {
            $snapshot=Get-Content -LiteralPath $Runner.Log -Raw
            if($snapshot -match 'BOSS_ARENA_CRASH_FAILED|Encountered an unexpected exception') { throw 'Native arena crash fixture failed; preserve log.' }
            if($snapshot -match $Pattern) { return }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Missing native marker: $Pattern"
}
function Send-Console($Runner,[string]$Line) { $Runner.Process.StandardInput.WriteLine($Line);$Runner.Process.StandardInput.Flush() }
function Finish-Runner($Runner,[string]$Path,[bool]$Stop) {
    if($Stop -and -not $Runner.Process.HasExited) { Send-Console $Runner 'stop' }
    if(-not $Runner.Process.WaitForExit(30000)) { throw 'Owned server runner remains live.' }
    ($Runner.Out.GetAwaiter().GetResult()+$Runner.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath $Path
    $code=$Runner.Process.ExitCode;$Runner.Process.Dispose();return $code
}
$index=0
foreach($checkpoint in $Checkpoints) {
    if($checkpoint -notin @('SPAWN_INTENT','SPAWN_MEMORY','SPAWN_DURABLE','ACTIVE','VICTORY_PREPARED','SETTLING','REWARD_GRANTED','REWARDS_DURABLE','LOOT_MEMORY','LOOT_DURABLE','LOOT_STAGED','COOLDOWN','EVENT','COMMITTED','CLOSED','REMOVED')) { throw 'Invalid checkpoint.' }
    $index++;$name="$Run-$index";$runPath=Join-Path $repoPath "run-boss-$name";$world=Join-Path $runPath 'world'
    if(Test-Path -LiteralPath $world) { throw 'Fresh run identifiers required; preserve prior worlds.' }
    $runner=Start-Runner $name;$finished=$false
    try {
        Wait-Marker $runner 'Done \([0-9.,]+s\)!';Send-Console $runner "lotfbossarenacrash prepare $checkpoint"
        Wait-Marker $runner ("BOSS_ARENA_CRASH_PAUSED checkpoint="+$checkpoint) 45
        $ownedServerId=[int](Get-Content -LiteralPath (Join-Path $world 'validation/arena-crash-pid.txt'))
        $native=Get-Process -Id $ownedServerId
        $null=$native.Handle # Keep a native handle open across Stop-Process so ExitCode remains observable.
        $metadata=Get-CimInstance Win32_Process -Filter "ProcessId = $ownedServerId"
        if($metadata.Name -ne 'java.exe' -or $metadata.CommandLine -notmatch 'serverRunProgramArgs.txt' -or $native.StartTime -lt $runner.Started.AddSeconds(-1)) { throw 'PID does not match this owned native server.' }
        Stop-Process -Id $ownedServerId
        if(-not $native.WaitForExit(5000)) { throw 'Native server survived forced termination.' }
        $nativeExit=$native.ExitCode;$native.Dispose()
        $wrapperExit=Finish-Runner $runner (Join-Path $repoPath ".tools/boss-arena-$name-interrupted.log") $false;$finished=$true
        if($nativeExit -eq 0 -or $wrapperExit -eq 0) { throw 'Expected genuine abnormal native termination.' }
        $snapshot=Join-Path $repoPath "run-boss-$name-interrupted"
        if(Test-Path -LiteralPath $snapshot) { throw 'Preserve existing interrupted snapshot.' }
        New-Item -ItemType Directory -Path $snapshot | Out-Null
        Copy-Item -LiteralPath $world -Destination (Join-Path $snapshot 'world') -Recurse
        Write-Output "KILLED: $checkpoint native PID=$ownedServerId exit=$nativeExit"
    } finally { if(-not $finished) { Finish-Runner $runner (Join-Path $repoPath ".tools/boss-arena-$name-failed.log") $true | Out-Null } }
    $runner=Start-Runner $name
    try { Wait-Marker $runner 'Done \([0-9.,]+s\)!';Send-Console $runner 'lotfbossarenacrash recover';Wait-Marker $runner ("BOSS_ARENA_CRASH_RECOVERY_PASS checkpoint="+$checkpoint) 45 }
    finally { $recoveredExit=Finish-Runner $runner (Join-Path $repoPath ".tools/boss-arena-$name-recovered.log") $true }
    if($recoveredExit -ne 0 -or (Get-Content -LiteralPath $runner.Log -Raw) -notmatch 'All dimensions are saved') { throw 'Recovery server did not save and stop normally.' }
    $results.Add(@{run=$name;checkpoint=$checkpoint;killedPid=$ownedServerId;nativeExitCode=$nativeExit;wrapperExitCode=$wrapperExit;recoveryExitCode=$recoveredExit})
    $results.ToArray() | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $repoPath ".tools/boss-arena-$Run-kills.json")
    Write-Output "RECOVERED: $checkpoint, normal shutdown. Independently verify native files before validation."
}
