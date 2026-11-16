param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='qn1')
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$world=Join-Path $repoPath "run-quests-$Run/world";$prefix=Join-Path $repoPath ".tools/quest-network-$Run"
if(Test-Path -LiteralPath $world) { throw 'Fresh isolated run required; preserve prior worlds.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if($LASTEXITCODE -ne 0) { throw 'Native client preparation failed.' }
$launch=Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template=(Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw).Replace('127.0.0.1:25587','127.0.0.1:25586')
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-quest-test-server.ps1'),'-Run',$Run,'-Network')) { $info.ArgumentList.Add($arg) }
$info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
$serverOut=$runner.StandardOutput.ReadToEndAsync();$serverErr=$runner.StandardError.ReadToEndAsync();$log=Join-Path $repoPath "run-quests-$Run/logs/latest.log"
$clients=[Collections.Generic.List[object]]::new();$results=[Collections.Generic.List[object]]::new();$observed=$false
function Server-Text { if((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) { return Get-Content -LiteralPath $log -Raw };return '' }
try {
    $deadline=(Get-Date).AddSeconds(120)
    $ready=$false
    while(-not $runner.HasExited -and (Get-Date) -lt $deadline) { $snapshot=Server-Text;if($snapshot -match 'Done \([0-9.,]+s\)!') { $ready=$true;break };Start-Sleep -Milliseconds 250 }
    if(-not $ready -or $runner.HasExited) { throw 'Quest network server not ready.' }
    foreach($index in 1,2) {
        $suffix=if($index -eq 1) {'one'} else {'two'};$name=if($index -eq 1) {'QuestViewer'} else {'QuestReader'};$profile=Join-Path $repoPath "run-quest-network-client-$suffix-$Run"
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
        $argsList.Insert($main,'-Xms256M');$argsList.Insert($main,'-Xmx1536M');$argsList.Insert($main,'-Dlotf.validation.questNetwork=true');$argsList[$argsList.Count-1]='@'+$program.Replace('\','\\')
        $ci=[Diagnostics.ProcessStartInfo]::new();$ci.FileName=Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
        foreach($arg in $argsList) { $ci.ArgumentList.Add($arg) }
        $ci.WorkingDirectory=$profile;$ci.UseShellExecute=$false;$ci.CreateNoWindow=$true;$ci.RedirectStandardOutput=$true;$ci.RedirectStandardError=$true
        foreach($entry in $launch.environment.PSObject.Properties) { $ci.Environment[$entry.Name]=[string]$entry.Value }
        $client=[Diagnostics.Process]::new();$client.StartInfo=$ci;$client.Start() | Out-Null
        $clients.Add(@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Name=$name})
        Write-Output "STARTED: native $name PID=$($client.Id)"
    }
    $deadline=(Get-Date).AddSeconds(300)
    while(-not $runner.HasExited -and (Get-Date) -lt $deadline) {
        $snapshot=Server-Text
        if($snapshot -match 'QUEST_NETWORK_SERVER_FAILED') { throw 'Native journal server verification failed; inspect logs.' }
        $finished=0
        foreach($item in $clients) {
            $clientLog=Join-Path $item.Profile 'logs/latest.log'
            if((Test-Path -LiteralPath $clientLog) -and (Get-Content -LiteralPath $clientLog -Raw) -match 'QUEST_NETWORK_CLIENT_FAILED') { throw "Native client driver failed: $($item.Name)" }
            if($item.Process.HasExited) {
                if($item.Process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $item.Profile 'validation/quest-network-client.nbt'))) { throw "Native client exited without a valid proof: $($item.Name)" }
                $finished++
            }
        }
        if($finished -eq 2 -and $snapshot -match 'QUEST_NETWORK_SERVER_PASS' -and (Test-Path -LiteralPath (Join-Path $world 'validation/quest-network-server.nbt'))) { $observed=$true;break }
        Start-Sleep -Milliseconds 250
    }
    if(-not $observed) { throw 'Native quest network scenario did not finish.' }
} finally {
    if(-not $runner.HasExited) { $runner.StandardInput.WriteLine('stop');$runner.StandardInput.Flush() }
    if(-not $runner.WaitForExit(30000)) { throw 'Owned network server runner remains live.' }
    ($serverOut.GetAwaiter().GetResult()+$serverErr.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-server.log"
    $serverExit=$runner.ExitCode;$runner.Dispose()
    foreach($item in $clients) {
        $forced=$false;if(-not $item.Process.WaitForExit(20000)) { $forced=$true;$item.Process.Kill();$item.Process.WaitForExit(5000) | Out-Null }
        ($item.Out.GetAwaiter().GetResult()+$item.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-$($item.Name)-console.log"
        $results.Add(@{name=$item.Name;pid=$item.Process.Id;exitCode=$item.Process.ExitCode;forced=$forced;profile=$item.Profile});$item.Process.Dispose()
    }
    @{run=$Run;observed=$observed;serverExitCode=$serverExit;clients=$results.ToArray();finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$prefix-result.json"
}
if($serverExit -ne 0 -or @($results | Where-Object { $_.exitCode -ne 0 -or $_.forced }).Count -gt 0 -or (Server-Text) -notmatch 'All dimensions are saved') { throw 'Native server/client shutdown not clean.' }
foreach($item in $results) { if(-not (Test-Path -LiteralPath (Join-Path $item.profile 'validation/quest-network-client.nbt'))) { throw 'Missing native client proof.' } }
Write-Output 'OBSERVED: server and both native client proofs. Run the independent verifier before validation.'
