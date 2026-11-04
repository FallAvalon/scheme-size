param([string]$DesktopJar,[string]$ServerJar,[string]$JdkHome,[string]$GameJava,[ValidateSet('plus','god')][string]$Edition='god',[bool]$Strict=$false)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$run=Join-Path $PSScriptRoot ('workspace-remote-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $run 'classes';$hostRoot=Join-Path $run 'server';$clientRoot=Join-Path $run 'client'
New-Item -ItemType Directory -Force -Path $classes,$hostRoot,(Join-Path $clientRoot 'appdata/Mindustry/mods'),(Join-Path $clientRoot 'local') | Out-Null
& "$JdkHome/bin/javac.exe" --release 17 -encoding UTF-8 -cp $ServerJar -d $classes "$PSScriptRoot/RemoteTeleportServer.java"
if($LASTEXITCODE -ne 0){throw 'server observer compilation'}
& "$JdkHome/bin/javac.exe" --release 17 -encoding UTF-8 -cp $DesktopJar -d $classes "$PSScriptRoot/RemoteTeleportClient.java"
if($LASTEXITCODE -ne 0){throw 'client observer compilation'}
Copy-Item "$repo/mod/build/libs/scheme-size-$Edition.jar" "$clientRoot/appdata/Mindustry/mods/"
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$hostProcess=$null;$clientProcess=$null
function WaitMarker($path,$marker,$process){$until=(Get-Date).AddSeconds(50);while((Get-Date) -lt $until){if(Test-Path $path){if(Select-String -Path $path -Pattern 'SCHEMETOOLS_REMOTE_FAIL' -Quiet){throw "Failure: $path"};if(Select-String -Path $path -SimpleMatch -Pattern $marker -Quiet){return}};if($process.HasExited){throw "Process exited: $path"};Start-Sleep -Seconds 1};throw "Timeout: $path"}
try{
 $mode=$Strict.ToString().ToLower()
 $hostProcess=Start-Process "$JdkHome/bin/java.exe" -ArgumentList @('-cp',('"'+$ServerJar+';'+$classes+'"'),'probe.RemoteTeleportServer',$mode) -WorkingDirectory $hostRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$hostRoot/stdout.log" -RedirectStandardError "$hostRoot/stderr.log"
 WaitMarker "$hostRoot/stdout.log" 'SCHEMETOOLS_REMOTE_READY' $hostProcess
 $env:APPDATA="$clientRoot/appdata";$env:LOCALAPPDATA="$clientRoot/local"
 $clientProcess=Start-Process $GameJava -ArgumentList @('-cp',('"'+$DesktopJar+';'+$classes+'"'),'probe.RemoteTeleportClient',$mode) -WorkingDirectory $clientRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$clientRoot/stdout.log" -RedirectStandardError "$clientRoot/stderr.log"
 WaitMarker "$clientRoot/stdout.log" 'SCHEMETOOLS_REMOTE_CLIENT_PASS' $clientProcess
 WaitMarker "$hostRoot/stdout.log" 'SCHEMETOOLS_REMOTE_SERVER_PASS' $hostProcess
 Write-Host "Remote teleport $Edition strict=$Strict PASS: $run"
}finally{foreach($p in @($clientProcess,$hostProcess)){if($p -and !$p.HasExited){Stop-Process -Id $p.Id}};$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal}