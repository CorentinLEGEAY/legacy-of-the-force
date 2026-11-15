param(
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'solo1',
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$TemplateRun = 'net3',
    [string]$PythonPath = 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$profile = Join-Path $repoPath "run-travel-client-solo-$Run"
if (Test-Path -LiteralPath $profile) { throw 'Use a new solo profile and preserve existing worlds.' }
$templateWorld = Join-Path $repoPath "run-travel-network-$TemplateRun/world"
$check = @'
import sys,pathlib
sys.path.insert(0,str(pathlib.Path(sys.argv[1])/'scripts'))
from read_nbt import load
tag=load(pathlib.Path(sys.argv[2])/'level.dat')
assert 'Player' not in tag['Data'],'Template must contain no embedded single-player data'
'@
& $PythonPath -c $check $repoPath $templateWorld
if ($LASTEXITCODE -ne 0) { throw 'Dedicated world metadata template is unsuitable.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
$launch = Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template = Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw
$marker = $template.IndexOf('# User Supplied Program Arguments'); if ($marker -lt 0) { throw 'Pinned program argument template changed.' }
$world = Join-Path $profile 'saves/TravelValidation'; New-Item -ItemType Directory -Path $world -Force | Out-Null
# Only generation/settings metadata: no player, receipt, fixture block or previous proof is copied.
Copy-Item -LiteralPath (Join-Path $templateWorld 'level.dat') -Destination (Join-Path $world 'level.dat')
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
$programFile = Join-Path $repoPath ".tools/travel-solo-$Run-args.txt"
($template.Substring(0, $marker) + "# User Supplied Program Arguments`n--username`nForceTesterSolo`n--quickPlaySingleplayer`nTravelValidation`n") | Set-Content -LiteralPath $programFile -Encoding utf8NoBOM
$arguments = [Collections.Generic.List[string]]::new(); foreach ($arg in $launch.arguments) { $arguments.Add([string]$arg) }
$mainIndex = $arguments.IndexOf('net.neoforged.devlaunch.Main'); if ($mainIndex -lt 0) { throw 'Unexpected native client entry point.' }
foreach ($arg in @('-Xms256M', '-Xmx2048M', '-Dlotf.validation.travelNetwork=true', '-Dlotf.validation.travelSolo=true')) { $arguments.Insert($mainIndex, $arg) }
$arguments[$arguments.Count - 1] = '@' + $programFile.Replace('\', '\\')
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = Join-Path $repoPath '.tools/java-client-two/bin/java.exe'
foreach ($arg in $arguments) { $info.ArgumentList.Add($arg) }
foreach ($entry in $launch.environment.PSObject.Properties) { $info.Environment[$entry.Name] = [string]$entry.Value }
$info.WorkingDirectory = $profile; $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
$client = [Diagnostics.Process]::new(); $client.StartInfo = $info; $client.Start() | Out-Null
$out = $client.StandardOutput.ReadToEndAsync(); $err = $client.StandardError.ReadToEndAsync(); Write-Output "STARTED: native solo PID=$($client.Id)"
$forced = $false
try {
    $deadline = (Get-Date).AddSeconds(270)
    while (-not $client.HasExited -and (Get-Date) -lt $deadline) { $client.WaitForExit(1000) | Out-Null }
    if (-not $client.HasExited) { $forced = $true; $client.Kill(); $client.WaitForExit(5000) | Out-Null; throw 'Solo timed out; stopped the owned test process.' }
} finally {
    ($out.GetAwaiter().GetResult() + $err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-solo-$Run-console.log")
    $exitCode = $client.ExitCode
    @{run=$Run;pid=$client.Id;exitCode=$exitCode;forced=$forced;finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-solo-$Run-result.json")
    $client.Dispose()
}
if ($exitCode -ne 0 -or $forced -or -not (Test-Path -LiteralPath (Join-Path $world 'validation/travel-network-server.nbt')) -or
        -not (Test-Path -LiteralPath (Join-Path $profile 'validation/travel-network-client.nbt'))) { throw 'Native solo did not produce both proofs and close normally.' }
Write-Output 'OBSERVED: native solo and integrated server proofs. Verify their contents before declaring success.'

