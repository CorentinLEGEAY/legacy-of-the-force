param([ValidatePattern('^[a-z0-9_-]{1,12}$')][string]$Run='jarfluid1')
$ErrorActionPreference='Stop'
$repoPath=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$serverPath=Join-Path $repoPath '.tools/neoforge-prod-21.1.265'
$jar=Join-Path $repoPath 'build/libs/legacyoftheforce-0.1.0-dev.jar'
$argsFile=Join-Path $serverPath 'libraries/net/neoforged/neoforge/21.1.265/win_args.txt'
if(-not (Test-Path -LiteralPath $argsFile) -or -not (Test-Path -LiteralPath $jar)) { throw 'Build the JAR and install the pinned standalone NeoForge server first.' }
$worldName="jar-smoke-$Run";$world=Join-Path $serverPath $worldName
if(Test-Path -LiteralPath $world) { throw 'Fresh packaged-server world required; preserve prior results.' }
$mods=Join-Path $serverPath 'mods';New-Item -ItemType Directory -Path $mods -Force | Out-Null
if(@(Get-ChildItem -LiteralPath $mods -Filter '*.jar' | Where-Object Name -ne 'legacyoftheforce-0.1.0-dev.jar').Count) { throw 'Isolated smoke must not load additional mods.' }
Copy-Item -LiteralPath $jar -Destination (Join-Path $mods 'legacyoftheforce-0.1.0-dev.jar') -Force
$jarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
'eula=true' | Set-Content -LiteralPath (Join-Path $serverPath 'eula.txt') -Encoding ascii
@"
server-ip=127.0.0.1
server-port=25589
online-mode=false
enable-rcon=false
spawn-protection=0
level-name=$worldName
level-seed=20261115
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
difficulty=peaceful
view-distance=3
simulation-distance=3
"@ | Set-Content -LiteralPath (Join-Path $serverPath 'server.properties') -Encoding ascii
$results=[Collections.Generic.List[object]]::new()
foreach($phase in 1,2) {
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=Join-Path $repoPath '.tools/java/jdk-21.0.13+9/bin/java.exe'
    foreach($arg in @('-Xms256M','-Xmx1536M','-Dmixin.debug.verbose=true',"@$argsFile",'nogui')) { $info.ArgumentList.Add($arg) }
    $info.WorkingDirectory=$serverPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $native=[Diagnostics.Process]::new();$native.StartInfo=$info;$started=Get-Date;$native.Start() | Out-Null;$null=$native.Handle
    $out=$native.StandardOutput.ReadToEndAsync();$err=$native.StandardError.ReadToEndAsync();$log=Join-Path $serverPath 'logs/latest.log';$forced=$false
    function Wait-ProductionMarker([string]$Pattern,[int]$Seconds=120) {
        $deadline=(Get-Date).AddSeconds($Seconds)
        while(-not $native.HasExited -and (Get-Date) -lt $deadline) {
            $text=''
            try {
                if([IO.File]::GetLastWriteTime($log) -ge $started) {
                    $stream=[IO.File]::Open($log,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
                    $reader=[IO.StreamReader]::new($stream)
                    try { $text=$reader.ReadToEnd() } finally { $reader.Dispose() }
                }
            } catch [IO.FileNotFoundException] {
                # Log4j can rotate latest.log between checking its date and opening it.
            } catch [IO.DirectoryNotFoundException] {
                # First boot may not have created the log directory yet.
            }
            if($text) {
                if($text -match 'MixinApplyError|InvalidInjectionException|InjectionError|Failed to start the minecraft server') { throw 'Packaged mod startup failed; preserve logs.' }
                if($text -match $Pattern) { return }
            }
            Start-Sleep -Milliseconds 250
        }
        throw "Packaged server missing marker: $Pattern"
    }
    try {
        Wait-ProductionMarker 'Done \([0-9.,]+s\)!'
        if($phase -eq 1) {
            foreach($command in @('fill 0 99 0 1 99 0 minecraft:stone','setblock -1 100 0 minecraft:stone','setblock 0 100 -1 minecraft:stone','setblock 0 100 1 minecraft:stone','setblock 0 100 0 minecraft:water')) { $native.StandardInput.WriteLine($command) }
            $native.StandardInput.Flush()
        }
        $native.WaitForExit(1500) | Out-Null
        $native.StandardInput.WriteLine("execute if block 1 100 0 minecraft:water run say LOTF_JAR_FLOW_PASS_$phase")
        $native.StandardInput.WriteLine('execute in legacyoftheforce:ilum run time query daytime')
        $native.StandardInput.WriteLine('lotf rpg recover');$native.StandardInput.Flush()
        Wait-ProductionMarker "LOTF_JAR_FLOW_PASS_$phase" 30
    } finally {
        if(-not $native.HasExited) { $native.StandardInput.WriteLine('stop');$native.StandardInput.Flush() }
        if(-not $native.WaitForExit(30000)) { $forced=$true;$native.Kill();$native.WaitForExit(5000) | Out-Null }
        $console=$out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()
        $console | Set-Content -LiteralPath (Join-Path $repoPath ".tools/packaged-$Run-phase$phase-console.log")
        if(Test-Path -LiteralPath $log) { Copy-Item -LiteralPath $log -Destination (Join-Path $repoPath ".tools/packaged-$Run-phase$phase.log") }
        $results.Add(@{phase=$phase;pid=$native.Id;exitCode=$native.ExitCode;forced=$forced});$exitCode=$native.ExitCode;$native.Dispose()
        @{run=$Run;mixinVerbose=$true;jarSha256=$jarHash;serverRoot=$serverPath;world=$world;phases=$results.ToArray()} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $repoPath ".tools/packaged-$Run-result.json")
    }
    if($exitCode -ne 0 -or $forced -or (Get-Content -LiteralPath $log -Raw) -notmatch 'All dimensions are saved') { throw 'Packaged server failed to stop normally.' }
    Write-Output "PASS: packaged NeoForge phase$phase, native exit0 and saved dimensions; inspect logs/artifact before validation."
}
