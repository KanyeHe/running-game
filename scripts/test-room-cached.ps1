param([string]$Jdk=$env:JAVA_HOME,[string]$Repository='E:\GMove\mavenRepository')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$dependencies=@(
  'com/fasterxml/jackson/core/jackson-databind/2.21.4',
  'com/fasterxml/jackson/core/jackson-core/2.21.4',
  'com/fasterxml/jackson/core/jackson-annotations/2.21',
  'org/springframework/spring-context/6.2.12'
)
$jars=@(foreach($dependency in $dependencies){Get-ChildItem (Join-Path $Repository $dependency) -Filter *.jar | ForEach-Object FullName})
$out=Join-Path $root 'server/target/core-classes'
$classpath=($jars -join ';')+';'+$out
$sources=@('room/Room.java','room/RoomService.java','store/StateStore.java','store/MemoryStore.java') | ForEach-Object {Join-Path $root "server/src/main/java/cn/luzhou/$_"}
$sources+=Join-Path $root 'server/src/test/java/cn/luzhou/RoomChecks.java'
& "$Jdk/bin/javac.exe" --release 25 -encoding UTF-8 -cp $classpath -d $out $sources
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$Jdk/bin/java.exe" -cp $classpath cn.luzhou.RoomChecks
exit $LASTEXITCODE
