$ErrorActionPreference='Stop'
$base=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
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
& $maven -gs "$base/.mvn/settings.xml" -s "$base/.mvn/settings.xml" "-Dmaven.repo.local=$base/../.tools/m2" @args
exit $LASTEXITCODE
