param([Parameter(Mandatory=$true)][string]$AndroidJar,[Parameter(Mandatory=$true)][string]$D8Jar,[Parameter(Mandatory=$true)][string]$JdkBin)
$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
New-Item -ItemType Directory -Force "$taskRoot/build/classes","$taskRoot/build/dex" | Out-Null
$taskSources=@(Get-ChildItem "$taskRoot/src","$taskRoot/stubs" -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
& "$JdkBin/javac.exe" --release 8 -encoding UTF-8 -cp $AndroidJar -d "$taskRoot/build/classes" $taskSources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
$taskProduction=Get-ChildItem "$taskRoot/src" -Recurse -Filter '*.java' | ForEach-Object {$_.BaseName}
$taskClasses=Get-ChildItem "$taskRoot/build/classes/com/mrdany" -Recurse -Filter '*.class' | Where-Object {
    $taskClassName=$_.BaseName.Split('$')[0]
    $taskProduction -contains $taskClassName
} | ForEach-Object {$_.FullName}
& "$JdkBin/java.exe" -cp $D8Jar com.android.tools.r8.D8 --min-api 24 --lib $AndroidJar --classpath "$taskRoot/build/classes" --output "$taskRoot/build/dex" $taskClasses
if($LASTEXITCODE -ne 0){throw 'DEX failed'}
& "$JdkBin/jar.exe" --create --file "$taskRoot/TorBoxLibraryTV.cs3" -C "$taskRoot/build/dex" classes.dex -C "$taskRoot" manifest.json
if($LASTEXITCODE -ne 0){throw 'Packaging failed'}
$taskManifest=Get-Content "$taskRoot/plugins.json" -Raw | ConvertFrom-Json
$taskManifest[0].fileSize=(Get-Item "$taskRoot/TorBoxLibraryTV.cs3").Length
ConvertTo-Json -InputObject @($taskManifest) -Depth 8 | Set-Content -Encoding utf8 "$taskRoot/plugins.json"
