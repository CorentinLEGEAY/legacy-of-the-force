param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='ap1', [ValidateSet('Prep','Travel','Journey','Recovery','Progress','ProgressRecovery','Rewards','RewardsRecovery','Block','BlockRecover','Impulse','ImpulseCrash','ImpulseRecover','ImpulseUnload','SaberWorkshop','SaberRecover','SaberCrash','SaberPickup','SaberPickupCrash','SaberIo','SaberCheckpointRecover','SaberStorage','SaberStorageRecover','SaberStorageCrash','SaberStorageCheckpointRecover')][string]$Mode='Prep', [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$PreparationRun='ap4', [switch]$SmallViewport, [switch]$DesignGallery, [switch]$PersonalMenu, [switch]$Duel, [switch]$KillHeld, [switch]$StorageDeposit, [string]$PythonPath='', [ValidatePattern('^(|PREPARED|PAYMENT_DURABLE|PLAYER_DURABLE|STORAGE_DURABLE|HELD|ENTITY_DURABLE|CLOSED)$')][string]$TransferStage='')
$ErrorActionPreference='Stop'
if($StorageDeposit -and $Mode -notin @('SaberStorageCrash','SaberStorageCheckpointRecover')) {throw 'StorageDeposit requires an isolated storage checkpoint mode.'}
if($Mode -in @('SaberCrash','SaberPickupCrash') -and $TransferStage -and $TransferStage -notin @('PREPARED','PAYMENT_DURABLE','HELD','ENTITY_DURABLE','CLOSED')) {throw 'Drop/pickup requires a world-item transfer boundary.'}
if($Mode -eq 'SaberStorageCrash' -and $TransferStage -notin @('PREPARED','PLAYER_DURABLE','STORAGE_DURABLE','CLOSED')) {throw 'SaberStorageCrash requires an actual storage boundary.'}
if($PersonalMenu -and $Mode -ne 'SaberCrash') {throw 'PersonalMenu requires isolated SaberCrash mode.'}
if($Mode -eq 'SaberIo' -and $TransferStage -notin @('PREPARED','PAYMENT_DURABLE','HELD','CLOSED')) {throw 'SaberIo requires a supported TransferStage.'}
if($DesignGallery -and $Mode -ne 'SaberRecover') {throw 'DesignGallery requires isolated SaberRecover mode.'}
if($Mode -eq 'SaberPickupCrash' -and -not $TransferStage) {throw 'SaberPickupCrash requires TransferStage.'}
if($TransferStage -and $Mode -notin @('SaberCrash','SaberPickupCrash','SaberIo','SaberStorageCrash')) { throw 'TransferStage requires an isolated world-item or storage fault mode.' }
$killRequested=$KillHeld -or $Mode -in @('ImpulseCrash','SaberCrash','SaberPickupCrash','SaberStorageCrash')
$checkpointName=if($Mode -eq 'SaberStorageCrash') {if($StorageDeposit) {'saber-storage-deposit-checkpoint.nbt'} else {'saber-storage-checkpoint.nbt'}} elseif($Mode -in @('SaberCrash','SaberPickupCrash') -and $TransferStage) {'saber-transfer-checkpoint.nbt'} elseif($Mode -eq 'SaberCrash') {'saber-crash-drop.nbt'} elseif($Mode -eq 'ImpulseCrash') {'impulse-crash-active.nbt'} else {'block-crash-held.nbt'}
if($KillHeld -and $Mode -ne 'Block') { throw 'KillHeld requires isolated Block mode.' }
if($Duel -and $Mode -ne 'Journey') { throw 'Duel requires the legitimate journey prefix.' }
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$world=Join-Path $repoPath "run-adventure-$Run/world";$prefix=Join-Path $repoPath ".tools/adventure-preparation-$Run"
if(Test-Path -LiteralPath $world) { throw 'Fresh isolated run required; preserve prior worlds.' }
if($Mode -ne 'Prep') {
    $source=Join-Path $repoPath "run-adventure-$PreparationRun/world"
    if(-not (Test-Path -LiteralPath (Join-Path $source 'validation/adventure-preparation-server.nbt'))) { throw 'Stopped native preparation proof required.' }
    if($Mode -in @('Recovery','Progress','ProgressRecovery') -and -not (Test-Path -LiteralPath (Join-Path $source 'validation/adventure-personalization-stage.nbt'))) { throw 'Completed paid route required before recovery.' }
    $active=Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like "*run-adventure-$PreparationRun*" }
    if($active) { throw 'Source world still has an active JVM.' }
    $sourceLock=Join-Path $source 'session.lock'
    $lockStream=[IO.File]::Open($sourceLock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try { $null=$lockStream.ReadByte() } finally { $lockStream.Dispose() }
    New-Item -ItemType Directory -Path (Split-Path -Parent $world) | Out-Null
    Copy-Item -LiteralPath $source -Destination $world -Recurse
    $prefix=Join-Path $repoPath ".tools/adventure-$($Mode.ToLowerInvariant())-$Run"
}
$validationFlag=if($Mode -eq 'Prep') {'adventurePrep'} elseif($Mode -eq 'Recovery') {'adventureRecovery'} elseif($Mode -eq 'Progress') {'adventureProgress'} elseif($Mode -eq 'ProgressRecovery') {'adventureProgressRecovery'} elseif($Mode -eq 'Rewards') {'adventureRewards'} elseif($Mode -eq 'RewardsRecovery') {'adventureRewardsRecovery'} elseif($Mode -in @('Block','BlockRecover')) {'adventureBlock'} elseif($Mode -in @('SaberStorageCrash','SaberStorageCheckpointRecover')) {'adventureSaberStorageCrash'} elseif($Mode -in @('SaberStorage','SaberStorageRecover')) {'adventureSaberStorage'} elseif($Mode -eq 'SaberIo') {'adventureSaberIo'} elseif($Mode -eq 'SaberCheckpointRecover') {'adventureSaberCheckpointRecover'} elseif($Mode -in @('SaberPickup','SaberPickupCrash')) {'adventureSaberPickup'} elseif($Mode -in @('SaberWorkshop','SaberRecover','SaberCrash')) {'adventureSaberWorkshop'} elseif($Mode -eq 'Impulse') {'adventureImpulse'} elseif($Mode -in @('ImpulseCrash','ImpulseRecover','ImpulseUnload')) {'adventureImpulseRestart'} else {'adventureTravel'}
$artifact=if($Mode -eq 'BlockRecover') {'block-recovered'} elseif($Mode -eq 'RewardsRecovery') {'rewards-recovery'} elseif($Mode -eq 'ProgressRecovery') {'progress-recovery'} elseif($Mode -eq 'Prep') {'preparation'} else {$Mode.ToLowerInvariant()}
$marker=if($Mode -eq 'BlockRecover') {'ADVENTURE_BLOCK_RECOVER'} elseif($Mode -eq 'RewardsRecovery') {'ADVENTURE_REWARDS_RECOVERY'} elseif($Mode -eq 'ProgressRecovery') {'ADVENTURE_PROGRESS_RECOVERY'} elseif($Mode -eq 'Prep') {'ADVENTURE_PREPARATION'} else {'ADVENTURE_'+$Mode.ToUpperInvariant()}
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if($LASTEXITCODE -ne 0) { throw 'Native client preparation failed.' }
$launch=Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template=(Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw).Replace('127.0.0.1:25587','127.0.0.1:25586')
if($SmallViewport) { $template+="`n--width`n640`n--height`n480`n" }
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-adventure-test-server.ps1'),'-Run',$Run,'-Mode',$Mode)) { $info.ArgumentList.Add($arg) }
if($Duel) { $info.ArgumentList.Add('-Duel') }
if($StorageDeposit) { $info.ArgumentList.Add('-StorageDeposit') }
if($KillHeld) { $info.ArgumentList.Add('-KillHeld') }
if($TransferStage) { $info.ArgumentList.Add('-TransferStage');$info.ArgumentList.Add($TransferStage) }
$info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
$serverOut=$runner.StandardOutput.ReadToEndAsync();$serverErr=$runner.StandardError.ReadToEndAsync();$log=Join-Path $repoPath "run-adventure-$Run/logs/latest.log"
$clients=[Collections.Generic.List[object]]::new();$results=[Collections.Generic.List[object]]::new();$observed=$false;$killed=$false;$killedPid=$null;$killUtc=$null
function Server-Text { if((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) { return Get-Content -LiteralPath $log -Raw };return '' }
try {
    $deadline=(Get-Date).AddSeconds(120)
    $ready=$false
    while(-not $runner.HasExited -and (Get-Date) -lt $deadline) { $snapshot=Server-Text;if($snapshot -match 'Done \([0-9.,]+s\)!') { $ready=$true;break };Start-Sleep -Milliseconds 250 }
    if(-not $ready -or $runner.HasExited) { throw 'Adventure preparation server not ready.' }
    foreach($index in 1,2) {
        $suffix=if($index -eq 1) {'one'} else {'two'};$name=if($index -eq 1) {'AdventureOne'} else {'AdventureTwo'};$profile=Join-Path $repoPath "run-adventure-client-$suffix-$Run"
        if(Test-Path -LiteralPath $profile) { throw 'Fresh native client profiles required.' }
        New-Item -ItemType Directory -Path $profile | Out-Null
        @'
version:3955
onboardAccessibility:false
renderDistance:3
simulationDistance:5
maxFps:30
pauseOnLostFocus:false
fullscreen:false
enableVsync:false
lang:fr_fr
guiScale:2
tutorialStep:none
'@ | Set-Content -LiteralPath (Join-Path $profile 'options.txt') -Encoding ascii
        $program="$prefix-client$index-args.txt";$template.Replace('ForceTesterTwo',$name) | Set-Content -LiteralPath $program -Encoding utf8NoBOM
        $argsList=[Collections.Generic.List[string]]::new();foreach($arg in $launch.arguments) { $argsList.Add([string]$arg) }
        $main=$argsList.IndexOf('net.neoforged.devlaunch.Main');if($main -lt 0) { throw 'Unexpected native launcher.' }
        if($KillHeld) { $argsList.Insert($main,'-Dlotf.validation.blockCrash=true') }
        if($Mode -eq 'BlockRecover') { $argsList.Insert($main,'-Dlotf.validation.blockRecover=true') }
        if($Mode -eq 'ImpulseRecover') { $argsList.Insert($main,'-Dlotf.validation.impulseRecover=true') }
        if($PersonalMenu) { $argsList.Insert($main,'-Dlotf.validation.saberPersonalMenu=true') }
        if($DesignGallery) { $argsList.Insert($main,'-Dlotf.validation.saberDesignGallery=true') }
        if($StorageDeposit) { $argsList.Insert($main,'-Dlotf.validation.saberStorageDeposit=true') }
        if($Mode -eq 'SaberStorageCheckpointRecover') { $argsList.Insert($main,'-Dlotf.validation.saberStorageCheckpointRecover=true') }
        if($Mode -eq 'SaberStorageRecover') { $argsList.Insert($main,'-Dlotf.validation.saberStorageRecover=true') }
        if($Mode -eq 'SaberRecover') { $argsList.Insert($main,'-Dlotf.validation.saberRecover=true') }
        if($Mode -eq 'SaberCrash') { $argsList.Insert($main,'-Dlotf.validation.saberCrash=true') }
        if($Mode -eq 'SaberPickupCrash') { $argsList.Insert($main,'-Dlotf.validation.saberPickupCrash=true') }
        if($Mode -eq 'ImpulseUnload') { $argsList.Insert($main,'-Dlotf.validation.impulseUnload=true') }
        if($Mode -eq 'Journey') { $argsList.Insert($main,'-Dlotf.validation.adventureJourney=true') }
        if($Duel) { $argsList.Insert($main,'-Dlotf.validation.adventureDuel=true') }
        $argsList.Insert($main,'-Xms256M');$argsList.Insert($main,'-Xmx1536M');$argsList.Insert($main,"-Dlotf.validation.$validationFlag=true");$argsList[$argsList.Count-1]='@'+$program.Replace('\','\\')
        $ci=[Diagnostics.ProcessStartInfo]::new();$ci.FileName=Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
        foreach($arg in $argsList) { $ci.ArgumentList.Add($arg) }
        $ci.WorkingDirectory=$profile;$ci.UseShellExecute=$false;$ci.CreateNoWindow=$true;$ci.RedirectStandardOutput=$true;$ci.RedirectStandardError=$true
        foreach($entry in $launch.environment.PSObject.Properties) { $ci.Environment[$entry.Name]=[string]$entry.Value }
        $client=[Diagnostics.Process]::new();$client.StartInfo=$ci;$client.Start() | Out-Null
        $clients.Add(@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Name=$name})
        Write-Output "STARTED: native $name PID=$($client.Id)"
    }
    $deadline=(Get-Date).AddSeconds($(if($Mode -eq 'Journey') {1860} else {660}))
    while((-not $runner.HasExited -or ($killRequested -and $killed)) -and (Get-Date) -lt $deadline) {
        $snapshot=Server-Text
        if($snapshot -match "$($marker)_SERVER_FAILED") { throw 'Native adventure server verification failed; inspect logs.' }
        if($killRequested -and -not $killed -and (Test-Path -LiteralPath (Join-Path $world "validation/$checkpointName"))) {
            $validationPython=$PythonPath
            if(-not $validationPython) {
                $runtimePath=(Get-AppxPackage '*CodexPrimaryRuntime*' | Select-Object -First 1).InstallLocation
                if($runtimePath) { $validationPython=Join-Path $runtimePath 'dependencies/python/python.exe' }
            }
            if(-not $validationPython -or -not (Test-Path -LiteralPath $validationPython)) { throw 'Pass -PythonPath to an existing Python3.11+ executable for bounded checkpoint reading.' }
            $raw=& $validationPython (Join-Path $PSScriptRoot 'read_nbt.py') (Join-Path $world "validation/$checkpointName")
            if($LASTEXITCODE -ne 0) { throw 'Cannot read bounded native kill checkpoint.' }
            $checkpoint=$raw | ConvertFrom-Json
            if(-not [string]::Equals([IO.Path]::GetFullPath($checkpoint.world),[IO.Path]::GetFullPath($world),[StringComparison]::OrdinalIgnoreCase)) { throw 'Native checkpoint world mismatch.' }
            $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($checkpoint.pid)"
            $listener=Get-NetTCPConnection -State Listen -LocalPort 25586 -ErrorAction SilentlyContinue | Where-Object { $_.LocalAddress -eq '127.0.0.1' -and $_.OwningProcess -eq $checkpoint.pid }
            if(-not $owned -or $owned.Name -ne 'java.exe' -or $owned.CommandLine -notlike '*serverRun*' -or -not $listener -or (Get-Process -Id $checkpoint.pid).StartTime -lt $started) { throw 'Owned native loopback server identity not confirmed; nothing killed.' }
            $killedPid=[long]$checkpoint.pid;$killUtc=[DateTime]::UtcNow.ToString('O')
            Stop-Process -Id $killedPid
            $killed=$true;Write-Output "KILLED: owned native Java PID=$killedPid after both native client acknowledgements and durable $checkpointName checkpoint."
        }
        $finished=0
        foreach($item in $clients) {
            $clientLog=Join-Path $item.Profile 'logs/latest.log'
            if((Test-Path -LiteralPath $clientLog) -and (Get-Content -LiteralPath $clientLog -Raw) -match "$($marker)_CLIENT_FAILED") { throw "Native client driver failed: $($item.Name)" }
            if($item.Process.HasExited) {
                if($item.Process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $item.Profile "validation/adventure-$artifact-client.nbt"))) { throw "Native client exited without a valid proof: $($item.Name)" }
                $finished++
            }
        }
        if($killRequested -and $killed -and $finished -eq 2 -and $runner.HasExited) { $observed=$true;break }
        if(-not $killRequested -and $finished -eq 2 -and $snapshot -match "$($marker)_SERVER_PASS" -and (Test-Path -LiteralPath (Join-Path $world "validation/adventure-$artifact-server.nbt"))) { $observed=$true;break }
        Start-Sleep -Milliseconds 250
    }
    if(-not $observed) { throw 'Native adventure preparation scenario did not finish.' }
} finally {
    if(-not $runner.HasExited -and -not $killed) { $runner.StandardInput.WriteLine('stop');$runner.StandardInput.Flush() }
    if(-not $runner.WaitForExit(30000)) { throw 'Owned network server runner remains live.' }
    ($serverOut.GetAwaiter().GetResult()+$serverErr.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-server.log"
    $serverExit=$runner.ExitCode;$runner.Dispose()
    foreach($item in $clients) {
        $forced=$false;if(-not $item.Process.WaitForExit(20000)) { $forced=$true;$item.Process.Kill();$item.Process.WaitForExit(5000) | Out-Null }
        ($item.Out.GetAwaiter().GetResult()+$item.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-$($item.Name)-console.log"
        $results.Add(@{name=$item.Name;pid=$item.Process.Id;exitCode=$item.Process.ExitCode;forced=$forced;profile=$item.Profile});$item.Process.Dispose()
    }
    @{run=$Run;killed=$killed;killedPid=$killedPid;killUtc=$killUtc;observed=$observed;serverExitCode=$serverExit;clients=$results.ToArray();finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$prefix-result.json"
}
if(($killRequested -and (-not $killed -or $serverExit -eq 0)) -or (-not $killRequested -and ($serverExit -ne 0 -or (Server-Text) -notmatch 'All dimensions are saved')) -or @($results | Where-Object { $_.exitCode -ne 0 -or $_.forced }).Count -gt 0) { throw 'Native server/client shutdown not clean.' }
foreach($item in $results) { if(-not (Test-Path -LiteralPath (Join-Path $item.profile "validation/adventure-$artifact-client.nbt"))) { throw 'Missing native client proof.' } }
Write-Output 'OBSERVED: server and both native client proofs. Run the independent verifier before validation.'
