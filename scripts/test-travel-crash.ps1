param(
    [ValidateSet('Sweep', 'Kill', 'Recover', 'Safety')][string]$Mode = 'Sweep',
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'sweep1',
    [ValidateSet('OUTBOUND', 'RETURN')][string]$Kind = 'OUTBOUND',
    [ValidateSet('PAYMENT_MEMORY', 'TELEPORTED', 'ACTIVE_DURABLE')][string]$Checkpoint = 'PAYMENT_MEMORY',
    [ValidateSet('CLAIM', 'LAVA', 'FALLBACK')][string]$Hazard = 'CLAIM'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$started = Get-Date
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = (Get-Process -Id $PID).Path
$info.ArgumentList.Add('-NoProfile'); $info.ArgumentList.Add('-File'); $info.ArgumentList.Add((Join-Path $PSScriptRoot 'run-travel-crash-server.ps1'))
$info.WorkingDirectory = $repoPath; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
$info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
$process = [Diagnostics.Process]::new(); $process.StartInfo = $info; $process.Start() | Out-Null
$stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
$log = Join-Path $repoPath 'run-travel-crash/logs/latest.log'
function Wait-Marker([string]$Pattern, [int]$Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) {
            $text = Get-Content -LiteralPath $log -Raw
            if ($text -match 'TRAVEL_CRASH_FAILED|VALIDATION_TRAVEL_PAUSE_EXPIRED') { throw 'Native travel assertion failed or pause expired.' }
            if ($text -match $Pattern) { return }
        }
        Start-Sleep -Milliseconds 150
    }
    throw "Missing server marker: $Pattern"
}
function Command([string]$Text, [string]$Marker) { $process.StandardInput.WriteLine($Text); $process.StandardInput.Flush(); Wait-Marker $Marker }
$killed = $false
try {
    Wait-Marker 'Done \([0-9.,]+s\)!' 120
    if ($Mode -eq 'Recover') { Command "lotftravelcrash recover $Run verify" "TRAVEL_CRASH_RECOVER_PASS run=$Run"; Write-Output "PASS: recovered $Run" }
    else {
        [string[]]$checkpoints = if ($Mode -eq 'Sweep') { @('PREPARED','PAYMENT_MEMORY','PAYMENT_DURABLE','PAID','TELEPORTED','ARRIVAL_DURABLE','ARRIVED','ACTIVATION_MEMORY','ACTIVE_DURABLE','DONE') } elseif ($Mode -eq 'Safety') { @('ARRIVED', 'ACTIVE_DURABLE') } else { @($Checkpoint) }
        [string[]]$kinds = if ($Mode -eq 'Sweep') { @('OUTBOUND', 'RETURN') } else { @($Kind) }
        foreach ($direction in $kinds) { for ($i = 0; $i -lt $checkpoints.Count; $i++) {
            $point = $checkpoints[$i]; $case = if ($Mode -eq 'Sweep') { "$Run-$($direction.ToLowerInvariant())-$i" } elseif ($Mode -eq 'Safety') { "$Run-$i" } else { $Run }
            Command "lotftravelcrash init $case $direction $point" "TRAVEL_CRASH_INIT_PASS run=$case"
            Start-Sleep -Seconds 3
            if ($direction -eq 'RETURN') { Command "lotftravelcrash depart $case now" "TRAVEL_CRASH_DEPART_PASS run=$case"; Start-Sleep -Seconds 3 }
            if ($Mode -eq 'Safety') { Command "lotftravelcrash hazard $case $Hazard" "TRAVEL_CRASH_HAZARD_PASS run=$case"; Write-Output "PASS: $direction/$point/$Hazard" }
            elseif ($Mode -eq 'Kill') {
                Command "lotftravelcrash interrupt $case KILL" "VALIDATION_TRAVEL_PAUSED run=$case checkpoint=$point "
                & (Join-Path $PSScriptRoot 'kill-paused-travel.ps1') -Run $case -Checkpoint $point
                $killed = $true; Write-Output "PASS: Java terminated at $direction/$point"
            } else { Command "lotftravelcrash interrupt $case ERROR" "TRAVEL_CRASH_INTERRUPT_PASS run=$case"; Write-Output "PASS: $direction/$point" }
        } }
    }
} finally {
    if (-not $process.HasExited -and -not $killed) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    if (-not $process.WaitForExit(30000)) { throw "Inspect still-running runner PID=$($process.Id)" }
    $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
    $output | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-crash-$Run-$($Mode.ToLowerInvariant())-console.log")
    $exitCode = $process.ExitCode; $process.Dispose()
}
if ($Mode -eq 'Kill') { if (-not $killed -or $exitCode -eq 0) { throw 'Expected an actual killed Java failure.' } }
elseif ($exitCode -ne 0 -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Server did not close cleanly.' }
