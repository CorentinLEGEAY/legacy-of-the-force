param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9_-]{1,32}$')][string]$Run,
    [Parameter(Mandatory)][ValidateSet('PAYMENT_MEMORY', 'ENERGY_DURABLE', 'ACTIVATED')][string]$Checkpoint,
    [string]$PythonPath = 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$repoPath = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$worldPath = Join-Path $repoPath 'run-recharge-crash/world'
$logPath = Join-Path $repoPath 'run-recharge-crash/logs/latest.log'
$prefix = Join-Path $repoPath ('.tools/recharge-crash-' + $Run)
$marker = "VALIDATION_RECHARGE_PAUSED run=$Run checkpoint=$Checkpoint "
$tail = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
if (-not $tail.Contains($marker) -or $tail.Contains('VALIDATION_RECHARGE_PAUSE_EXPIRED') -or
        ([DateTime]::UtcNow - (Get-Item -LiteralPath $logPath).LastWriteTimeUtc).TotalSeconds -gt 12) { throw 'No recent matching recharge pause. Nothing stopped.' }
if (Test-Path -LiteralPath ($prefix + '-meta.json')) { throw 'Evidence already exists. Nothing stopped.' }
$sdkPath = [IO.Path]::GetFullPath((Join-Path $repoPath '.tools/java')) + [IO.Path]::DirectorySeparatorChar
$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath).StartsWith($sdkPath, [StringComparison]::OrdinalIgnoreCase) -and
        $_.CommandLine.Contains('net.neoforged.devlaunch.Main') -and $_.CommandLine.Contains('serverRunProgramArgs.txt') -and
        $_.CommandLine.Contains('serverRunVmArgs.txt')
})
if ($candidates.Count -ne 1) { throw 'Expected exactly one opted-in development Java server. Nothing stopped.' }
$target = $candidates[0]
if (-not ((Get-Content -LiteralPath (Join-Path $repoPath 'build/moddev/serverRunVmArgs.txt')) -contains '-Dlotf.validation.rechargeCrash=true')) { throw 'Opt-in launch arguments missing. Nothing stopped.' }
$proof = @'
import json,pathlib,sys,uuid
sys.path.insert(0,str(pathlib.Path(sys.argv[1])/'scripts'))
from read_nbt import load
repo,world,prefix,run,checkpoint,pid=map(str,sys.argv[1:])
world=pathlib.Path(world)
fixture=world/'validation/recharge-crash'/f'{run}.nbt'
f=load(fixture)
uid=lambda values:str(uuid.UUID(''.join(f'{v & 0xffffffff:08x}' for v in values)))
owner=uid(f['owner'])
assert f['schemaVersion']==1 and f['run']==run and f['checkpoint']==checkpoint
assert f['preparationPid']==int(pid), 'Fixture was prepared by another Java process'
journal=world/'data/legacyoftheforce/speeder_recharge'/f'{owner}.nbt'
player=world/'playerdata'/f'{owner}.dat'
j,p=load(journal),load(player)
unpaid=checkpoint=='PAYMENT_MEMORY'
assert j['phase']=={'PAYMENT_MEMORY':'PREPARED','ENERGY_DURABLE':'PAID','ACTIVATED':'APPLIED'}[checkpoint]
assert uid(j['owner'])==owner and uid(j['vehicle']['vehicleId'])==uid(f['vehicle'])
expected=j['before' if unpaid else 'after']
assert p['Inventory']==expected['Inventory']
assert p['neoforge:attachments']['legacyoftheforce:speeder_recharge_receipt']==expected['receipt']
assert expected['receipt']['revision']==(0 if unpaid else 1)
for path,suffix in [(fixture,'-fixture.nbt'),(journal,'-journal.nbt'),(player,'-player.dat')]:
 pathlib.Path(prefix+suffix).write_bytes(path.read_bytes())
meta=dict(run=run,checkpoint=checkpoint,owner=owner,vehicle=uid(f['vehicle']),phase=j['phase'],durablePaid=not unpaid)
pathlib.Path(prefix+'-meta.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
print(json.dumps(meta))
'@
& $PythonPath -c $proof $repoPath $worldPath $prefix $Run $Checkpoint $target.ProcessId
if ($LASTEXITCODE -ne 0) { throw 'Native checkpoint proof failed. Nothing stopped.' }
$fresh = Get-CimInstance Win32_Process -Filter "ProcessId = $($target.ProcessId)"
$last = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
if (-not $fresh -or $fresh.CreationDate -ne $target.CreationDate -or $fresh.CommandLine -ne $target.CommandLine -or
        -not $last.Contains($marker) -or $last.Contains('VALIDATION_RECHARGE_PAUSE_EXPIRED')) { throw 'Server identity or pause changed. Nothing stopped.' }
Copy-Item -LiteralPath $logPath -Destination ($prefix + '-server.log')
$nativeProcess = Get-Process -Id $target.ProcessId -ErrorAction Stop
$nativeProcess.Handle | Out-Null
@{ processId=$target.ProcessId; run=$Run; checkpoint=$Checkpoint; creationDate=$target.CreationDate } | ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination-request.json')
$nativeProcess.Kill()
if (-not $nativeProcess.WaitForExit(5000) -or -not $nativeProcess.HasExited) { throw 'Target did not exit in five seconds.' }
@{ processId=$target.ProcessId; exitCode=$nativeProcess.ExitCode; run=$Run; checkpoint=$Checkpoint; terminatedAtUtc=[DateTime]::UtcNow.ToString('O'); shutdownCommand=$false } |
    ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination.json')
Write-Output "KILLED isolated recharge validation server PID=$($target.ProcessId) run=$Run checkpoint=$Checkpoint; no stop/save command."
