param(
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'solo1',
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$TemplateRun = 'net3',
    [switch]$OwnerSaveProbe,
    [ValidateSet('Load', 'Kill', 'Recover')][string]$OwnerSaveMode = 'Load',
    [string]$PythonPath = 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$profile = Join-Path $repoPath "run-travel-client-solo-$Run"
if (Test-Path -LiteralPath $profile) { throw 'Use a new solo profile and preserve existing worlds.' }
$templateWorld = if ($OwnerSaveProbe) { Join-Path $repoPath "run-travel-client-solo-$TemplateRun/saves/TravelValidation" } else { Join-Path $repoPath "run-travel-network-$TemplateRun/world" }
$check = @'
import sys,pathlib
sys.path.insert(0,str(pathlib.Path(sys.argv[1])/'scripts'))
from read_nbt import load
tag=load(pathlib.Path(sys.argv[2])/'level.dat')
assert ('Player' in tag['Data']) == (sys.argv[3]=='True'),'Template player mode mismatch'
'@
& $PythonPath -c $check $repoPath $templateWorld ([string][bool]$OwnerSaveProbe)
if ($LASTEXITCODE -ne 0) { throw 'Dedicated world metadata template is unsuitable.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
$launch = Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template = Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw
$marker = $template.IndexOf('# User Supplied Program Arguments'); if ($marker -lt 0) { throw 'Pinned program argument template changed.' }
$world = Join-Path $profile 'saves/TravelValidation'; New-Item -ItemType Directory -Path $world -Force | Out-Null
# Only generation/settings metadata: no player, receipt, fixture block or previous proof is copied.
if ($OwnerSaveProbe) {
    # Preserve the source. Reproduce native owner loading from a complete stopped solo copy.
    Get-ChildItem -LiteralPath $templateWorld -Force | Copy-Item -Destination $world -Recurse
    if (Test-Path -LiteralPath (Join-Path $world 'validation/solo-owner-save.nbt')) { throw 'Owner save probe must not overwrite an existing proof.' }
} else {
    Copy-Item -LiteralPath (Join-Path $templateWorld 'level.dat') -Destination (Join-Path $world 'level.dat')
}
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
$modeFlag = if ($OwnerSaveProbe) { '-Dlotf.validation.soloOwnerSave=true' } else { '-Dlotf.validation.travelNetwork=true' }
foreach ($arg in @('-Xms256M', '-Xmx2048M', $modeFlag, '-Dlotf.validation.travelSolo=true')) { $arguments.Insert($mainIndex, $arg) }
if ($OwnerSaveProbe) { $arguments.Insert($mainIndex, "-Dlotf.validation.soloOwnerSave.mode=$OwnerSaveMode") }
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
    while (-not $client.HasExited -and (Get-Date) -lt $deadline) {
        if ($OwnerSaveProbe -and $OwnerSaveMode -eq 'Kill' -and (Test-Path -LiteralPath (Join-Path $world 'validation/solo-owner-checkpoint.nbt'))) {
            $forced = $true; $client.Kill(); $client.WaitForExit(5000) | Out-Null; break
        }
        $client.WaitForExit(1000) | Out-Null
    }
    if (-not $client.HasExited) { $forced = $true; $client.Kill(); $client.WaitForExit(5000) | Out-Null; throw 'Solo timed out; stopped the owned test process.' }
} finally {
    ($out.GetAwaiter().GetResult() + $err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-solo-$Run-console.log")
    $exitCode = $client.ExitCode
    @{run=$Run;pid=$client.Id;exitCode=$exitCode;forced=$forced;finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $repoPath ".tools/travel-solo-$Run-result.json")
    $client.Dispose()
}
if ($OwnerSaveProbe) {
    if ($OwnerSaveMode -eq 'Kill') {
        if (-not $forced -or $exitCode -eq 0) { throw 'Expected actual abrupt termination of the owned solo test JVM.' }
        Write-Output 'OBSERVED: stopped owned solo JVM after durable barrier without shutdown command; verify and recover a copy.'
        return
    }
    if ($exitCode -ne 0 -or $forced -or -not (Test-Path -LiteralPath (Join-Path $world 'validation/solo-owner-save.nbt'))) { throw 'Native owner-save probe did not finish.' }
    Write-Output 'OBSERVED: native owner-save probe; read its passed field and snapshots before declaring success.'
    return
}
if ($exitCode -ne 0 -or $forced -or -not (Test-Path -LiteralPath (Join-Path $world 'validation/travel-network-server.nbt')) -or
        -not (Test-Path -LiteralPath (Join-Path $profile 'validation/travel-network-client.nbt'))) { throw 'Native solo did not produce both proofs and close normally.' }
Write-Output 'OBSERVED: native solo and integrated server proofs. Verify their contents before declaring success.'

