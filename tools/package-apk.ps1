param(
  [Parameter(Mandatory=$true)][string]$ApkPath,
  [ValidateSet('Plus','SS')][string]$Edition='Plus'
)
$ErrorActionPreference='Stop'
$plusRoot=Split-Path -Parent $PSScriptRoot
$apk=(Resolve-Path -LiteralPath $ApkPath).Path
$sdk=$env:ANDROID_HOME
if(-not $sdk){$sdk=$env:ANDROID_SDK_ROOT}
if(-not $sdk -and (Test-Path -LiteralPath (Join-Path $plusRoot 'local.properties'))){
  $sdkLine=Get-Content -LiteralPath (Join-Path $plusRoot 'local.properties') | Where-Object {$_ -match '^sdk\.dir='} | Select-Object -First 1
  if($sdkLine){$sdk=$sdkLine.Substring(8).Replace('\:',':').Replace('\\','\')}
}
if(-not $sdk){throw 'Configure ANDROID_HOME or ANDROID_SDK_ROOT before packaging.'}
$buildTools=Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Where-Object {$_.Name -match '^\d+\.\d+\.\d+$'} | Sort-Object {[version]$_.Name} -Descending | Select-Object -First 1
if(-not $buildTools){throw 'Android SDK build-tools are missing.'}
$aapt=Join-Path $buildTools.FullName 'aapt.exe'
$signer=Join-Path $buildTools.FullName 'apksigner.bat'
& $signer verify $apk
if($LASTEXITCODE -ne 0){throw 'APK is unsigned or invalid. Sign it before packaging.'}
$badging=(& $aapt dump badging $apk) -join "`n"
$expectedId=if($Edition -eq 'SS'){'com.khumam.gbaduo.plus.ss'}else{'com.khumam.gbaduo.plus'}
$expectedLabel=if($Edition -eq 'SS'){'GBA Duo Plus SS'}else{'GBA Duo Plus'}
if($LASTEXITCODE -ne 0 -or -not $badging.Contains("package: name='$expectedId'")){throw 'Wrong release application ID for the selected edition.'}
if(-not $badging.Contains("application-label:'$expectedLabel'")){throw 'Wrong application label.'}
if($badging -notmatch "uses-permission: name='android.permission.INTERNET'"){throw 'RetroAchievements network permission is missing.'}
$version=[regex]::Match($badging,"versionName='([^']+)'").Groups[1].Value
if($version -notmatch '^\d+\.\d+\.\d+(?:[-.A-Za-z0-9]+)?$'){throw 'Unexpected APK version string.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::OpenRead($apk)
try{
  $native=$archive.GetEntry('lib/arm64-v8a/libgbaduo.so')
  if(-not $native){throw 'ARM64 native library is missing.'}
  foreach($entry in $archive.Entries | Where-Object FullName -Match '^classes\d*\.dex$'){
    $stream=$entry.Open();$buffer=New-Object System.IO.MemoryStream
    try{
      $stream.CopyTo($buffer);$text=[System.Text.Encoding]::UTF8.GetString($buffer.ToArray())
      foreach($forbidden in @('setHashOverride','ra-hash-overrides','translate.googleapis.com','api.groq.com','TranslationCredentials')){
        if($text.Contains($forbidden)){throw 'An excluded feature remains in the APK.'}
      }
    }finally{$stream.Dispose();$buffer.Dispose()}
  }
}finally{$archive.Dispose()}
$baseVersion=if($Edition -eq 'SS'){
  if(-not $version.EndsWith('-ss')){throw 'SS version suffix is missing.'}
  $version.Substring(0,$version.Length-3)
}else{$version}
$release=Join-Path $plusRoot ('releases\'+$baseVersion)
New-Item -ItemType Directory -Path $release -Force | Out-Null
$name=if($Edition -eq 'SS'){'GBA-Duo-Plus-SS-'+$baseVersion+'.apk'}else{'GBA-Duo-Plus-'+$version+'.apk'}
$output=Join-Path $release $name
Copy-Item -LiteralPath $apk -Destination $output -Force
$digest=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
$sums=Join-Path $release 'SHA256SUMS.txt'
$previous=if(Test-Path -LiteralPath $sums){@(Get-Content -LiteralPath $sums | Where-Object {$_ -and -not $_.EndsWith('  '+$name)})}else{@()}
Set-Content -LiteralPath $sums -Value (@($previous)+"$digest  $name" | Sort-Object) -Encoding ASCII
Write-Output "Audited local package: $output"
Write-Output 'No GitHub release was created or uploaded.'
