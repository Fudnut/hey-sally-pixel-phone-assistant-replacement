$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root '.tools/car-audio-checks'
New-Item -ItemType Directory -Path $output -Force | Out-Null
& javac -d $output "$root/app/src/debug/java/com/steve/spotifywakeprobe/CarAudioProbe.java" "$PSScriptRoot/CarAudioProbeCheck.java"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $output com.steve.spotifywakeprobe.CarAudioProbeCheck
exit $LASTEXITCODE
