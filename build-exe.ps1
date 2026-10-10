# Builds dist\MessagePigeon.exe (the launcher) and dist\messagepigeon.jar (the application).
# Copy the whole dist folder wherever you like; the exe and the jar must stay together.
#   .\build-exe.ps1               build the jar with Maven, then the exe
#   .\build-exe.ps1 -SkipMaven    reuse target\messagepigeon.jar
# Needs JDK 21+ (JAVA_HOME pointing at it, or pass -JavaHome) and Maven on the PATH (or pass -MavenBin).
param(
    [switch]$SkipMaven,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenBin = ''
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $SkipMaven) {
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
        $env:PATH = "$JavaHome\bin;$env:PATH"
    }
    if ($MavenBin) { $env:PATH = "$MavenBin;$env:PATH" }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw 'Maven (mvn) was not found. Install it and add it to the PATH, or pass -MavenBin <folder containing mvn.cmd>.'
    }
    mvn -q -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed (the build needs JDK 21 or later; check JAVA_HOME or pass -JavaHome)' }
}

New-Item -ItemType Directory -Force dist | Out-Null
# csc.exe ships with Windows (.NET Framework 4), so no SDK is needed to build the launcher.
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $csc /nologo /target:winexe /optimize+ /out:dist\MessagePigeon.exe /win32icon:launcher\app.ico /reference:System.Windows.Forms.dll launcher\Launcher.cs
if ($LASTEXITCODE -ne 0) { throw 'Launcher build failed' }
Copy-Item target\messagepigeon.jar dist\messagepigeon.jar -Force
Write-Host "Built $PSScriptRoot\dist\MessagePigeon.exe"
