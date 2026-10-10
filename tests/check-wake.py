#!/usr/bin/env python3
# Copyright (c) 2026 Spotify Wake Probe contributors.
# SPDX-License-Identifier: GPL-3.0-only
# Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
"""Optional acoustic check of the actual WakeDetector/VoskWakeDecoder pipeline.

Needs a synthetic/public mono PCM16 16-kHz WAV corpus and matching official
Vosk desktop Java/native runtimes, JNA and Gson. No downloads, phone access,
microphone capture or decoded-transcript logging. See WAKE_CHECKS.md.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
for name in ['corpus', 'library', 'vosk-jar', 'jna-jar', 'gson-jar']:
    parser.add_argument('--' + name, type=Path, required=True)
parser.add_argument('--report', type=Path)
args = parser.parse_args()
paths = [args.vosk_jar.resolve(), args.jna_jar.resolve(), args.gson_jar.resolve()]
classpath = os.pathsep.join(map(str, paths))
source = root / 'app/src/main/java/com/steve/spotifywakeprobe'
with tempfile.TemporaryDirectory(prefix='hey-sally-wake-') as directory:
    build = Path(directory)
    sources = [source / name for name in ['WakePhrase.java', 'WakeDetector.java', 'VoskWakeDecoder.java']]
    sources.append(root / 'tests/WakeAcousticCheck.java')
    subprocess.run(['javac', '-cp', classpath, '-d', str(build), *map(str, sources)], check=True)
    library = args.library.resolve()
    environment = dict(os.environ)
    environment['PATH'] = str(library.parent) + os.pathsep + environment.get('PATH', '')
    command = ['java', '--enable-native-access=ALL-UNNAMED', '-Djna.library.path=' + str(library.parent),
               '-cp', str(build) + os.pathsep + classpath, 'com.steve.spotifywakeprobe.WakeAcousticCheck',
               str(root / 'app/src/main/assets/model-en-us'), str(args.corpus.resolve())]
    if args.report:
        command.append(str(args.report.resolve()))
    subprocess.run(command, env=environment, check=True)
