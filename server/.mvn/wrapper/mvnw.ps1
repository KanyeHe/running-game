$ErrorActionPreference='Stop'
$base=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
# Windows PowerShell 将 Java 的 stderr 当作错误流，暂时允许捕获版本输出。
$oldPreference=$ErrorActionPreference
$ErrorActionPreference='Continue'
$javaInfo=(& $java -XshowSettings:properties -version 2>&1 | Out-String)
$ErrorActionPreference=$oldPreference
if($javaInfo -notmatch 'java.specification.version = 25\s') {
  throw 'JDK 25 is required. Set JAVA_HOME to JDK 25.'
}
$version='3.9.11'
$maven=Join-Path $PSScriptRoot "apache-maven-$version/bin/mvn.cmd"
if(!(Test-Path $maven)) {
  $url="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
  $zip=Join-Path $PSScriptRoot 'maven.zip'
  Invoke-WebRequest -UseBasicParsing $url -OutFile $zip
  $expected=((Invoke-WebRequest -UseBasicParsing "$url.sha512").Content.Trim() -split '\s+')[0]
  if((Get-FileHash $zip -Algorithm SHA512).Hash -ne $expected){throw 'Maven distribution checksum mismatch'}
  Expand-Archive -LiteralPath $zip -DestinationPath $PSScriptRoot -Force
}
& $maven -f "$base/pom.xml" -gs "$base/.mvn/settings.xml" -s "$base/.mvn/settings.xml" "-Dmaven.repo.local=$base/../.tools/m2" @args
exit $LASTEXITCODE
