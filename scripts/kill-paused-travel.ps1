param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9_-]{1,32}$')][string]$Run,
    [Parameter(Mandatory)][ValidateSet('PAYMENT_MEMORY', 'TELEPORTED', 'ACTIVE_DURABLE')][string]$Checkpoint,
    [string]$PythonPath = 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$worldPath = Join-Path $repoPath 'run-travel-crash/world'
$logPath = Join-Path $repoPath 'run-travel-crash/logs/latest.log'
$prefix = Join-Path $repoPath ('.tools/travel-crash-' + $Run)
$marker = "VALIDATION_TRAVEL_PAUSED run=$Run checkpoint=$Checkpoint "
$tail = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
if (-not $tail.Contains($marker) -or $tail.Contains('VALIDATION_TRAVEL_PAUSE_EXPIRED') -or
    ([DateTime]::UtcNow - (Get-Item -LiteralPath $logPath).LastWriteTimeUtc).TotalSeconds -gt 12) { throw 'No recent matching travel pause. Nothing stopped.' }
if (Test-Path -LiteralPath ($prefix + '-meta.json')) { throw 'Existing kill evidence must not be overwritten.' }
$sdkPath = [IO.Path]::GetFullPath((Join-Path $repoPath '.tools/java')) + [IO.Path]::DirectorySeparatorChar
$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath).StartsWith($sdkPath, [StringComparison]::OrdinalIgnoreCase) -and
    $_.CommandLine.Contains('net.neoforged.devlaunch.Main') -and $_.CommandLine.Contains('serverRunProgramArgs.txt') -and $_.CommandLine.Contains('serverRunVmArgs.txt')
})
if ($candidates.Count -ne 1) { throw 'Expected exactly one isolated development Java server. Nothing stopped.' }
$target = $candidates[0]
if ((Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/serverRunVmArgs.txt')) -notcontains '-Dlotf.validation.travelCrash=true') { throw 'Opt-in launch arguments missing.' }
$proof = @'
import json,pathlib,sys,time,uuid
sys.path.insert(0,str(pathlib.Path(sys.argv[1])/'scripts'))
from read_nbt import load
repo,world,prefix,run,checkpoint,pid=sys.argv[1:]
world=pathlib.Path(world);uid=lambda v:str(uuid.UUID(''.join(f'{n&0xffffffff:08x}' for n in v)))
fixture=world/'validation/travel-crash'/f'{run}.nbt';paused=world/'validation/travel-crash'/f'{run}-paused.nbt'
f,q=load(fixture),load(paused);owner=uid(f['owner'])
assert f['schemaVersion']==1 and f['run']==q['run']==run and f['checkpoint']==q['checkpoint']==checkpoint
assert f['preparationPid']==q['pausePid']==int(pid) and q['mode']=='KILL'
assert 0<=time.time()*1000-q['pauseEpochMillis']<20000,'Checkpoint is stale'
journal=world/'data/legacyoftheforce/travel'/f'{owner}.nbt';player=world/'playerdata'/f'{owner}.dat'
j,p=load(journal),load(player);assert j==q['journal'] and p==q['nativePlayer'] and uid(j['owner'])==owner
assert j['phase']=={'PAYMENT_MEMORY':'PREPARED','TELEPORTED':'PAID','ACTIVE_DURABLE':'ARRIVED'}[checkpoint]
expected=j['before' if checkpoint=='PAYMENT_MEMORY' else 'after'];atlas=dict(expected['atlas'])
if checkpoint=='ACTIVE_DURABLE':atlas.pop('pending')
assert p['Inventory']==expected['Inventory'] and p['neoforge:attachments']['legacyoftheforce:travel_state']==atlas
for path,suffix in [(fixture,'-fixture.nbt'),(paused,'-paused.nbt'),(journal,'-journal.nbt'),(player,'-player.dat')]:pathlib.Path(prefix+suffix).write_bytes(path.read_bytes())
meta=dict(run=run,checkpoint=checkpoint,kind=f['kind'],owner=owner,phase=j['phase'],durableDimension=p['Dimension'],liveDimension=q['livePoint']['dimension'])
pathlib.Path(prefix+'-meta.json').write_text(json.dumps(meta,indent=2),encoding='utf-8');print(json.dumps(meta))
'@
& $PythonPath -c $proof $repoPath $worldPath $prefix $Run $Checkpoint $target.ProcessId
if ($LASTEXITCODE -ne 0) { throw 'Native checkpoint proof failed. Nothing stopped.' }
$fresh = Get-CimInstance Win32_Process -Filter "ProcessId = $($target.ProcessId)"
$last = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
if (-not $fresh -or $fresh.CreationDate -ne $target.CreationDate -or $fresh.CommandLine -ne $target.CommandLine -or
    -not $last.Contains($marker) -or $last.Contains('VALIDATION_TRAVEL_PAUSE_EXPIRED')) { throw 'Identity or pause changed. Nothing stopped.' }
Copy-Item -LiteralPath $logPath -Destination ($prefix + '-server.log')
$nativeProcess = Get-Process -Id $target.ProcessId -ErrorAction Stop; $nativeProcess.Handle | Out-Null
@{processId=$target.ProcessId;run=$Run;checkpoint=$Checkpoint;creationDate=$target.CreationDate} | ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination-request.json')
$nativeProcess.Kill()
if (-not $nativeProcess.WaitForExit(5000) -or -not $nativeProcess.HasExited) { throw 'Target has not exited.' }
@{processId=$target.ProcessId;exitCode=$nativeProcess.ExitCode;run=$Run;checkpoint=$Checkpoint;shutdownCommand=$false;terminatedAtUtc=[DateTime]::UtcNow.ToString('O')} |
    ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination.json')
Write-Output "KILLED isolated travel Java PID=$($target.ProcessId) run=$Run checkpoint=$Checkpoint; no stop/save command."
