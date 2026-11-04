param([string]$DesktopJar,[string]$JdkHome,[string]$GameJava)
$ErrorActionPreference='Stop'
$run=Join-Path $PSScriptRoot ('workspace-import-'+[guid]::NewGuid().ToString('N'));$classes=Join-Path $run 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$JdkHome/bin/javac.exe" --release 17 -encoding UTF-8 -cp $DesktopJar -d $classes "$PSScriptRoot/GithubImportProbe.java"
if($LASTEXITCODE -ne 0){throw 'import observer compile'}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA
try{foreach($edition in @('plus','god')){
 $root=Join-Path $run $edition;New-Item -ItemType Directory -Force -Path "$root/appdata","$root/local" | Out-Null
 $env:APPDATA="$root/appdata";$env:LOCALAPPDATA="$root/local"
 $process=Start-Process $GameJava -ArgumentList @('-cp',('"'+$DesktopJar+';'+$classes+'"'),'probe.GithubImportProbe',"FallAvalon/scheme-size-$edition","scheme-size-$edition") -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/stdout.log" -RedirectStandardError "$root/stderr.log"
 try{$pass=$false;$until=(Get-Date).AddSeconds(60);while((Get-Date) -lt $until){if(Test-Path "$root/stdout.log"){if(Select-String -Path "$root/stdout.log" -Pattern 'SCHEMETOOLS_IMPORT_FAIL|ClassNotFoundException' -Quiet){throw "Import failure: $root"};if(Select-String -Path "$root/stdout.log" -Pattern 'SCHEMETOOLS_IMPORT_PASS' -Quiet){$pass=$true;break}};if($process.HasExited){throw 'Import process exit'};Start-Sleep -Seconds 1};if(!$pass){throw "Import timeout $root"};Write-Host "GitHub import $edition PASS: $root"}finally{if(!$process.HasExited){Stop-Process -Id $process.Id}}
}}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal}