param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='ap1', [ValidateSet('Prep','Travel')][string]$Mode='Prep', [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$PreparationRun='ap4', [switch]$SmallViewport)
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$world=Join-Path $repoPath "run-adventure-$Run/world";$prefix=Join-Path $repoPath ".tools/adventure-preparation-$Run"
if(Test-Path -LiteralPath $world) { throw 'Fresh isolated run required; preserve prior worlds.' }
if($Mode -eq 'Travel') {
    $source=Join-Path $repoPath "run-adventure-$PreparationRun/world"
    if(-not (Test-Path -LiteralPath (Join-Path $source 'validation/adventure-preparation-server.nbt'))) { throw 'Stopped native preparation proof required.' }
    $active=Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like "*run-adventure-$PreparationRun*" }
    if($active) { throw 'Source world still has an active JVM.' }
    $sourceLock=Join-Path $source 'session.lock'
    $lockStream=[IO.File]::Open($sourceLock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try { $null=$lockStream.ReadByte() } finally { $lockStream.Dispose() }
    New-Item -ItemType Directory -Path (Split-Path -Parent $world) | Out-Null
    Copy-Item -LiteralPath $source -Destination $world -Recurse
    $prefix=Join-Path $repoPath ".tools/adventure-travel-$Run"
}
$validationFlag=if($Mode -eq 'Prep') {'adventurePrep'} else {'adventureTravel'}
$artifact=if($Mode -eq 'Prep') {'preparation'} else {'travel'}
$marker=if($Mode -eq 'Prep') {'ADVENTURE_PREPARATION'} else {'ADVENTURE_TRAVEL'}
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if($LASTEXITCODE -ne 0) { throw 'Native client preparation failed.' }
$launch=Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template=(Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw).Replace('127.0.0.1:25587','127.0.0.1:25586')
if($SmallViewport) { $template+="`n--width`n640`n--height`n480`n" }
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-adventure-test-server.ps1'),'-Run',$Run,'-Mode',$Mode)) { $info.ArgumentList.Add($arg) }
$info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
$serverOut=$runner.StandardOutput.ReadToEndAsync();$serverErr=$runner.StandardError.ReadToEndAsync();$log=Join-Path $repoPath "run-adventure-$Run/logs/latest.log"
$clients=[Collections.Generic.List[object]]::new();$results=[Collections.Generic.List[object]]::new();$observed=$false
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
        $argsList.Insert($main,'-Xms256M');$argsList.Insert($main,'-Xmx1536M');$argsList.Insert($main,"-Dlotf.validation.$validationFlag=true");$argsList[$argsList.Count-1]='@'+$program.Replace('\','\\')
        $ci=[Diagnostics.ProcessStartInfo]::new();$ci.FileName=Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
        foreach($arg in $argsList) { $ci.ArgumentList.Add($arg) }
        $ci.WorkingDirectory=$profile;$ci.UseShellExecute=$false;$ci.CreateNoWindow=$true;$ci.RedirectStandardOutput=$true;$ci.RedirectStandardError=$true
        foreach($entry in $launch.environment.PSObject.Properties) { $ci.Environment[$entry.Name]=[string]$entry.Value }
        $client=[Diagnostics.Process]::new();$client.StartInfo=$ci;$client.Start() | Out-Null
        $clients.Add(@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Name=$name})
        Write-Output "STARTED: native $name PID=$($client.Id)"
    }
    $deadline=(Get-Date).AddSeconds(660)
    while(-not $runner.HasExited -and (Get-Date) -lt $deadline) {
        $snapshot=Server-Text
        if($snapshot -match "$($marker)_SERVER_FAILED") { throw 'Native adventure server verification failed; inspect logs.' }
        $finished=0
        foreach($item in $clients) {
            $clientLog=Join-Path $item.Profile 'logs/latest.log'
            if((Test-Path -LiteralPath $clientLog) -and (Get-Content -LiteralPath $clientLog -Raw) -match "$($marker)_CLIENT_FAILED") { throw "Native client driver failed: $($item.Name)" }
            if($item.Process.HasExited) {
                if($item.Process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $item.Profile "validation/adventure-$artifact-client.nbt"))) { throw "Native client exited without a valid proof: $($item.Name)" }
                $finished++
            }
        }
        if($finished -eq 2 -and $snapshot -match "$($marker)_SERVER_PASS" -and (Test-Path -LiteralPath (Join-Path $world "validation/adventure-$artifact-server.nbt"))) { $observed=$true;break }
        Start-Sleep -Milliseconds 250
    }
    if(-not $observed) { throw 'Native adventure preparation scenario did not finish.' }
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
foreach($item in $results) { if(-not (Test-Path -LiteralPath (Join-Path $item.profile "validation/adventure-$artifact-client.nbt"))) { throw 'Missing native client proof.' } }
Write-Output 'OBSERVED: server and both native client proofs. Run the independent verifier before validation.'
