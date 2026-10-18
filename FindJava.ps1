# The newest JDK of major version $Major in $Folder. Its version is in the "release" file every JDK has, e.g.
# JAVA_VERSION="17.0.17" (or "1.8.0_202" for Java 8). A JRE - no include\jni.h, which JAVA_INCLUDE is for - is
# skipped. At the same version, the installed folder comes before a junction to it.
function Find-Jdk([string] $Folder, [int] $Major) {
    $jdks = foreach ($jdk in Get-ChildItem -Path $Folder -Directory -ErrorAction SilentlyContinue) {
        $release = Join-Path $jdk.FullName 'release'
        if (-not (Test-Path (Join-Path $jdk.FullName 'include\jni.h')) -or -not (Test-Path $release)) { continue }
        $line = Select-String -Path $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
        if (-not $line) { continue }
        $parts = @(($line.Matches[0].Groups[1].Value -replace '_', '.' -replace '[^0-9.].*$', '') -split '\.' | Where-Object { $_ })
        if ($parts.Count -gt 1 -and $parts[0] -eq '1') { $parts = $parts[1..($parts.Count - 1)] }     # 1.8 is Java 8
        if (-not $parts -or [int] $parts[0] -ne $Major) { continue }
        while ($parts.Count -lt 2) { $parts += '0' }
        [pscustomobject] @{
            Path     = $jdk.FullName
            Version  = [version] ($parts[0..([Math]::Min(3, $parts.Count - 1))] -join '.')
            Junction = [bool] $jdk.LinkType
        }
    }
    $jdks | Sort-Object -Property @{ Expression = 'Version'; Descending = $true }, Junction | Select-Object -First 1
}

$jdk = Find-Jdk $JavaRoot $JdkVersion
$javaHome = if ($jdk) { $jdk.Path } else { $null }
if ($jdk) {
    Write-Host "JDK $JdkVersion`: $javaHome (Java $($jdk.Version))"
} else {
    Write-Warning "There is no JDK $JdkVersion in $JavaRoot, so JAVA_HOME and JAVA_INCLUDE are left as they are"
}
