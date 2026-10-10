$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root '.tools/playlist-checks'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sources = @(
    "$root/app/src/main/java/com/steve/spotifywakeprobe/WakePhrase.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/WakeResults.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/WakeDetector.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/CommandFailure.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/VoiceCommand.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/PlaylistBrowse.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/CommandLanguage.java",
    "$root/app/src/main/java/com/steve/spotifywakeprobe/SpecialDestination.java",
    "$PSScriptRoot/PlaylistBrowseCheck.java",
    "$PSScriptRoot/WakeDetectorCheck.java"
)
& javac -d $output @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $output com.steve.spotifywakeprobe.WakePhrase
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $output com.steve.spotifywakeprobe.PlaylistBrowseCheck
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp $output com.steve.spotifywakeprobe.WakeDetectorCheck
exit $LASTEXITCODE
