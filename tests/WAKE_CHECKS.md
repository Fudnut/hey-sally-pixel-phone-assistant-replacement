# Wake acoustic regression

The wake recognizer must let unrelated words compete with **Hey Sally**, then accept only a finalized exact match. A grammar containing only `hey sally` and `[unk]` can decode unrelated speech as the wake phrase even with word confidence 1.0. Raising confidence alone does not fix that failure.

`check-wake.py` evaluates the recognizer constructor expression from WakeService through a small desktop adapter, then replays WAVs through native Vosk with that selected grammar and the application's downloaded model. It uses the actual WakePhrase matcher. The adapter covers constructor selection; it does not run the Android service, microphone or callback lifecycle.

Run from the repository root. The optional test needs Python 3, Java, a local synthetic/public WAV corpus and an official desktop Vosk native library. It does not download dependencies or read the phone/microphone. Generated audio, runtime files and reports belong under ignored `.tools/`, outside release assets.

On Windows, generate synthetic speech using installed local voices:

```powershell
powershell.exe -NoProfile -File ./tests/generate-wake-corpus.ps1
python -X utf8 ./tests/check-wake.py --corpus .tools/wake-acoustic/corpus/synthetic-corpus.json --library <path-to-libvosk.dll> --report .tools/wake-acoustic/result.json
```

Keep the native library's companion DLLs in the same folder. The verified Windows test runtime was the official PyPI `vosk-0.3.45-py3-none-win_amd64.whl`, SHA-256 `6994ddc68556c7e5730c3b6f6bad13320e3519b13ce3ed2aa25a86724e7c10ac`; extract its `vosk/*.dll` files. The Python package need not be installed. The app still uses Android Vosk 0.3.75; desktop runtime results are qualified accordingly.

The 10 October synthetic corpus used Microsoft David Desktop and Microsoft Zira Desktop: 42 unrelated utterances and four intentional wakes (normal/slower rate). It includes the reported words subject, administration and they, ordinary sentences, and confusing phrases such as they said/they sell it. The wake-only grammar produced five false wakes; unrestricted decoding produced zero false wakes and recognized all four intentional wakes. These observations establish a regression for this corpus, not a general false-wake rate or Android result. The report stores sample numbers, outcomes and timing, not decoded transcripts.

Full-vocabulary decoding requires more processing than the tiny grammar. Before a replacement APK is considered device-validated, repeat the video-call/background-speech trial at normal volume, test intentional Hey Sally with the user's accent/normal distance and locked screen, and compare CPU/battery use during extended listening. Check actual ready beeps and diagnostic events; desktop recognition does not prove audible behavior. A genuine Hey Sally played by a speaker can still activate the app: no speaker verification is implemented.
