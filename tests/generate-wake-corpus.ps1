# Copyright (c) 2026 Spotify Wake Probe contributors.
# SPDX-License-Identifier: GPL-3.0-only
# Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
param([string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) '.tools/wake-acoustic/corpus'))
$ErrorActionPreference = 'Stop'
# Run with Windows PowerShell: local installed voices, no microphone or network.
Add-Type -AssemblyName System.Speech
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$base = (Resolve-Path -LiteralPath $OutputDirectory).Path
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo (16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$phrases = @('Hey Sally', 'Hey Sally', 'subject', 'administration', 'they', 'the subject', 'the administration', 'they said', 'they sell it', 'they certainly do', 'he said', 'hey salad', 'hey sadly', 'Hey Spotify', 'Hey Music', 'Sally', 'administration of the subject', 'the subject of the administration', 'they are discussing the subject', 'this subject needs careful administration', 'they said the administration would discuss the subject', 'in the next session we will examine the subject', 'please Hey Sally now')
$records = @()
try {
    foreach ($voice in $synth.GetInstalledVoices()) {
        $synth.SelectVoice($voice.VoiceInfo.Name)
        for ($i=0; $i -lt $phrases.Length; $i++) {
            $file = "$($voice.VoiceInfo.Name.Replace(' ', '-'))-$i.wav"
            $synth.Rate = if ($i -eq 1) { -2 } else { 0 }
            $synth.SetOutputToWaveFile((Join-Path $base $file), $format)
            $synth.Speak($phrases[$i])
            $synth.SetOutputToNull()
            $records += [pscustomobject]@{file=$file; phrase=$phrases[$i]; voice=$voice.VoiceInfo.Name; expected=($i -lt 2)}
        }
    }
} finally { $synth.Dispose() }
if ($records.Count -eq 0) { throw 'Install a local Windows speech voice before generating the test corpus.' }
$records | ConvertTo-Json | Set-Content (Join-Path $base 'synthetic-corpus.json') -Encoding UTF8
Write-Output "Generated $($records.Count) local synthetic utterances in $base. No microphone input."
