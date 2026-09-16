$ErrorActionPreference = "Stop"

$Version = "1.0.1"

$ProjectRoot =
    Resolve-Path "$PSScriptRoot\..\.."

$ReleaseDir =
    Join-Path $ProjectRoot "releases"

$WorkDir =
    Join-Path $env:TEMP "zorza-windows-build"

$Icon =
    Join-Path $ProjectRoot "packaging\windows\Zorza.ico"

Write-Host ""
Write-Host "======================================"
Write-Host " Zorza Notes Windows Release Build"
Write-Host " Version $Version"
Write-Host "======================================"
Write-Host ""

Set-Location $ProjectRoot

# -------------------------------------------------------
# Check tools
# -------------------------------------------------------

Write-Host "1. Checking Java..."
java --version

if ($LASTEXITCODE -ne 0) {
    throw "Java was not found."
}

Write-Host ""
Write-Host "2. Checking jpackage..."
jpackage --version

if ($LASTEXITCODE -ne 0) {
    throw "jpackage was not found."
}

Write-Host ""
Write-Host "3. Checking Maven..."
mvn --version

if ($LASTEXITCODE -ne 0) {
    throw "Maven was not found."
}

# -------------------------------------------------------
# Build Java application
# -------------------------------------------------------

Write-Host ""
Write-Host "4. Building Zorza Notes..."

mvn clean package

if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed."
}

# -------------------------------------------------------
# Prepare directories
# -------------------------------------------------------

Write-Host ""
Write-Host "5. Preparing build directories..."

if (Test-Path $WorkDir) {

    Remove-Item `
        -Recurse `
        -Force `
        $WorkDir
}

New-Item `
    -ItemType Directory `
    -Force `
    -Path $WorkDir |
    Out-Null

New-Item `
    -ItemType Directory `
    -Force `
    -Path $ReleaseDir |
    Out-Null

# Remove previous MSI releases only.

Get-ChildItem `
    -Path $ReleaseDir `
    -Filter "*.msi" `
    -ErrorAction SilentlyContinue |
    Remove-Item -Force

# -------------------------------------------------------
# Build jpackage arguments
# -------------------------------------------------------

Write-Host ""
Write-Host "6. Preparing Windows package..."

$JPackageArguments = @(
    "--type", "msi",
    "--name", "Zorza Notes",
    "--app-version", $Version,
    "--description", "Private local-first notes",
    "--vendor", "Zorza",
    "--input", "$ProjectRoot\target\package",
    "--main-jar", "zorza-notes.jar",
    "--main-class", "org.zorzanotes.ZorzaLauncher",
    "--dest", $WorkDir,
    "--win-menu",
    "--win-menu-group", "Zorza",
    "--win-shortcut",
    "--win-dir-chooser"
)

# -------------------------------------------------------
# Add custom icon when available
# -------------------------------------------------------

if (Test-Path $Icon) {

    Write-Host "Using Zorza icon:"
    Write-Host $Icon

    $JPackageArguments += @(
        "--icon",
        $Icon
    )

} else {

    Write-Host ""
    Write-Host "WARNING:"
    Write-Host "packaging\windows\Zorza.ico was not found."
    Write-Host "Building with the default application icon."
}

# -------------------------------------------------------
# Build MSI
# -------------------------------------------------------

Write-Host ""
Write-Host "7. Building Windows MSI..."

& jpackage @JPackageArguments

if ($LASTEXITCODE -ne 0) {
    throw "jpackage MSI build failed."
}

# -------------------------------------------------------
# Copy MSI
# -------------------------------------------------------

Write-Host ""
Write-Host "8. Copying MSI to releases..."

$MsiFiles =
    Get-ChildItem `
        -Path $WorkDir `
        -Filter "*.msi"

if ($MsiFiles.Count -eq 0) {
    throw "No MSI file was created."
}

foreach ($Msi in $MsiFiles) {

    Copy-Item `
        -Path $Msi.FullName `
        -Destination $ReleaseDir `
        -Force
}

# -------------------------------------------------------
# Done
# -------------------------------------------------------

Write-Host ""
Write-Host "======================================"
Write-Host " ZORZA NOTES WINDOWS BUILD COMPLETE"
Write-Host "======================================"
Write-Host ""

Get-ChildItem `
    -Path $ReleaseDir `
    -Filter "*.msi"

Write-Host ""
Write-Host "Release directory:"
Write-Host $ReleaseDir
Write-Host ""