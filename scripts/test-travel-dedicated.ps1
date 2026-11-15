param([ValidateSet('outbound', 'return', 'home')][string]$Stage = 'outbound')
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runPath = Join-Path $repoPath 'run-smoke'
$properties = Get-Content -LiteralPath (Join-Path $runPath 'server.properties')
if ($properties -notcontains 'server-ip=127.0.0.1') { throw 'Validation server must bind only to loopback.' }
$jdkPath = Get-ChildItem -LiteralPath (Join-Path $repoPath '.tools/java') -Directory | Select-Object -First 1
if ($jdkPath) { $env:JAVA_HOME = $jdkPath.FullName; $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = 'cmd.exe'
$info.Arguments = '/d /c gradlew.bat runSmokeServer --console=plain'
$info.WorkingDirectory = $repoPath
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = [Diagnostics.Process]::new(); $process.StartInfo = $info
$started = Get-Date
$process.Start() | Out-Null
$stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
$log = Join-Path $runPath 'logs/latest.log'
function Wait-TravelMarker([string]$Pattern, [int]$Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
        if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).LastWriteTime -ge $started) {
            $text = Get-Content -LiteralPath $log -Raw
            if ($text -match 'TRAVEL_VALIDATION_FAILED') { throw 'Travel assertion failed. Inspect the server log.' }
            if ($text -match $Pattern) { return }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Missing server marker: $Pattern"
}
$passed = $false
try {
    Wait-TravelMarker 'Done \([0-9.,]+s\)!' 120
    $commands = switch ($Stage) { 'outbound' { 'start', 'outbound' } 'return' { 'away', 'return' } 'home' { 'home' } }
    foreach ($command in $commands) {
        # The production cooldown is 40 ticks; this is an actual idle period, not a state edit.
        Start-Sleep -Seconds 3
        $process.StandardInput.WriteLine("lotftravel$command"); $process.StandardInput.Flush()
        Wait-TravelMarker ("TRAVEL_" + $command.ToUpperInvariant() + '_PASS')
        Write-Output "PASS: $command"
    }
    $passed = $true
} finally {
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
        if (-not $process.WaitForExit(30000)) { throw "Server did not stop; inspect process $($process.Id)." }
    }
    $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
    $output | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-native-$Stage-server.log")
    $exitCode = $process.ExitCode; $process.Dispose()
}
if (-not $passed -or $exitCode -ne 0 -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Validation or clean save failed.' }
Write-Output "PASS: dedicated $Stage stage, clean shutdown and all dimensions saved."
