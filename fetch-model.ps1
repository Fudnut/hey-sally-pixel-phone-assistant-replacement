$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$tools = Join-Path $root '.tools'
$zip = Join-Path $tools 'vosk-model-small-en-us-0.15.zip'
$extract = Join-Path $tools 'model-extract'
$source = Join-Path $extract 'vosk-model-small-en-us-0.15'
$target = Join-Path $root 'app\src\main\assets\model-en-us'
$expected = '30F26242C4EB449F948E42CB302DD7A686CB29A3423A8367F99FF41780942498'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
if (-not (Test-Path -LiteralPath $zip)) {
    Invoke-WebRequest 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip' -OutFile $zip
}
if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected) {
    throw 'Vosk model SHA-256 mismatch'
}
if (-not (Test-Path -LiteralPath $source)) {
    Expand-Archive -LiteralPath $zip -DestinationPath $extract -Force
}
New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem -LiteralPath $source | Copy-Item -Destination $target -Recurse -Force
Set-Content -LiteralPath (Join-Path $target 'uuid') -Value 'vosk-model-small-en-us-0.15-30f26242' -NoNewline
Write-Output "Model ready at $target"
