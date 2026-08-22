<#
.SYNOPSIS
  Packs a jpackage app image into an unsigned MSIX for the Microsoft Store.

.DESCRIPTION
  ONE implementation, used by both `windows-ime-release.yml` (which uploads the
  result for Partner Center) and `windows-ime-smoke.yml` (which installs a
  signed COPY of it and proves the app starts). Two copies of this sequence
  would eventually diverge, and the copy that diverged would be the one that
  shipped.

  The package is deliberately left UNSIGNED. Microsoft re-signs Store packages
  with its own certificate at ingestion, and a submission carrying a
  self-signed signature is rejected. Sideload testing signs a separate copy —
  see sign-for-sideload.ps1.

  What it does:
    1. copies the app image into a clean staging tree (the package root is the
       image root, so `Executable="BangluTyper.exe"` in the manifest resolves);
    2. drops AppxManifest.xml at that root and the generated PNG tiles in
       Assets\;
    3. runs MakeAppx.exe pack /d over the tree.

.PARAMETER AppImage
  The jpackage app image directory — the one CONTAINING BangluTyper.exe.

.PARAMETER Manifest
  AppxManifest.xml, as produced by `./gradlew :windows-ime:generateAppxManifest`.

.PARAMETER Assets
  The PNG tile directory, as produced by `:windows-ime:generateMsixAssets`.

.PARAMETER Out
  Path of the .msix to write.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$AppImage,
  [Parameter(Mandatory = $true)][string]$Manifest,
  [Parameter(Mandatory = $true)][string]$Assets,
  [Parameter(Mandatory = $true)][string]$Out
)

$ErrorActionPreference = 'Stop'

function Find-SdkTool([string]$name) {
  # The Windows SDK installs side-by-side versions; take the newest that has
  # the tool. Sorting by the directory name works because SDK build numbers are
  # dotted-numeric and left-padded in practice, but Version-sorting is exact.
  $roots = @(
    "${env:ProgramFiles(x86)}\Windows Kits\10\bin",
    "$env:ProgramFiles\Windows Kits\10\bin"
  ) | Where-Object { Test-Path $_ }
  $hits = foreach ($root in $roots) {
    Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
      ForEach-Object {
        $candidate = Join-Path $_.FullName "x64\$name"
        if (Test-Path $candidate) {
          $parsed = $null
          $order = if ([Version]::TryParse($_.Name, [ref]$parsed)) { $parsed } else { [Version]'0.0.0.0' }
          [pscustomobject]@{ Path = $candidate; Order = $order }
        }
      }
  }
  $best = $hits | Sort-Object Order -Descending | Select-Object -First 1
  if (-not $best) { throw "$name not found under any Windows Kits\10\bin\*\x64" }
  return $best.Path
}

if (-not (Test-Path (Join-Path $AppImage 'BangluTyper.exe'))) {
  throw "no BangluTyper.exe in '$AppImage' — pass the app image root, not its parent"
}

$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("banglu-msix-" + [guid]::NewGuid().ToString('N'))
Write-Host "staging  : $staging"
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item -Path (Join-Path $AppImage '*') -Destination $staging -Recurse -Force

Copy-Item -Path $Manifest -Destination (Join-Path $staging 'AppxManifest.xml') -Force
New-Item -ItemType Directory -Path (Join-Path $staging 'Assets') -Force | Out-Null
Copy-Item -Path (Join-Path $Assets '*.png') -Destination (Join-Path $staging 'Assets') -Force

$imageMb = [math]::Round(((Get-ChildItem $staging -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Host "app image: $imageMb MB"
Write-Host "--- AppxManifest.xml ---"
Get-Content (Join-Path $staging 'AppxManifest.xml')
Write-Host "--- Assets ---"
Get-ChildItem (Join-Path $staging 'Assets') | ForEach-Object { Write-Host ("  {0} ({1} bytes)" -f $_.Name, $_.Length) }

$makeappx = Find-SdkTool 'makeappx.exe'
Write-Host "makeappx : $makeappx"
New-Item -ItemType Directory -Path (Split-Path -Parent $Out) -Force | Out-Null
if (Test-Path $Out) { Remove-Item $Out -Force }

# /o overwrite, /d pack-from-directory. No /l (localized package) and no
# bundling: this is a single x64 package.
& $makeappx pack /o /d $staging /p $Out
if ($LASTEXITCODE -ne 0) { throw "makeappx failed with exit code $LASTEXITCODE" }

Remove-Item $staging -Recurse -Force
$msixMb = [math]::Round(((Get-Item $Out).Length / 1MB), 1)
Write-Host "=== MSIX: $Out ($msixMb MB, unsigned) ==="
