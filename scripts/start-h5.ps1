param([string]$Jdk=$env:JAVA_HOME,[switch]$Persistent)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(!$Jdk -and (Test-Path 'D:\DevTools\jdk-25.0.4.1\bin\java.exe')){$Jdk='D:\DevTools\jdk-25.0.4.1'}
if(!$Jdk -or !(Test-Path "$Jdk/bin/java.exe")){throw '请设置 JAVA_HOME 为 JDK 25，或使用 -Jdk 指定路径。'}
$env:JAVA_HOME=$Jdk
$version=& "$Jdk/bin/java.exe" --version | Out-String
if($version -notmatch '(?m)^(openjdk|java) 25(?:[. +]|$)'){throw '必须使用 JDK 25。'}
if(!(Test-Path "$root/server/src/main/resources/static/index.html")){throw 'H5 产物缺失，请先在 web 目录运行 npm install 和 npm run build。'}
if($Persistent){$env:SPRING_PROFILES_ACTIVE='persistent'}else{$env:SPRING_PROFILES_ACTIVE='memory'}
Write-Host '服务启动成功后打开 http://localhost:8080；四窗口联机测试 http://localhost:8080/test.html'
Write-Host '首次启动需要下载 Maven 和 Java 依赖；Ctrl+C 停止服务。'
Push-Location "$root/server"
try {& .\mvnw.cmd spring-boot:run;exit $LASTEXITCODE}finally{Pop-Location}
