#!/usr/bin/env python3
# Copyright (c) 2026 Spotify Wake Probe contributors.
# SPDX-License-Identifier: GPL-3.0-only
# Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
"""Optional acoustic regression: local WAV corpus, production wake configuration/matcher.

Usage: python -X utf8 tests/check-wake.py --corpus PATH --library PATH
Corpus JSON rows: {"file": "relative.wav", "expected": true/false}.
WAVs must be synthetic/public test speech, mono 16-bit PCM at 16 kHz.
Use an official desktop libvosk with its adjacent runtime DLLs. No downloads,
phone access, microphone capture or audio/transcript logging are performed.
Desktop results do not validate Android battery use, microphones or accents.
"""
import argparse
import base64
import ctypes
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import wave

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--corpus', type=Path, required=True)
parser.add_argument('--library', type=Path, required=True)
parser.add_argument('--report', type=Path)
args = parser.parse_args()
corpus = args.corpus.resolve()
library = args.library.resolve()
source = root / 'app/src/main/java/com/steve/spotifywakeprobe'

# Evaluate the actual constructor expression and actual WakePhrase matcher.
# The Android Service cannot run on a desktop JVM. This adapter records only
# the selected constructor's grammar; native Vosk below performs the decoding.
expressions = re.findall(r'^        recognizer = (.*?);',
                         (source / 'WakeService.java').read_text(encoding='utf-8'), re.M | re.S)
if len(expressions) != 1:
    raise RuntimeError('WakeService recognizer assignment changed; review the acoustic adapter')
with tempfile.TemporaryDirectory(prefix='hey-sally-wake-') as directory:
    build = Path(directory)
    adapter = build / 'WakeAcousticProbe.java'
    adapter.write_text('''package com.steve.spotifywakeprobe;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
final class Recognizer {
    final String grammar;
    Recognizer(Object model, float rate) { grammar = null; }
    Recognizer(Object model, float rate, String value) { grammar = value; }
}
public final class WakeAcousticProbe {
    static Recognizer create(Object model, boolean forCommand) { return ''' + expressions[0] + '''; }
    public static void main(String[] args) {
        if (args.length == 0) {
            String grammar = create(new Object(), false).grammar;
            System.out.println(grammar == null ? "DEFAULT" : Base64.getEncoder().encodeToString(
                    grammar.getBytes(StandardCharsets.UTF_8)));
        } else for (String text : args) System.out.println(WakePhrase.matches(text));
    }
}
''', encoding='utf-8')
    subprocess.run(['javac', '-d', str(build), str(adapter), str(source / 'WakePhrase.java')], check=True)
    java = ['java', '-cp', str(build), 'com.steve.spotifywakeprobe.WakeAcousticProbe']
    selected = subprocess.check_output(java, text=True).strip()
    grammar = None if selected == 'DEFAULT' else base64.b64decode(selected)
    # Keep DLL search path alive for all recognition calls on Windows.
    dll_directory = os.add_dll_directory(str(library.parent)) if os.name == 'nt' else None
    native = ctypes.CDLL(str(library))
    def bind(name, result_type, *argument_types):
        function = getattr(native, 'vosk_' + name)
        function.restype = result_type
        function.argtypes = list(argument_types)
        return function
    pointer = ctypes.c_void_p
    model_new = bind('model_new', pointer, ctypes.c_char_p)
    model_free = bind('model_free', None, pointer)
    recognizer_new = bind('recognizer_new', pointer, pointer, ctypes.c_float)
    recognizer_grammar = bind('recognizer_new_grm', pointer, pointer, ctypes.c_float, ctypes.c_char_p)
    recognizer_free = bind('recognizer_free', None, pointer)
    accept = bind('recognizer_accept_waveform', ctypes.c_int, pointer, ctypes.c_char_p, ctypes.c_int)
    result = bind('recognizer_result', ctypes.c_char_p, pointer)
    final = bind('recognizer_final_result', ctypes.c_char_p, pointer)
    bind('set_log_level', None, ctypes.c_int)(-1)
    rows = json.loads(corpus.read_text(encoding='utf-8-sig'))
    if not rows or not any(row['expected'] for row in rows) or not any(not row['expected'] for row in rows):
        raise ValueError('Corpus must contain intentional wakes and unrelated speech')
    model = model_new(os.fsencode(root / 'app/src/main/assets/model-en-us'))
    if not model:
        raise RuntimeError('Fetch the pinned model before running acoustic checks')
    decoded = []
    try:
        for index, row in enumerate(rows):
            with wave.open(str(corpus.parent / row['file']), 'rb') as audio:
                if audio.getparams()[:3] != (1, 2, 16000) or audio.getcomptype() != 'NONE':
                    raise ValueError('Corpus requires mono 16-bit PCM at 16 kHz')
                pcm = audio.readframes(audio.getnframes()) + b'\0' * 64000
            recognizer = (recognizer_new(model, 16000) if grammar is None
                          else recognizer_grammar(model, 16000, grammar))
            if not recognizer:
                raise RuntimeError('Could not create native recognizer')
            texts = []
            started = time.perf_counter()
            try:
                for offset in range(0, len(pcm), 6400):
                    block = pcm[offset:offset + 6400]
                    if accept(recognizer, block, len(block)):
                        texts.append(json.loads(result(recognizer)).get('text', ''))
                texts.append(json.loads(final(recognizer)).get('text', ''))
            finally:
                recognizer_free(recognizer)
            # Transcripts live only in memory for the production matcher probe.
            decoded.append((index, row['expected'], texts, time.perf_counter() - started, len(pcm) / 32000))
        report = []
        for index, expected, texts, elapsed, duration in decoded:
            matches = subprocess.check_output(java + texts, text=True).splitlines()
            accepted = 'true' in matches
            report.append({'sample': index, 'expected': expected, 'accepted': accepted,
                           'rtf': round(elapsed / duration, 4)})
    finally:
        model_free(model)
        if dll_directory is not None:
            dll_directory.close()
false_wakes = sum(row['accepted'] and not row['expected'] for row in report)
misses = sum(row['expected'] and not row['accepted'] for row in report)
summary = {'grammar': 'unrestricted' if grammar is None else 'restricted',
           'intentional': sum(row['expected'] for row in report),
           'unrelated': sum(not row['expected'] for row in report),
           'false_wakes': false_wakes, 'misses': misses, 'samples': report}
if args.report:
    args.report.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print(f"Wake acoustic check: {summary['grammar']}; {summary['intentional']} intentional, "
      f"{summary['unrelated']} unrelated; false wakes={false_wakes}, misses={misses}")
if false_wakes or misses:
    raise SystemExit(1)
