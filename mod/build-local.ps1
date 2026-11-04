param(
    [Parameter(Mandatory=$true)][string]$GameJar,
    [Parameter(Mandatory=$true)][string]$JdkHome
)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$game = (Resolve-Path -LiteralPath $GameJar).Path
$javac = Join-Path $JdkHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw 'JDK javac.exe was not found.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$gameZip = [IO.Compression.ZipFile]::OpenRead($game)
try {
    $versionEntry = $gameZip.GetEntry('version.properties')
    if ($null -eq $versionEntry) { throw 'Not a Mindustry desktop JAR: version.properties missing.' }
    $reader = [IO.StreamReader]::new($versionEntry.Open())
    try { $version = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($version -notmatch '(?m)^build=160\.7\s*$') { throw 'Expected Mindustry build 160.7.' }
} finally { $gameZip.Dispose() }
$classes = Join-Path $project ('build/manual-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes, (Join-Path $project 'build/libs') | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& $javac --release 17 -encoding UTF-8 -classpath $game -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed.' }
foreach ($edition in @('plus','god')) {
$output = Join-Path $project ('build/libs/scheme-size-' + $edition + '.jar')
$stream = [IO.File]::Open($output, [IO.FileMode]::Create, [IO.FileAccess]::Write)
try {
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($file in Get-ChildItem -LiteralPath $classes -Recurse -File -Filter '*.class') {
            $entry = [IO.Path]::GetRelativePath($classes, $file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry) | Out-Null
        }
        $resources = Join-Path $project 'src/main/resources'
        foreach ($file in Get-ChildItem -LiteralPath $resources -Recurse -File) {
            if ($file.FullName -eq (Join-Path $resources 'icon.png')) { continue }
            $entry = [IO.Path]::GetRelativePath($resources, $file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry) | Out-Null
        }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $project ('editions/' + $edition + '.hjson')), 'mod.hjson') | Out-Null
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $project ('editions/' + $edition + '/icon.png')), 'icon.png') | Out-Null
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }
Write-Host "Built $output (development build; not feature complete)."
}
