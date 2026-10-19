param([string]$Jdk=$env:JAVA_HOME,[int]$Seeds=30)
$ErrorActionPreference='Stop'
if (!$Jdk -or !(Test-Path "$Jdk/bin/javac.exe")) { throw 'Set JAVA_HOME to JDK 25 or pass -Jdk <path>' }
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root 'server/target/core-classes'
New-Item -ItemType Directory -Force $out | Out-Null
$files=@(Get-ChildItem "$root/server/src/main/java/cn/luzhou/rules","$root/server/src/main/java/cn/luzhou/game" -Filter *.java -Recurse | ForEach-Object FullName)
$files+=Join-Path $root 'server/src/test/java/cn/luzhou/CoreChecks.java'
& "$Jdk/bin/javac.exe" --release 25 -encoding UTF-8 -d $out $files
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$Jdk/bin/java.exe" -Xmx768m -cp $out cn.luzhou.CoreChecks $Seeds
exit $LASTEXITCODE
