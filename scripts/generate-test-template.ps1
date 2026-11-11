# Original empty GameTest structure; no imported game assets.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$target = Join-Path $repoRoot 'src/gameTest/resources/data/legacyoftheforce/structure/empty.nbt'
New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
$memory = [IO.MemoryStream]::new()
function Write-Int([int]$Value) {
    $bytes = [BitConverter]::GetBytes($Value)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($bytes) }
    $memory.Write($bytes, 0, $bytes.Length)
}
function Write-String([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $memory.WriteByte([byte]($bytes.Length -shr 8))
    $memory.WriteByte([byte]($bytes.Length -band 255))
    $memory.Write($bytes, 0, $bytes.Length)
}
function Write-Header([byte]$Type, [string]$Name) {
    $memory.WriteByte($Type)
    Write-String $Name
}
Write-Header 10 ''
Write-Header 9 'size'
$memory.WriteByte(3)
Write-Int 3
1..3 | ForEach-Object { Write-Int 5 }
Write-Header 9 'palette'
$memory.WriteByte(10)
Write-Int 1
Write-Header 8 'Name'
Write-String 'minecraft:air'
$memory.WriteByte(0)
foreach ($list in @('blocks', 'entities')) {
    Write-Header 9 $list
    $memory.WriteByte(10)
    Write-Int 0
}
Write-Header 3 'DataVersion'
Write-Int 3955
$memory.WriteByte(0)
$file = [IO.File]::Create($target)
$gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionMode]::Compress)
try { $bytes = $memory.ToArray(); $gzip.Write($bytes, 0, $bytes.Length) }
finally { $gzip.Dispose(); $file.Dispose(); $memory.Dispose() }
