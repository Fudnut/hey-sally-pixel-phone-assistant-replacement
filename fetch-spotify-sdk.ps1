$ErrorActionPreference = 'Stop'
$target = Join-Path $PSScriptRoot 'app/libs/spotify-app-remote-release-0.8.0.aar'
$expected = 'B5A6DD880EAF01F63A871CBA9EF7AF77C341F8A94FFC8FDF2E9021F9A9D4C198'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
if (-not (Test-Path -LiteralPath $target)) {
    Invoke-WebRequest 'https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-app-remote-release-0.8.0.aar' -OutFile $target
}
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected) {
    throw 'Spotify SDK SHA-256 mismatch. Remove the downloaded file and retry.'
}
Write-Output 'Spotify App Remote SDK verified.'
