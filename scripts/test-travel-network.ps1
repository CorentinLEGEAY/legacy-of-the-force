param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'net1', [switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$worldPath = Join-Path $repoPath "run-travel-network-$Run/world"
if (-not $PrepareOnly -and (Test-Path -LiteralPath $worldPath)) { throw 'Use a new run identifier; preserve existing worlds and evidence.' }
$initPath = Join-Path $repoPath '.tools/travel-network-launch.init.gradle'
@'
import groovy.json.JsonOutput
gradle.projectsEvaluated {
    rootProject.tasks.register('writeTravelClientLaunch') {
        dependsOn 'compileGameTestJava', 'prepareClientTwoRun'
        doLast {
            def launch = rootProject.tasks.named('runClientTwo').get()
            // RunGameTask fills these providers inside exec(); JavaExec.classpath alone is still empty here.
            def providerArgs = launch.jvmArgumentProviders.collectMany { it.asArguments().toList() }
            def result = [arguments: launch.allJvmArgs + providerArgs + ['-cp', launch.classpathProvider.asPath, launch.mainClass.get()] + launch.args,
                          environment: launch.environmentProperty.get(), workingDirectory: launch.gameDirectory.get().asFile.absolutePath]
            rootProject.file('.tools/travel-network-launch.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(result))
        }
    }
}
'@ | Set-Content -LiteralPath $initPath -Encoding ascii
& (Join-Path $PSScriptRoot 'gradle.ps1') writeTravelClientLaunch --init-script $initPath '-PconnectTo=127.0.0.1:25587' --console=plain *> (Join-Path $repoPath ".tools/travel-network-$Run-prepare.log")
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the native client launch.' }
$launch = Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
if (-not $launch.arguments[$launch.arguments.IndexOf('-cp') + 1]) { throw 'Native launch classpath must not be empty.' }
$template = Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw
if ($PrepareOnly) { Write-Output 'PREPARED: native launch metadata and pinned client assets.'; return }
$started = Get-Date
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = (Get-Process -Id $PID).Path
$info.ArgumentList.Add('-NoProfile'); $info.ArgumentList.Add('-File'); $info.ArgumentList.Add((Join-Path $PSScriptRoot 'run-travel-network-server.ps1')); $info.ArgumentList.Add('-Run'); $info.ArgumentList.Add($Run)
$info.WorkingDirectory = $repoPath; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
$info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
$server = [Diagnostics.Process]::new(); $server.StartInfo = $info; $server.Start() | Out-Null
$serverOut = $server.StandardOutput.ReadToEndAsync(); $serverErr = $server.StandardError.ReadToEndAsync()
$logPath = Join-Path $repoPath "run-travel-network-$Run/logs/latest.log"
$clients = [Collections.Generic.List[object]]::new()
function Server-Text {
    if ((Test-Path -LiteralPath $logPath) -and (Get-Item -LiteralPath $logPath).LastWriteTime -ge $started) { return Get-Content -LiteralPath $logPath -Raw }
    return ''
}
$passed = $false
$observedAt = $null
$clientResults = [Collections.Generic.List[object]]::new()
try {
    $deadline = (Get-Date).AddSeconds(120)
    while (-not $server.HasExited -and (Get-Date) -lt $deadline -and (Server-Text) -notmatch 'Done \([0-9.,]+s\)!') { Start-Sleep -Milliseconds 250 }
    if ($server.HasExited -or (Server-Text) -notmatch 'Done \([0-9.,]+s\)!') { throw 'Isolated server did not start.' }
    foreach ($index in 1,2) {
        $name = if ($index -eq 1) { 'ForceTesterOne' } else { 'ForceTesterTwo' }
        $suffix = if ($index -eq 1) { 'one' } else { 'two' }
        $profile = Join-Path $repoPath "run-travel-client-$suffix-$Run"
        if (Test-Path -LiteralPath $profile) { throw "Fresh client profile required: $profile" }
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
'@ | Set-Content -LiteralPath (Join-Path $profile 'options.txt') -Encoding ascii
        $programFile = Join-Path $repoPath ".tools/travel-network-$Run-client$index-args.txt"
        $template.Replace('ForceTesterTwo', $name) | Set-Content -LiteralPath $programFile -Encoding utf8NoBOM
        $arguments = [Collections.Generic.List[string]]::new(); foreach ($arg in $launch.arguments) { $arguments.Add([string]$arg) }
        $mainIndex = $arguments.IndexOf('net.neoforged.devlaunch.Main'); if ($mainIndex -lt 0) { throw 'Unexpected native launcher main class.' }
        $arguments.Insert($mainIndex, '-Xms256M'); $arguments.Insert($mainIndex, '-Xmx1536M'); $arguments.Insert($mainIndex, '-Dlotf.validation.travelNetwork=true')
        $arguments[$arguments.Count - 1] = '@' + $programFile.Replace('\', '\\')
        $clientInfo = [Diagnostics.ProcessStartInfo]::new(); $clientInfo.FileName = Join-Path $repoPath ".tools/java-client-$suffix/bin/java.exe"
        if (-not (Test-Path -LiteralPath $clientInfo.FileName)) { throw "Missing existing isolated Java runtime: $($clientInfo.FileName)" }
        foreach ($arg in $arguments) { $clientInfo.ArgumentList.Add($arg) }
        $clientInfo.WorkingDirectory = $profile; $clientInfo.UseShellExecute = $false; $clientInfo.CreateNoWindow = $true; $clientInfo.RedirectStandardOutput = $true; $clientInfo.RedirectStandardError = $true
        foreach ($entry in $launch.environment.PSObject.Properties) { $clientInfo.Environment[$entry.Name] = [string]$entry.Value }
        $client = [Diagnostics.Process]::new(); $client.StartInfo = $clientInfo; $client.Start() | Out-Null
        $clients.Add(@{Process=$client;Out=$client.StandardOutput.ReadToEndAsync();Err=$client.StandardError.ReadToEndAsync();Profile=$profile;Name=$name})
        Write-Output "STARTED: $name PID=$($client.Id)"
    }
    $deadline = (Get-Date).AddSeconds(240)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        $text = Server-Text
        if ($text -match 'TRAVEL_NETWORK_SERVER_FAILED') { throw 'Server network assertion failed.' }
        foreach ($item in $clients) { if ($item.Process.HasExited) { throw "Native client exited early: $($item.Name), exit=$($item.Process.ExitCode)" } }
        $complete = @($clients | Where-Object { Test-Path -LiteralPath (Join-Path $_.Profile 'validation/travel-network-client.nbt') }).Count
        if ($complete -eq 2 -and $text -match 'TRAVEL_NETWORK_SERVER_PASS clients=2') {
            if ($null -eq $observedAt) { $observedAt = Get-Date; Write-Output 'OBSERVED: both native clients completed; checking fifteen more seconds connected.' }
            if (((Get-Date) - $observedAt).TotalSeconds -ge 15) { $passed = $true; break }
        }
        if (@($clients | Where-Object { (Test-Path -LiteralPath (Join-Path $_.Profile 'logs/latest.log')) -and (Get-Content -LiteralPath (Join-Path $_.Profile 'logs/latest.log') -Raw) -match 'TRAVEL_NETWORK_CLIENT_FAILED' }).Count -gt 0) { throw 'Client network assertion failed.' }
        Start-Sleep -Milliseconds 250
    }
    if (-not $passed) { throw 'Two native clients did not finish the network scenario.' }
    Write-Output 'OBSERVED: server pass and proof files from both live native clients. Verify NBT before declaring success.'
} finally {
    if (-not $server.HasExited) { $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush() }
    if (-not $server.WaitForExit(30000)) { throw "Server runner still alive PID=$($server.Id)" }
    ($serverOut.GetAwaiter().GetResult() + $serverErr.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-network-$Run-server.log")
    $serverExit = $server.ExitCode; $server.Dispose()
    foreach ($item in $clients) {
        if (-not $item.Process.WaitForExit(20000)) { $item.Process.Kill(); $item.Process.WaitForExit(5000) | Out-Null; Write-Output "CLEANUP: stopped owned native test client PID=$($item.Process.Id)" }
        ($item.Out.GetAwaiter().GetResult() + $item.Err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-network-$Run-$($item.Name)-console.log")
        $clientResults.Add(@{name=$item.Name;pid=$item.Process.Id;exitCode=$item.Process.ExitCode;profile=$item.Profile})
        Write-Output "CLOSED: $($item.Name) exit=$($item.Process.ExitCode)"; $item.Process.Dispose()
    }
}
@{run=$Run;observedProofs=$passed;serverExitCode=$serverExit;clients=$clientResults.ToArray();finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-network-$Run-result.json")
if ($serverExit -ne 0 -or (Server-Text) -notmatch 'All dimensions are saved' -or @($clientResults | Where-Object exitCode -ne 0).Count -gt 0) { throw 'Server or native clients did not close cleanly.' }

