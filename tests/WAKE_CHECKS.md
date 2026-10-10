# Wake acoustic regression and processing scope

Wake detection uses one Vosk recognizer with a closed vocabulary: `hey sally`, `[unk]`, and 255 ordinary/phonetic decoy words. A finalized result activates only when `WakePhrase.matches` accepts it. An exact `hey` endpoint is held for one following finalized result so exact `sally` can complete the wake. There is no verifier, audio replay buffer, or stored audio/transcript. The WakeService five-second debounce and command recognizer are separate and unchanged.

`check-playlists.ps1` compiles the production detector and grammar. `WakeDetectorCheck.java` covers exact acceptance/rejection, a wake split across endpoints, one-result hold clearing, close idempotence, and rejection after close. `WakeGrammarCheck.java` structurally parses the generated JSON without a JSON library and checks ordering, quoting, lowercase entries, size, uniqueness, and required confusers.

The optional `check-wake.py` compiles the actual `WakeGrammar`, `WakeDetector`, `VoskWakeDecoder`, and `WakePhrase` with `WakeAcousticCheck.java`, then replays the 4-intentional/42-unrelated synthetic corpus through native desktop Vosk. A fixed seed applies clean, 20 dB, 10 dB, 5 dB, and quiet (0.25 gain) plus 10 dB conditions; noise RMS is relative to each clip's RMS. Reports contain aggregate counts only.

Run from the repository root. The acoustic check needs Python 3, Java 17, the local mono PCM16 16-kHz corpus, matching official desktop Vosk Java/native runtimes, JNA, and Gson. It downloads nothing and accesses neither the phone nor microphone. Keep generated audio, runtimes, and reports under ignored `.tools/`.

```powershell
powershell.exe -NoProfile -File ./tests/generate-wake-corpus.ps1
python -X utf8 ./tests/check-wake.py --corpus .tools/wake-acoustic/corpus/synthetic-corpus.json --library <path-to-libvosk.dll> --vosk-jar <path-to-vosk-0.3.45.jar> --jna-jar <path-to-jna-5.18.1.jar> --gson-jar <path-to-gson-2.13.2.jar> --report .tools/wake-acoustic/result-decoy.json
```

Keep the native library's companion DLLs in its folder. The checked Windows runtime is the official PyPI Vosk 0.3.45 wheel plus matching Maven Java jar; Android uses Vosk 0.3.75, so desktop results are qualified. No product dependency is added.

Clean, 20 dB, and 10 dB gate on 4/4 intentional detections and 0/42 false wakes. The 5 dB and quiet-plus-10 dB rows do not gate recall, but each permits at most 1/42 false wakes. The corpus uses Microsoft David and Zira synthetic voices, so it does not prove Android microphone/callback behavior, Steve's voice or accent, Zoom/background-talk behavior, car/Bluetooth behavior, locked-screen audio, far-field pickup, or battery use.

Debug builds may emit three fixed aggregate input probes containing levels, `finals`, and keyword-bit `candidateFlags`; release builds emit none. These probes contain no recognized transcript. Device validation must separately check normal-distance intentional wakes and sustained unrelated speech in the real target conditions.
