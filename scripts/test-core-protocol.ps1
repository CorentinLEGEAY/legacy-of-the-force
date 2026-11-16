param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='cp1')
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$world=Join-Path $repoPath "run-core-protocol-$Run/world";$prefix=Join-Path $repoPath ".tools/core-protocol-$Run"
if(Test-Path -LiteralPath $world) { throw 'Fresh isolated world required.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if($LASTEXITCODE -ne 0) { throw 'Native client preparation failed.' }
$launch=Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template=(Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw).Replace('127.0.0.1:25587','127.0.0.1:25584')
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-core-protocol-server.ps1'),'-Run',$Run)) { $info.ArgumentList.Add($arg) }
$info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
$serverOut=$runner.StandardOutput.ReadToEndAsync();$serverErr=$runner.StandardError.ReadToEndAsync();$log=Join-Path $repoPath "run-core-protocol-$Run/logs/latest.log"
$clients=[Collections.Generic.List[object]]::new();$results=[Collections.Generic.List[object]]::new();$observed=$false
function Server-Text { if(Test-Path -LiteralPath $log) { return Get-Content -LiteralPath $log -Raw };return '' }
function Start-Client([string]$Case) {
    $admin=$Case -in @('Admin','Reconnect');$suffix=if($admin) {'one'} else {'two'};$name=if($admin) {'ProtocolAdmin'} else {'ProtocolGuest'};$profile=Join-Path $repoPath "run-core-protocol-client-$Case-$Run"
    if(Test-Path -LiteralPath $profile) { throw 'Fresh native client profile required.' }
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
    $program="$prefix-$Case-args.txt";$template.Replace('ForceTesterTwo',$name) | Set-Content -LiteralPath $program -Encoding utf8NoBOM
    $arguments=[Collections.Generic.List[string]]::new();foreach($arg in $launch.arguments) { $arguments.Add([string]$arg) }
    $main=$arguments.IndexOf('net.neoforged.devlaunch.Main');if($main -lt 0) { throw 'Unexpected native launcher.' }
    foreach($arg in @('-Xms256M','-Xmx1536M','-Dlotf.validation.coreProtocol=true',"-Dlotf.validation.coreCase=$Case")) { $arguments.Insert($main,$arg);$main++ }
    $arguments[$arguments.Count-1]='@'+$program.Replace('\','\\')
    $ci=[Diagnostics.ProcessStartInfo]::new();$ci.FileName=Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
    foreach($arg in $arguments) { $ci.ArgumentList.Add($arg) }
    $ci.WorkingDirectory=$profile;$ci.UseShellExecute=$false;$ci.CreateNoWindow=$true;$ci.RedirectStandardOutput=$true;$ci.RedirectStandardError=$true
    foreach($entry in $launch.environment.PSObject.Properties) { $ci.Environment[$entry.Name]=[string]$entry.Value }
    $client=[Diagnostics.Process]::new();$client.StartInfo=$ci;$client.Start() | Out-Null
    $item=@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Case=$Case};$clients.Add($item)
    Write-Host "STARTED: $Case $name PID=$($client.Id)";return $item
}
function Check-Clients {
    if($runner.HasExited -or (Server-Text) -match 'CORE_PROTOCOL_SERVER_FAILED') { throw 'Native protocol server failed.' }
    foreach($item in $clients) {
        $clientLog=Join-Path $item.Profile 'logs/latest.log'
        if((Test-Path -LiteralPath $clientLog) -and (Get-Content -LiteralPath $clientLog -Raw) -match 'CORE_PROTOCOL_CLIENT_FAILED') { throw "Native driver failed: $($item.Case)" }
        if($item.Process.HasExited -and ($item.Process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $item.Profile 'validation/core-protocol-client.nbt')))) { throw "Native client exited without proof: $($item.Case)" }
    }
}
function Wait-Client($item) {
    $deadline=(Get-Date).AddSeconds(240)
    while(-not $item.Process.HasExited -and (Get-Date) -lt $deadline) { Check-Clients;Start-Sleep -Milliseconds 250 }
    Check-Clients;if(-not $item.Process.HasExited) { throw "Native case deadline: $($item.Case)" }
}
try {
    $deadline=(Get-Date).AddSeconds(120)
    $ready=$false
    while(-not $runner.HasExited -and (Get-Date) -lt $deadline) { $snapshot=[string](Server-Text);if($snapshot -match 'Done \([0-9.,]+s\)!') { $ready=$true;break };Start-Sleep -Milliseconds 250 }
    if(-not $ready -or $runner.HasExited) { throw 'Native server not ready.' }
    $admin=Start-Client 'Admin';$guest=Start-Client 'Guest';Wait-Client $admin
    $reconnect=Start-Client 'Reconnect';Wait-Client $guest
    foreach($case in @('WrongDirection','Malformed','Mismatch')) { $adverse=Start-Client $case;Wait-Client $adverse }
    $validation=Join-Path $reconnect.Profile 'validation';New-Item -ItemType Directory -Path $validation -Force | Out-Null
    'All independent native cases exited; request final server checkpoint.' | Set-Content -LiteralPath (Join-Path $validation 'finalize.request') -Encoding ascii
    Wait-Client $reconnect
    $observed=(Server-Text) -match 'CORE_PROTOCOL_SERVER_PASS' -and (Test-Path -LiteralPath (Join-Path $world 'validation/core-protocol-server.nbt'))
    if(-not $observed) { throw 'Missing final native server evidence.' }
} finally {
    if(-not $runner.HasExited) { $runner.StandardInput.WriteLine('stop');$runner.StandardInput.Flush() }
    if(-not $runner.WaitForExit(30000)) { throw 'Owned server runner remains live.' }
    ($serverOut.GetAwaiter().GetResult()+$serverErr.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-server.log"
    $serverExit=$runner.ExitCode;$runner.Dispose()
    foreach($item in $clients) {
        $forced=$false;if(-not $item.Process.WaitForExit(20000)) { $forced=$true;$item.Process.Kill();$item.Process.WaitForExit(5000) | Out-Null }
        ($item.Out.GetAwaiter().GetResult()+$item.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-$($item.Case)-console.log"
        $results.Add(@{case=$item.Case;pid=$item.Process.Id;exitCode=$item.Process.ExitCode;forced=$forced;profile=$item.Profile});$item.Process.Dispose()
    }
    @{run=$Run;observed=$observed;serverExitCode=$serverExit;clients=$results.ToArray();finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$prefix-result.json"
}
if(-not $observed -or $serverExit -ne 0 -or @($results | Where-Object { $_.exitCode -ne 0 -or $_.forced }).Count -gt 0 -or (Server-Text) -notmatch 'All dimensions are saved') { throw 'Native protocol scenario/shutdown incomplete.' }
Write-Output 'OBSERVED: six native cases and final unchanged server checkpoint; run independent verifier.'
