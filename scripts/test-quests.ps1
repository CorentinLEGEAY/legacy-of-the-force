param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='quest1')
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));$runPath=Join-Path $repoPath "run-quests-$Run"
if(Test-Path -LiteralPath (Join-Path $runPath 'world')) { throw 'Use a fresh isolated run; preserve previous worlds.' }
New-Item -ItemType Directory -Path $runPath -Force | Out-Null
'eula=true' | Set-Content -LiteralPath (Join-Path $runPath 'eula.txt') -Encoding ascii
foreach($phase in @('JOURNEY','RELOAD')) {
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=(Get-Process -Id $PID).Path
    foreach($arg in @('-NoProfile','-File',(Join-Path $PSScriptRoot 'run-quest-test-server.ps1'),'-Run',$Run)) { $info.ArgumentList.Add($arg) }
    $info.WorkingDirectory=$repoPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $runner=[Diagnostics.Process]::new();$runner.StartInfo=$info;$started=Get-Date;$runner.Start() | Out-Null
    $out=$runner.StandardOutput.ReadToEndAsync();$err=$runner.StandardError.ReadToEndAsync();$log=Join-Path $runPath 'logs/latest.log'
    function Wait-Marker([string]$Pattern,[int]$Seconds) {
        $deadline=(Get-Date).AddSeconds($Seconds)
        while(-not $runner.HasExited -and (Get-Date) -lt $deadline) {
            if((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) {
                $text=Get-Content -LiteralPath $log -Raw
                if($text -match 'failed at|Unknown or incomplete command|Unknown test|No such test|Encountered an unexpected exception') { throw 'Quest fixture failed; inspect preserved log.' }
                if($text -match $Pattern) { return }
            }
            Start-Sleep -Milliseconds 250
        }
        throw "Missing native quest marker: $Pattern"
    }
    try {
        Wait-Marker 'Done \([0-9.,]+s\)!' 150
        $runner.StandardInput.WriteLine("execute in minecraft:overworld positioned 100 150 100 run test run quests.$($phase.ToLowerInvariant())");$runner.StandardInput.Flush()
        Wait-Marker "QUEST_NATIVE_PASS kind=$phase" 180
        Write-Output "PASS: native quest $phase"
    } finally {
        if(-not $runner.HasExited) { $runner.StandardInput.WriteLine('stop');$runner.StandardInput.Flush() }
        if(-not $runner.WaitForExit(30000)) { throw "Owned quest runner still active: $($runner.Id)" }
        ($out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/quests-$Run-$phase-server.log")
        $exit=$runner.ExitCode;$runner.Dispose()
    }
    if($exit -ne 0 -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Quest dedicated did not stop and save normally.' }
}
