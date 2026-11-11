param([int]$TimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$smokeDirectory = Join-Path $repoRoot 'run-smoke'
New-Item -ItemType Directory -Force $smokeDirectory | Out-Null
$propertiesPath = Join-Path $smokeDirectory 'server.properties'
if (-not (Test-Path -LiteralPath $propertiesPath)) {
    "server-ip=127.0.0.1`nserver-port=25575`nlevel-seed=20261111`nview-distance=4`nsimulation-distance=4" | Set-Content -LiteralPath $propertiesPath
}
if (-not ((Get-Content -LiteralPath $propertiesPath) -contains 'server-ip=127.0.0.1')) {
    throw 'Smoke server must bind to loopback. Review run-smoke/server.properties.'
}
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = '/d /c gradlew.bat runSmokeServer --console=plain'
$startInfo.WorkingDirectory = $repoRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$startedAt = Get-Date
$process.Start() | Out-Null
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
$logPath = Join-Path $smokeDirectory 'logs/latest.log'
$ready = $false
try {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
        if ((Test-Path -LiteralPath $logPath) -and (Get-Item -LiteralPath $logPath).LastWriteTime -ge $startedAt) {
            $logText = Get-Content -LiteralPath $logPath -Raw
            if ($logText -match 'Done \([0-9.,]+s\)!') { $ready = $true; break }
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $process.HasExited) {
        if ($ready) { $process.StandardInput.WriteLine('lotf force recover') }
        $process.StandardInput.WriteLine('stop')
        $process.StandardInput.Flush()
        if (-not $process.WaitForExit(30000)) {
            throw "Server did not stop after 30 seconds. Inspect process $($process.Id) and $logPath."
        }
    }
    $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
    $output | Set-Content -LiteralPath (Join-Path $smokeDirectory 'smoke-console.log')
    $logText = Get-Content -LiteralPath $logPath -Raw
    if (-not $ready -or $process.ExitCode -ne 0 -or
        $output -notmatch 'Block recovery completed|force\.legacyoftheforce\.recovered|Récupération des blocs effectuée' -or
        $logText -notmatch 'infrastructure loaded' -or
        $logText -notmatch 'All dimensions are saved' -or
        $logText -match '\[(?:[^\]]*/)?(?:ERROR|FATAL)\]') {
        throw 'Smoke failed: inspect run-smoke/smoke-console.log and logs/latest.log.'
    }
    Write-Output 'PASS: mod loaded, dedicated server ready, clean stop, all dimensions saved.'
} finally {
    $process.Dispose()
}
