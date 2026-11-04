param(
    [Parameter(Mandatory=$true)][string]$DesktopJar,
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$GameJava,
    [ValidateRange(50,300)][int]$UiScale=100,
    [ValidateSet('all','plus','god','both')][string]$Only='all',
    [switch]$SmallWindow
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$game=(Resolve-Path -LiteralPath $DesktopJar).Path
$java=(Resolve-Path -LiteralPath $GameJava).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($game)
try {
    $reader=[IO.StreamReader]::new($zip.GetEntry('version.properties').Open())
    try { $version=$reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($version -notmatch '(?m)^build=160\.7\s*$' -or $version -match '(?m)^modifier=steam\s*$') { throw 'Use a separate non-Steam copy of Build 160.7.' }
} finally { $zip.Dispose() }
$run=Join-Path $PSScriptRoot ('workspace-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $run 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $game -d $classes (Join-Path $PSScriptRoot 'WorkspaceProbe.java') (Join-Path $PSScriptRoot 'PrepareSettings.java')
if ($LASTEXITCODE -ne 0) { throw 'Edition observer compilation failed.' }
$oldApp=$env:APPDATA
$oldLocal=$env:LOCALAPPDATA
try {
    foreach ($scenario in @('plus','god','both')) {
        if($Only -ne 'all' -and $Only -ne $scenario){continue}
        $root=Join-Path $run $scenario
        $mods=Join-Path $root 'appdata/Mindustry/mods'
        New-Item -ItemType Directory -Force -Path $mods,(Join-Path $root 'local') | Out-Null
        foreach ($edition in @('plus','god')) {
            if ($scenario -eq 'both' -or $scenario -eq $edition) {
                Copy-Item -LiteralPath (Join-Path $repo ('mod/build/libs/scheme-size-'+$edition+'.jar')) -Destination $mods
            }
        }
        $env:APPDATA=Join-Path $root 'appdata'
        $env:LOCALAPPDATA=Join-Path $root 'local'
        $expected=if ($scenario -eq 'plus') { 'plus' } else { 'god' }
        $stdout=Join-Path $root 'stdout.log'
        $classpath=$game+';'+$classes
        & (Join-Path $JdkHome 'bin/java.exe') -cp $classpath probe.PrepareSettings (Join-Path $root 'appdata/Mindustry') $UiScale
        if($LASTEXITCODE -ne 0){throw 'Test profile settings preparation failed.'}
        $launch=@('-Xmx1G','-cp',('"'+$classpath+'"'),'probe.WorkspaceProbe',$expected)
        if($SmallWindow){$launch+='small'}
        $process=Start-Process -FilePath $java -ArgumentList $launch -WorkingDirectory $root -RedirectStandardOutput $stdout -RedirectStandardError (Join-Path $root 'stderr.log') -WindowStyle Hidden -PassThru
        try {
            $passed=$false
            $until=(Get-Date).AddSeconds(95)
            while ((Get-Date) -lt $until) {
                if (Test-Path -LiteralPath $stdout) {
                    if (Select-String -Path $stdout -SimpleMatch 'SCHEMETOOLS_WORKSPACE_FAIL' -Quiet) { throw "Edition $scenario failed; inspect $stdout" }
                    if (Select-String -Path $stdout -SimpleMatch 'SCHEMETOOLS_WORKSPACE_PASS' -Quiet) { $passed=$true; break }
                }
                if ($process.HasExited) { throw "Edition $scenario exited; inspect $stdout" }
                Start-Sleep -Seconds 2
            }
            if (!$passed) { throw "Edition $scenario timed out; inspect $stdout" }
            Write-Host "Edition $scenario passed. Log: $stdout"
        } finally { if (!$process.HasExited) { Stop-Process -Id $process.Id } }
    }
} finally { $env:APPDATA=$oldApp; $env:LOCALAPPDATA=$oldLocal }

