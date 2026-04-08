$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$appVersion = "1.0.2"
$jarName = "netcdf-viewer-$appVersion.jar"
$packageInput = Join-Path $projectRoot "target\package-linux-input"
$installerDir = Join-Path $projectRoot "target\installer-linux"
$runtimeLibDir = Join-Path $projectRoot "target\app\lib"
$jarPath = Join-Path $projectRoot ("target\" + $jarName)
$javafxModuleDir = Join-Path $projectRoot "target\javafx-modules-linux"
$iconPath = Join-Path $projectRoot "src\main\resources\icons\app-icon.png"
$finalDebPath = Join-Path $installerDir "NetCDFViewer-linux-x86_64-1.0.2.deb"

function Convert-ToWslPath([string]$windowsPath) {
    $resolvedPath = (Resolve-Path $windowsPath).Path
    $drive = $resolvedPath.Substring(0, 1).ToLowerInvariant()
    $rest = $resolvedPath.Substring(2) -replace '\\', '/'
    return "/mnt/$drive$rest"
}

$linuxJavafxArtifacts = @(
    @{
        Artifact = "org.openjfx:javafx-base:21.0.8:jar:linux"
        JarName = "javafx-base-21.0.8-linux.jar"
    },
    @{
        Artifact = "org.openjfx:javafx-graphics:21.0.8:jar:linux"
        JarName = "javafx-graphics-21.0.8-linux.jar"
    },
    @{
        Artifact = "org.openjfx:javafx-controls:21.0.8:jar:linux"
        JarName = "javafx-controls-21.0.8-linux.jar"
    },
    @{
        Artifact = "org.openjfx:javafx-swing:21.0.8:jar:linux"
        JarName = "javafx-swing-21.0.8-linux.jar"
    }
)

$linuxJarRelativePaths = @{
    "javafx-base-21.0.8-linux.jar" = "javafx-base\21.0.8\javafx-base-21.0.8-linux.jar"
    "javafx-graphics-21.0.8-linux.jar" = "javafx-graphics\21.0.8\javafx-graphics-21.0.8-linux.jar"
    "javafx-controls-21.0.8-linux.jar" = "javafx-controls\21.0.8\javafx-controls-21.0.8-linux.jar"
    "javafx-swing-21.0.8-linux.jar" = "javafx-swing\21.0.8\javafx-swing-21.0.8-linux.jar"
}

Write-Host "Building project artifacts on Windows..."
mvn package

if (-not (Test-Path $jarPath)) {
    throw "Application jar not found: $jarPath"
}

Write-Host "Ensuring Linux JavaFX artifacts are available..."
foreach ($artifact in $linuxJavafxArtifacts) {
    mvn -q dependency:get "-Dartifact=$($artifact.Artifact)"
}

if (Test-Path $packageInput) {
    Remove-Item -LiteralPath $packageInput -Recurse -Force
}
if (Test-Path $javafxModuleDir) {
    Remove-Item -LiteralPath $javafxModuleDir -Recurse -Force
}
if (-not (Test-Path $installerDir)) {
    New-Item -ItemType Directory -Path $installerDir | Out-Null
}

New-Item -ItemType Directory -Path $packageInput | Out-Null
New-Item -ItemType Directory -Path $javafxModuleDir | Out-Null

Write-Host "Preparing Linux package input..."
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $packageInput $jarName)
Get-ChildItem -LiteralPath $runtimeLibDir -Filter *.jar |
    Where-Object { $_.Name -notlike "javafx-*-win.jar" } |
    ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $packageInput $_.Name)
    }

Get-ChildItem -LiteralPath $runtimeLibDir -Filter "javafx-*.jar" |
    Where-Object { $_.Name -notlike "javafx-*-win.jar" } |
    ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $javafxModuleDir $_.Name)
    }

foreach ($artifact in $linuxJavafxArtifacts) {
    $relativeJarPath = $linuxJarRelativePaths[$artifact.JarName]
    if ([string]::IsNullOrWhiteSpace($relativeJarPath)) {
        throw "Unsupported artifact mapping: $($artifact.JarName)"
    }

    $linuxJarPath = Join-Path $env:USERPROFILE (Join-Path ".m2\repository\org\openjfx" $relativeJarPath)

    if (-not (Test-Path $linuxJarPath)) {
        throw "Missing Linux JavaFX jar: $linuxJarPath"
    }

    Copy-Item -LiteralPath $linuxJarPath -Destination (Join-Path $javafxModuleDir $artifact.JarName)
}

if (Test-Path $finalDebPath) {
    Remove-Item -LiteralPath $finalDebPath -Force
}

$projectRootWsl = Convert-ToWslPath $projectRoot
$packageInputWsl = Convert-ToWslPath $packageInput
$installerDirWsl = Convert-ToWslPath $installerDir
$javafxModuleDirWsl = Convert-ToWslPath $javafxModuleDir
$iconPathWsl = Convert-ToWslPath $iconPath

Write-Host "Running jpackage inside WSL Ubuntu..."
$wslCommand = @"
set -e
cd '$projectRootWsl'
jpackage \
  --type deb \
  --name NetCDFViewer \
  --dest '$installerDirWsl' \
  --input '$packageInputWsl' \
  --main-jar '$jarName' \
  --main-class com.example.netcdfviewer.Launcher \
  --module-path '$javafxModuleDirWsl' \
  --add-modules javafx.controls,javafx.swing,java.logging,java.naming,java.security.jgss,java.compiler,jdk.jfr,jdk.unsupported,jdk.unsupported.desktop \
  --vendor lwj \
  --app-version $appVersion \
  --icon '$iconPathWsl' \
  --linux-shortcut \
  --linux-package-name netcdf-viewer \
  --linux-deb-maintainer 14959344+LWJ19386977562@user.noreply.gitee.com
"@

wsl bash -lc $wslCommand

$generatedDeb = Get-ChildItem -LiteralPath $installerDir -Filter *.deb |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $generatedDeb) {
    throw "No .deb file was generated in $installerDir"
}

if ($generatedDeb.FullName -ne $finalDebPath) {
    Move-Item -LiteralPath $generatedDeb.FullName -Destination $finalDebPath -Force
}

Write-Host "Linux package created: $finalDebPath"
