<#
.SYNOPSIS
  Signs a COPY of the MSIX with a throwaway self-signed certificate, for
  sideload testing only.

.DESCRIPTION
  Windows refuses to install an MSIX it cannot trace to a trusted certificate,
  so a CI smoke test has to sign one. The Store does not: Microsoft re-signs at
  ingestion and REJECTS a submission that arrives already signed by us.

  Those two facts are why this script signs a copy and never the original. The
  file the release workflow uploads for Partner Center must stay byte-identical
  to what MakeAppx produced.

  The certificate subject must equal the manifest's Package/Identity/Publisher
  EXACTLY — Add-AppxPackage compares the two and refuses the package on any
  difference. That value is read out of the manifest rather than passed in, so
  the two cannot drift.

  The certificate is generated fresh in the runner's own store, used, and never
  exported anywhere durable. It is not a signing key we own or keep.

.PARAMETER Msix
  The unsigned .msix produced by pack.ps1. Not modified.

.PARAMETER Manifest
  AppxManifest.xml, read for the Publisher subject.

.PARAMETER Out
  Path of the signed copy to write.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Msix,
  [Parameter(Mandatory = $true)][string]$Manifest,
  [Parameter(Mandatory = $true)][string]$Out
)

$ErrorActionPreference = 'Stop'

function Find-SdkTool([string]$name) {
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

[xml]$xml = Get-Content $Manifest
$subject = $xml.Package.Identity.Publisher
if (-not $subject) { throw "no Package/Identity/Publisher in $Manifest" }
Write-Host "cert subject (from the manifest): $subject"

Copy-Item -Path $Msix -Destination $Out -Force

# CodeSigningCert + the Code Signing EKU: signtool will not use a certificate
# without it, and Add-AppxPackage will not trust one that is not in Root.
$cert = New-SelfSignedCertificate `
  -Type Custom `
  -Subject $subject `
  -KeyUsage DigitalSignature `
  -FriendlyName "Banglu Typer MSIX sideload test (throwaway)" `
  -CertStoreLocation "Cert:\CurrentUser\My" `
  -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}")

# Trust it for THIS machine only, so Add-AppxPackage accepts the signature.
$cerPath = Join-Path ([System.IO.Path]::GetTempPath()) ("banglu-sideload-" + [guid]::NewGuid().ToString('N') + ".cer")
Export-Certificate -Cert $cert -FilePath $cerPath | Out-Null
Import-Certificate -FilePath $cerPath -CertStoreLocation "Cert:\LocalMachine\Root" | Out-Null
Remove-Item $cerPath -Force -ErrorAction SilentlyContinue

# Selected by thumbprint out of the current user's store — no .pfx is ever
# written and no password exists to be passed on a command line or logged.
$signtool = Find-SdkTool 'signtool.exe'
Write-Host "signtool : $signtool"
& $signtool sign /fd SHA256 /sha1 $cert.Thumbprint $Out
if ($LASTEXITCODE -ne 0) { throw "signtool failed with exit code $LASTEXITCODE" }

Write-Host "=== signed sideload copy: $Out ==="
