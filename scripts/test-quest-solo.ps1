param(
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run = 'qs1',
    [ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$TemplateRun = 'q2',
    [switch]$Recover,
    [string]$PythonPath = 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$profile = Join-Path $repoPath "run-quest-client-solo-$Run"
if (Test-Path -LiteralPath $profile) { throw 'Use a new solo profile and preserve existing worlds.' }
$templateWorld = if ($Recover) { Join-Path $repoPath "run-quest-client-solo-$TemplateRun/saves/QuestValidation" } else { Join-Path $repoPath "run-quests-$TemplateRun/world" }
# Refuse a live template: native session.lock must be exclusively accessible before any copy.
$templateLock = Join-Path $templateWorld 'session.lock'
if (Test-Path -LiteralPath $templateLock) {
    $lockProbe = [IO.File]::Open($templateLock, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $lockProbe.Dispose()
}
if ($Recover -and -not (Test-Path -LiteralPath (Join-Path $templateWorld 'validation/quest-solo-server.nbt'))) { throw 'Recovery requires a previously completed stopped solo.' }
$check = @'
import sys,pathlib
sys.path.insert(0,str(pathlib.Path(sys.argv[1])/'scripts'))
from read_nbt import load
tag=load(pathlib.Path(sys.argv[2])/'level.dat')
assert ('Player' in tag['Data']) == (sys.argv[3]=='True'),'Template player mode mismatch'
'@
& $PythonPath -c $check $repoPath $templateWorld ([string][bool]$Recover)
if ($LASTEXITCODE -ne 0) { throw 'Dedicated world metadata template is unsuitable.' }
& (Join-Path $PSScriptRoot 'test-travel-network.ps1') -Run $Run -PrepareOnly
$launch = Get-Content -LiteralPath (Join-Path $repoPath '.tools/travel-network-launch.json') -Raw | ConvertFrom-Json
$template = Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/clientTwoRunProgramArgs.txt') -Raw
$marker = $template.IndexOf('# User Supplied Program Arguments'); if ($marker -lt 0) { throw 'Pinned program argument template changed.' }
$world = Join-Path $profile 'saves/QuestValidation'; New-Item -ItemType Directory -Path $world -Force | Out-Null
# Only generation/settings metadata: no player, receipt, fixture block or previous proof is copied.
if ($Recover) {
    # Preserve the source. Reproduce native owner loading from a complete stopped solo copy.
    Get-ChildItem -LiteralPath $templateWorld -Force | Copy-Item -Destination $world -Recurse
    if (Test-Path -LiteralPath (Join-Path $world 'validation/quest-solo-recovered.nbt')) { throw 'Owner save probe must not overwrite an existing proof.' }

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
lang:fr_fr
guiScale:2
tutorialStep:none
'@ | Set-Content -LiteralPath (Join-Path $profile 'options.txt') -Encoding ascii
$programFile = Join-Path $repoPath ".tools/quest-solo-$Run-args.txt"
($template.Substring(0, $marker) + "# User Supplied Program Arguments`n--username`nQuestSolo`n--quickPlaySingleplayer`nQuestValidation`n") | Set-Content -LiteralPath $programFile -Encoding utf8NoBOM
$arguments = [Collections.Generic.List[string]]::new(); foreach ($arg in $launch.arguments) { $arguments.Add([string]$arg) }
$mainIndex = $arguments.IndexOf('net.neoforged.devlaunch.Main'); if ($mainIndex -lt 0) { throw 'Unexpected native client entry point.' }
foreach ($arg in @('-Xms256M', '-Xmx2048M', '-Dlotf.validation.questSolo=true')) { $arguments.Insert($mainIndex, $arg) }
if ($Recover) { $arguments.Insert($mainIndex, '-Dlotf.validation.questSoloRecover=true') }
$arguments[$arguments.Count - 1] = '@' + $programFile.Replace('\', '\\')
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = Join-Path $repoPath '.tools/java-client-two/bin/java.exe'
foreach ($arg in $arguments) { $info.ArgumentList.Add($arg) }
foreach ($entry in $launch.environment.PSObject.Properties) { $info.Environment[$entry.Name] = [string]$entry.Value }
$info.WorkingDirectory = $profile; $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
$client = [Diagnostics.Process]::new(); $client.StartInfo = $info; $client.Start() | Out-Null; $null = $client.Handle
$out = $client.StandardOutput.ReadToEndAsync(); $err = $client.StandardError.ReadToEndAsync(); Write-Output "STARTED: native solo PID=$($client.Id)"
$forced = $false
try {
    $deadline = (Get-Date).AddSeconds(900)
    while (-not $client.HasExited -and (Get-Date) -lt $deadline) {
        $client.WaitForExit(1000) | Out-Null
    }
    if (-not $client.HasExited) { $forced = $true; $client.Kill(); $client.WaitForExit(5000) | Out-Null; throw 'Solo timed out; stopped the owned test process.' }
} finally {
    ($out.GetAwaiter().GetResult() + $err.GetAwaiter().GetResult()) | Set-Content -LiteralPath (Join-Path $repoPath ".tools/quest-solo-$Run-console.log")
    $exitCode = $client.ExitCode
    @{run=$Run;templateRun=$TemplateRun;recover=[bool]$Recover;profile=$profile;pid=$client.Id;exitCode=$exitCode;forced=$forced;finishedUtc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $repoPath ".tools/quest-solo-$Run-result.json")
    $client.Dispose()
}
$serverProof = if ($Recover) { 'validation/quest-solo-recovered.nbt' } else { 'validation/quest-solo-server.nbt' }
$clientProof = if ($Recover) { 'validation/quest-solo-client.nbt' } else { 'validation/quest-solo-client.nbt' }
if ($exitCode -ne 0 -or $forced -or -not (Test-Path -LiteralPath (Join-Path $world $serverProof)) -or
        -not (Test-Path -LiteralPath (Join-Path $profile $clientProof))) { throw 'Native quest solo did not produce both proofs and close normally.' }
Write-Output 'OBSERVED: native quest solo and integrated server proofs. Verify contents before declaring success.'
