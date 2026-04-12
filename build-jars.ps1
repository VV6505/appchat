# Dong goi server.jar + client.jar — toolchain JDK 21 (khong dung JDK 22 lam bo bien dich).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Test-Java21Home {
    param([string]$Root)
    if ([string]::IsNullOrWhiteSpace($Root)) { return $false }
    if (-not (Test-Path "$Root\bin\javac.exe")) { return $false }
    $ver = & "$Root\bin\java.exe" -version 2>&1 | Out-String
    return $ver -match 'version "21\.'
}

function Find-Jdk21Dirs {
    $bases = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($b in $bases) {
        if (-not (Test-Path $b)) { continue }
        Get-ChildItem -Path $b -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^jdk-21' -or $_.Name -match '^jdk-21\.' } |
            ForEach-Object { $_.FullName }
    }
}

$jdk21 = $null
if ($env:JDK21_HOME -and (Test-Java21Home $env:JDK21_HOME)) {
    $jdk21 = $env:JDK21_HOME
}
elseif ($env:JAVA_HOME -and (Test-Java21Home $env:JAVA_HOME)) {
    $jdk21 = $env:JAVA_HOME
}
else {
    foreach ($d in (Find-Jdk21Dirs)) {
        if (Test-Java21Home $d) { $jdk21 = $d; break }
    }
}

if (-not $jdk21) {
    Write-Host "Khong tim thay JDK 21 (can java -version hien version 21)." -ForegroundColor Red
    Write-Host "Cai JDK 21, dat JDK21_HOME hoac JAVA_HOME tro toi thu muc goc JDK 21, roi chay lai: .\build-jars.ps1"
    exit 1
}

$env:JAVA_HOME = $jdk21
$env:PATH = "$jdk21\bin;$env:PATH"

Write-Host "JAVA_HOME=$jdk21"
& java -version 2>&1

if (-not (Get-Command ant -ErrorAction SilentlyContinue)) {
    Write-Host "Khong tim thay 'ant' trong PATH. NetBeans: Run Target -> jars; hoac cai Ant." -ForegroundColor Red
    exit 1
}

& ant jars
exit $LASTEXITCODE
