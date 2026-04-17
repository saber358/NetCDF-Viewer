$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Get-PomProject([string]$rootPath) {
    $pomPath = Join-Path $rootPath "pom.xml"
    if (-not (Test-Path $pomPath)) {
        throw "pom.xml not found: $pomPath"
    }
    [xml]$pom = Get-Content -LiteralPath $pomPath
    return $pom.project
}

function Get-ProjectVersion([string]$rootPath) {
    $version = (Get-PomProject $rootPath).version
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "Project version is missing in pom.xml"
    }
    return $version.Trim()
}

function Get-ProjectArtifactId([string]$rootPath) {
    $artifactId = (Get-PomProject $rootPath).artifactId
    if ([string]::IsNullOrWhiteSpace($artifactId)) {
        throw "Project artifactId is missing in pom.xml"
    }
    return $artifactId.Trim()
}

function Find-WixDirectory([string]$startPath) {
    $current = (Resolve-Path -LiteralPath $startPath).Path
    while (-not [string]::IsNullOrWhiteSpace($current)) {
        $candidate = Join-Path $current "tools\wix314"
        $light = Join-Path $candidate "light.exe"
        $candle = Join-Path $candidate "candle.exe"
        if ((Test-Path $light) -and (Test-Path $candle)) {
            return $candidate
        }

        $parent = Split-Path -Parent $current
        if ([string]::IsNullOrWhiteSpace($parent) -or $parent -eq $current) {
            break
        }
        $current = $parent
    }

    return $null
}

$appVersion = Get-ProjectVersion $projectRoot
$artifactId = Get-ProjectArtifactId $projectRoot
$jarName = "$artifactId-$appVersion.jar"
$packageInput = Join-Path $projectRoot "target\package-input"
$installerDir = Join-Path $projectRoot "target\installer"
$runtimeLibDir = Join-Path $projectRoot "target\app\lib"
$jarPath = Join-Path $projectRoot ("target\" + $jarName)
$wixPath = Find-WixDirectory $projectRoot
$javafxModuleDir = Join-Path $projectRoot "target\javafx-modules"
$iconPath = Join-Path $projectRoot "src\main\resources\icons\app-icon.ico"

Write-Host "Building project artifacts..."
mvn package

if (-not (Test-Path $jarPath)) {
    throw "Application jar not found: $jarPath"
}

if (Test-Path $packageInput) {
    Remove-Item -LiteralPath $packageInput -Recurse -Force
}
if (-not (Test-Path $installerDir)) {
    New-Item -ItemType Directory -Path $installerDir | Out-Null
}
New-Item -ItemType Directory -Path $packageInput | Out-Null

Copy-Item -LiteralPath $jarPath -Destination (Join-Path $packageInput $jarName)
Get-ChildItem -LiteralPath $runtimeLibDir -Filter *.jar | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $packageInput $_.Name)
}

if (Test-Path $javafxModuleDir) {
    Remove-Item -LiteralPath $javafxModuleDir -Recurse -Force
}
New-Item -ItemType Directory -Path $javafxModuleDir | Out-Null
Get-ChildItem -LiteralPath $packageInput -Filter "javafx-*-win.jar" | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $javafxModuleDir $_.Name)
}

if (Test-Path $wixPath) {
    $env:PATH = "$wixPath;$env:PATH"
    Write-Host "Using WiX binaries from $wixPath"
}

Write-Host "Running jpackage..."
jpackage `
    --type exe `
    --name NetCDFViewer `
    --dest $installerDir `
    --input $packageInput `
    --main-jar $jarName `
    --main-class com.example.netcdfviewer.Launcher `
    --module-path $javafxModuleDir `
    --add-modules javafx.controls,javafx.swing,java.logging,java.naming,java.security.jgss,java.compiler,jdk.jfr,jdk.unsupported,jdk.unsupported.desktop `
    --vendor lwj `
    --app-version $appVersion `
    --icon $iconPath `
    --win-shortcut `
    --win-menu

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

Write-Host "Installer created under $installerDir"
