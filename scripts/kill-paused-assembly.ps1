param(
    [Parameter(Mandatory)][ValidateSet('PAYMENT_DURABLE', 'ENTITY_DURABLE', 'ACTIVATED')][string]$Checkpoint,
    [Parameter(Mandatory)][long]$ExpectedRevision,
    [Guid]$Owner = '9713e147-c047-3ed9-aaac-6df18d3deefa',
    [string]$PythonPath
)
$ErrorActionPreference = 'Stop'
if ($ExpectedRevision -lt 1) { throw 'ExpectedRevision must be positive.' }
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$sdkRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot '.tools/java')) + [IO.Path]::DirectorySeparatorChar
$logPath = Join-Path $repoRoot 'run/logs/latest.log'
$marker = "VALIDATION_ASSEMBLY_PAUSED checkpoint=$Checkpoint owner=$Owner "
$tail = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
$ageSeconds = ([DateTime]::UtcNow - (Get-Item -LiteralPath $logPath).LastWriteTimeUtc).TotalSeconds
if (-not $tail.Contains($marker) -or $tail.Contains('VALIDATION_ASSEMBLY_PAUSE_EXPIRED') -or $ageSeconds -gt 12) {
    throw 'No recent matching validation pause. No process was stopped.'
}
$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath).StartsWith($sdkRoot, [StringComparison]::OrdinalIgnoreCase) -and
        $_.CommandLine.Contains('net.neoforged.devlaunch.Main') -and $_.CommandLine.Contains('serverRunProgramArgs.txt')
})
if ($candidates.Count -ne 1) { throw 'Expected exactly one development dedicated Java server. No process was stopped.' }
$target = $candidates[0]
$prefix = Join-Path $repoRoot ('.tools/crash-' + $Checkpoint.ToLowerInvariant() + '-r' + $ExpectedRevision)
if (Test-Path -LiteralPath ($prefix + '-meta.json')) { throw 'Evidence already exists; no process was stopped.' }
$python = if ($PythonPath) { $PythonPath } else { 'C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe' }
if (-not (Test-Path -LiteralPath $python)) {
    $pythonRoots = @(Get-ChildItem -LiteralPath 'C:/Program Files/WindowsApps' -Force -Directory -Filter 'OpenAI.CodexPrimaryRuntime*' | Sort-Object LastWriteTime -Descending)
    $python = $pythonRoots | ForEach-Object { Join-Path $_.FullName 'dependencies/python/python.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $python) { throw 'Bundled Python missing. No process was stopped.' }
Copy-Item -LiteralPath (Join-Path $repoRoot "run/world/data/legacyoftheforce/speeder_assembly/$Owner.nbt") -Destination ($prefix + '-journal.nbt')
Copy-Item -LiteralPath (Join-Path $repoRoot "run/world/playerdata/$Owner.dat") -Destination ($prefix + '-player.dat')
& $python (Join-Path $repoRoot 'scripts/read_nbt.py') ($prefix + '-journal.nbt') > ($prefix + '-journal.json')
if ($LASTEXITCODE -ne 0) { throw 'Cannot read journal evidence. No process was stopped.' }
& $python (Join-Path $repoRoot 'scripts/read_nbt.py') ($prefix + '-player.dat') > ($prefix + '-player.json')
if ($LASTEXITCODE -ne 0) { throw 'Cannot read player evidence. No process was stopped.' }
$proof = @'
import json,pathlib,sys,uuid
prefix,checkpoint,revision=sys.argv[1],sys.argv[2],int(sys.argv[3])
read=lambda suffix:json.loads(pathlib.Path(prefix+suffix).read_text(encoding='utf-8-sig'))
j,p=read('-journal.json'),read('-player.json')
assert j['phase']=={'PAYMENT_DURABLE':'PREPARED','ENTITY_DURABLE':'PAID','ACTIVATED':'PUBLISHED'}[checkpoint], 'Wrong durable phase'
receipt=p['neoforge:attachments']['legacyoftheforce:speeder_assembly_receipt']
assert receipt['revision']==revision and receipt==j['after']['receipt'], 'Wrong native receipt'
assert p['Inventory']==j['after']['Inventory'], 'Native inventory is not the paid image'
uid=lambda values:str(uuid.UUID(''.join(f'{v & 0xffffffff:08x}' for v in values)))
meta=dict(checkpoint=checkpoint,phase=j['phase'],revision=revision,owner=uid(j['owner']),operation=uid(j['operation']),vehicle=uid(j['vehicle']['vehicleId']),nativeInventoryPaid=True)
pathlib.Path(prefix+'-meta.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
print(json.dumps(meta))
'@
& $python -c $proof $prefix $Checkpoint $ExpectedRevision
if ($LASTEXITCODE -ne 0) { throw 'Durable checkpoint proof differs. No process was stopped.' }
$fresh = Get-CimInstance Win32_Process -Filter "ProcessId = $($target.ProcessId)"
if (-not $fresh -or $fresh.CreationDate -ne $target.CreationDate -or $fresh.CommandLine -ne $target.CommandLine) {
    throw 'Server identity changed. No process was stopped.'
}
$last = (Get-Content -LiteralPath $logPath -Tail 12) -join "`n"
if ($last.Contains('VALIDATION_ASSEMBLY_PAUSE_EXPIRED')) { throw 'Pause expired. No process was stopped.' }
Copy-Item -LiteralPath $logPath -Destination ($prefix + '-server.log')
$nativeProcess = Get-Process -Id $target.ProcessId -ErrorAction Stop
$nativeProcess.Handle | Out-Null
@{ processId = $target.ProcessId; checkpoint = $Checkpoint; revision = $ExpectedRevision; creationDate = $target.CreationDate } |
    ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination-request.json')
$nativeProcess.Kill()
if (-not $nativeProcess.WaitForExit(5000) -or -not $nativeProcess.HasExited) { throw 'Target server did not exit within five seconds.' }
@{ processId = $target.ProcessId; exitCode = $nativeProcess.ExitCode; checkpoint = $Checkpoint; revision = $ExpectedRevision; terminatedAtUtc = [DateTime]::UtcNow.ToString('O'); shutdownCommand = $false } |
    ConvertTo-Json | Set-Content -LiteralPath ($prefix + '-termination.json')
Write-Output "KILLED verified development server PID=$($target.ProcessId) checkpoint=$Checkpoint revision=$ExpectedRevision; no stop command or shutdown save."
