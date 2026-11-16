param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'boss1',[switch]$OnlyLoot,[switch]$OnlyBoundary)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)); $runPath = Join-Path $repoPath "run-boss-$Run"
if (Test-Path -LiteralPath (Join-Path $runPath 'world')) { throw 'Use a new isolated run; preserve previous worlds.' }
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = (Get-Process -Id $PID).Path
foreach ($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-boss-test-server.ps1'),'-Run',$Run)) { $info.ArgumentList.Add($arg) }
$info.WorkingDirectory=$repoPath; $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
$runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
$out=$runner.StandardOutput.ReadToEndAsync();$err=$runner.StandardError.ReadToEndAsync();$log=Join-Path $runPath 'logs/latest.log'
function Wait-Marker([string]$Pattern,[int]$Seconds) {
    $deadline=(Get-Date).AddSeconds($Seconds)
    while (-not $runner.HasExited -and (Get-Date) -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) {
            $text=Get-Content -LiteralPath $log -Raw
            if ($text -match 'failed at|Boss arenas paused|Unknown or incomplete command|Unknown test|No such test|Encountered an unexpected exception') { throw 'Boss fixture failed; inspect its preserved log.' }
            if ($text -match $Pattern) { return }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Missing native boss marker: $Pattern"
}
try {
    Wait-Marker 'Done \([0-9.,]+s\)!' 120
    foreach ($kind in $(if($OnlyLoot -or $OnlyBoundary) { @() } else { @('guardian','frost_lord') })) {
        $runner.StandardInput.WriteLine("execute in minecraft:overworld positioned 100 100 100 run test run bosses.ilum_$kind");$runner.StandardInput.Flush()
        Wait-Marker ("BOSS_GAME_TEST_PASS kind="+$kind.ToUpperInvariant()) 100
        Write-Output "PASS: actual dedicated $kind battle"
    }
    foreach ($scenario in $(if($OnlyBoundary) { @('boundary','loading') } elseif($OnlyLoot) { @('loot') } else { @('safety','cooldown','loot','boundary','loading') })) {
        $runner.StandardInput.WriteLine("execute in minecraft:overworld positioned 100 100 100 run test run bosses.$scenario");$runner.StandardInput.Flush()
        Wait-Marker ("BOSS_GAME_TEST_PASS kind="+$scenario.ToUpperInvariant()) 100
        Write-Output "PASS: actual dedicated $scenario scenario"
    }
} finally {
    if (-not $runner.HasExited) { $runner.StandardInput.WriteLine('stop');$runner.StandardInput.Flush() }
    if (-not $runner.WaitForExit(30000)) { throw "Owned boss server runner still live: $($runner.Id)" }
    ($out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/boss-$Run-server.log")
    $exit=$runner.ExitCode;$runner.Dispose()
}
if ($exit -ne 0 -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Boss dedicated did not stop and save normally.' }
