param([Parameter(Mandatory=$true)][string]$ApkPath)
$ErrorActionPreference='Stop'
# Compatibility entry point. This performs a local package audit/copy only.
& (Join-Path $PSScriptRoot 'package-apk.ps1') -ApkPath $ApkPath
