param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'graphic1')
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$prefix = Join-Path $repoPath ".tools/boss-graphics-$Run"
$world = Join-Path $repoPath "run-boss-$Run/world"
if (Test-Path -LiteralPath $world) { throw 'Fresh isolated world required; preserve prior evidence.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
if ($LASTEXITCODE -ne 0) { throw 'Client preparation failed.' }
$launch = Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template = (Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw).Replace('127.0.0.1:25587','127.0.0.1:25588')
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = (Get-Process -Id $PID).Path
foreach ($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-boss-test-server.ps1'),'-Run',$Run)) { $info.ArgumentList.Add($arg) }
$info.WorkingDirectory=$repoPath; $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
$server=[Diagnostics.Process]::new(); $server.StartInfo=$info; $server.Start() | Out-Null
$serverOut=$server.StandardOutput.ReadToEndAsync(); $serverErr=$server.StandardError.ReadToEndAsync()
$log=Join-Path $repoPath "run-boss-$Run/logs/latest.log"
$clients=[Collections.Generic.List[object]]::new(); $results=[Collections.Generic.List[object]]::new()
function Send-Console([string]$Line) { $server.StandardInput.WriteLine($Line); $server.StandardInput.Flush() }
try {
    $deadline=(Get-Date).AddSeconds(120)
    $ready=$false
    while (-not $server.HasExited -and (Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $log) { $snapshot=Get-Content -LiteralPath $log -Raw; if ($snapshot -match 'Done \([0-9.,]+s\)!') { $ready=$true; break } }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready -or $server.HasExited) { throw 'Server not ready.' }
    foreach ($index in 1,2) {
        $suffix=if ($index -eq 1) {'one'} else {'two'}; $name=if ($index -eq 1) {'ForceTesterOne'} else {'ForceTesterTwo'}
        $profile=Join-Path $repoPath "run-boss-client-$suffix-$Run"
        if (Test-Path -LiteralPath $profile) { throw 'Fresh client profile required.' }
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
key_key.use:key.keyboard.u
key_key.attack:key.keyboard.k
'@ | Set-Content -LiteralPath (Join-Path $profile 'options.txt') -Encoding ascii
        $programFile="$prefix-client$index-args.txt"
        $template.Replace('ForceTesterTwo',$name) | Set-Content -LiteralPath $programFile -Encoding utf8NoBOM
        $argsList=[Collections.Generic.List[string]]::new(); foreach ($arg in $launch.arguments) { $argsList.Add([string]$arg) }
        $main=$argsList.IndexOf('net.neoforged.devlaunch.Main'); if ($main -lt 0) { throw 'Unexpected launcher.' }
        $argsList.Insert($main,'-Xms256M'); $argsList.Insert($main,'-Xmx1536M')
        $argsList[$argsList.Count-1]='@'+$programFile.Replace('\','\\')
        $ci=[Diagnostics.ProcessStartInfo]::new(); $ci.FileName=Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
        foreach ($arg in $argsList) { $ci.ArgumentList.Add($arg) }
        $ci.WorkingDirectory=$profile; $ci.UseShellExecute=$false; $ci.CreateNoWindow=$true; $ci.RedirectStandardOutput=$true; $ci.RedirectStandardError=$true
        foreach ($entry in $launch.environment.PSObject.Properties) { $ci.Environment[$entry.Name]=[string]$entry.Value }
        $client=[Diagnostics.Process]::new(); $client.StartInfo=$ci; $client.Start() | Out-Null
        $clients.Add(@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Name=$name})
        Write-Output "STARTED: $name native PID=$($client.Id)"
    }
    $deadline=(Get-Date).AddSeconds(120)
    $joined=$false
    while ((Get-Date) -lt $deadline) {
        $snapshot=Get-Content -LiteralPath $log -Raw
        if ($snapshot -match 'ForceTesterOne joined the game' -and $snapshot -match 'ForceTesterTwo joined the game') { $joined=$true; break }
        if ($server.HasExited -or @($clients | Where-Object {$_.Process.HasExited}).Count -gt 0) { throw 'Client/server exited early.' }
        Start-Sleep -Milliseconds 250
    }
    if (-not $joined) { throw 'Both native clients must join.' }
    Send-Console 'execute in legacyoftheforce:ilum run forceload add 95 95 125 105'
    $deadline=(Get-Date).AddSeconds(30); $loaded=$false
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        Send-Console 'execute in legacyoftheforce:ilum if loaded 95 239 95 if loaded 125 239 105 run say BOSS_GRAPHICS_CHUNKS_READY'
        Start-Sleep -Milliseconds 250
        if ((Get-Content -LiteralPath $log -Raw) -match 'BOSS_GRAPHICS_CHUNKS_READY') { $loaded=$true; break }
    }
    if (-not $loaded) { throw 'Graphical fixture chunks did not load.' }
    # Explicit graphical fixture: real survival clients, a constructed arena, no automated fight or healing.
    foreach ($command in @(
        'gamerule doDaylightCycle false','execute in legacyoftheforce:ilum run time set day',
        'execute in legacyoftheforce:ilum run fill 95 239 95 105 239 105 minecraft:stone_bricks',
        'execute in legacyoftheforce:ilum run setblock 100 240 103 legacyoftheforce:jedi_trial_altar',
        'execute in legacyoftheforce:ilum run fill 115 239 95 125 239 105 minecraft:stone_bricks',
        'execute in legacyoftheforce:ilum run setblock 120 240 103 legacyoftheforce:sith_trial_altar',
        'op ForceTesterOne','op ForceTesterTwo',
        'execute in legacyoftheforce:ilum run tp ForceTesterOne 100.5 240 101.5 0 40',
        'execute in legacyoftheforce:ilum run tp ForceTesterTwo 103.5 240 102.5 90 0',
        'deop ForceTesterOne','deop ForceTesterTwo',
        'give @a minecraft:diamond_sword','give @a minecraft:golden_apple 16',
        'item replace entity @a armor.head with minecraft:diamond_helmet',
        'item replace entity @a armor.chest with minecraft:diamond_chestplate',
        'item replace entity @a armor.legs with minecraft:diamond_leggings',
        'item replace entity @a armor.feet with minecraft:diamond_boots',
        'gamemode survival @a','save-all flush')) { Send-Console $command }
    Write-Output "READY: two survival clients, explicit Ilum fixture. Console lines: $prefix-commands.txt ; stop marker: $prefix-stop"
    $linesRead=0; $deadline=(Get-Date).AddMinutes(25)
    while (-not $server.HasExited -and (Get-Date) -lt $deadline -and -not (Test-Path -LiteralPath "$prefix-stop")) {
        if (Test-Path -LiteralPath "$prefix-commands.txt") {
            $lines=@(Get-Content -LiteralPath "$prefix-commands.txt")
            while ($linesRead -lt $lines.Count) { if ($lines[$linesRead].Trim()) { Send-Console $lines[$linesRead] }; $linesRead++ }
        }
        if (@($clients | Where-Object {-not $_.Process.HasExited}).Count -eq 0) { break }
        Start-Sleep -Milliseconds 250
    }
} finally {
    if (-not $server.HasExited) { Send-Console 'stop' }
    if (-not $server.WaitForExit(30000)) { throw 'Owned server runner still live.' }
    ($serverOut.GetAwaiter().GetResult()+$serverErr.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-server.log"
    foreach ($item in $clients) {
        if (-not $item.Process.WaitForExit(10000)) { $item.Process.Kill(); $item.Process.WaitForExit(5000) | Out-Null }
        ($item.Out.GetAwaiter().GetResult()+$item.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath "$prefix-$($item.Name)-console.log"
        $results.Add(@{name=$item.Name;pid=$item.Process.Id;exitCode=$item.Process.ExitCode;profile=$item.Profile}); $item.Process.Dispose()
    }
    $serverExit=$server.ExitCode
    @{run=$Run;serverExitCode=$serverExit;clients=$results.ToArray();finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$prefix-result.json"
    $server.Dispose()
}
if ($serverExit -ne 0 -or @($results | Where-Object exitCode -ne 0).Count -gt 0 -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Graphical session did not close cleanly; preserve and inspect its logs.' }
