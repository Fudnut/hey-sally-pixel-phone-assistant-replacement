# Sally checkpoint — 22 October 2026

## Resume here

Steve's latest decision is **option 1: phone microphone for “Hey Sally,” then car microphone and speakers for the conversation, returning to stereo between conversations**. Driving operation must be voice-only. Implement and test this next; the hybrid transition is not implemented yet. Steve requested this checkpoint as the session allowance approached its limit.

Workspace: `C:\AI-Development\hay-sally`. Branch: `feature/car-audio-probe`. HEAD: `1cb3af6`. The subsequent car-audio implementation, diagnostics, tests and documentation are local and uncommitted. Preserve all existing changes. The repository is public; keys, device dumps, app preferences, APKs, contacts, messages and audio stay excluded.

## Current phone state

- Continuous car mode was force-stopped after the final successful trial and Steve's decision to switch. No hybrid update has been built or installed. The last command to the phone was force-stop, so it is not listening now.
- The saved `probe.carInput` preference remains true. Starting the currently installed listener reopens continuous Bluetooth capture and mono music. Do not treat it as hybrid mode.
- Phone screen was turned off for the successful screen-off trial; an unlock may be needed for setup after the next update. Do not attempt to bypass its lock.
- Installed private APK is `.tools/device-update/hey-sally-car-voice.apk`, SHA256 `55DEC6AD2BD9AE6CD3C5511D342249D43CF93EBDEC873777CD0EE7BC83560608`. It used the original signing identity and preserved Spotify preferences. Package/version unchanged: `com.steve.spotifywakeprobe`, versionCode 7, `0.2.1-alpha.5`.
- Earlier force-stop restored A2DP, and Steve confirmed normal stereo. After the latest stop, audible stereo recovery has not been separately reconfirmed.

## What worked

1. Incoming-call Spotify interruption fix is already committed/pushed as `1cb3af6`; Steve reported success.
2. Bounded debug diagnostic established actual Bluetooth SCO input, car speakers and timer/Stop/disconnect cleanup. The revised quiet test kept music silent through Sally's speech and the user's turn, then music resumed automatically. User confirmation and PAUSED/PAUSED/PLAYING snapshots agreed.
3. Production optional car mode uses owned local Vosk capture for wake and commands, with actual Bluetooth-input gating; it does not substitute the system recognizer's microphone.
4. Initial wake-to-command handover lost communication mode. Retaining one physical Bluetooth recorder across wake/command/prompt phases corrected that measured failure. PAUSE and NEXT subsequently completed, confirmed by Steve and diagnostics.
5. Latest repeat had the phone screen off: Steve confirmed NEXT changed the track and music stayed quiet while he spoke. Diagnostics show COMMAND_READY, COMMAND_HEARD NEXT and COMMAND_RESULT OK at approximately 16:43 local.
6. Focused and existing regression suites, debug/release assembly and lint passed after the recorder correction. One independent final source review found two focus-ordering issues; both received failing-then-passing regressions and fixes.

## Why the design is changing

- Continuous car-microphone listening holds the car's hands-free telephone audio channel open, including while waiting for Hey Sally. Music becomes telephone/mono audio. Finishing a command does not return stereo in that mode.
- Steve tried this mode and explicitly chose option 1 afterward. His earlier temporary selection of option 1 was withdrawn, but the latest selection is affirmative and current.
- A single press of the Pajero's Speak button opened only its own voice menu. The Pixel stayed on its home screen; no Android voice request was observed. This did not establish a Sally activation path. No alternate gesture was verified in the exact-era manual; avoid applying modern Android Auto instructions.
- Android documentation/source provides no supported guarantee of simultaneous same-car SCO microphone and A2DP stereo. Do not claim universal Bluetooth hardware impossibility. The working product direction is to release the car route between conversations.
- Existing Sally assistant eligibility does not implement button commands: `MusicSessionService` returns an empty session. Changing default assistant is unnecessary for this direction and can auto-start the saved continuous mode through `MusicVoiceService.onReady()`.

## Next implementation slice

Use the current car session and recorder boundaries. Keep implementation inline and request one fresh final review after the change. Write failing focused tests before implementation.

1. Separate the selected car-conversation policy from the active input phase. Idle hybrid mode must open only phone wake capture, make no communication-route request, and allow stereo.
2. On accepted Hey Sally, fully close the phone recorder before acquiring the bounded car route. Hold temporary music priority for the transition and conversation. Open the required-Bluetooth recorder while the route connects; actual Bluetooth input must be verified before the ready beep or command acceptance.
3. Retain that one car recorder across prompts and replies. Preserve quiet focus, connection timeout (10 seconds), command timeout (20 seconds) and overall conversation bound (90 seconds). No silent phone-command fallback on Bluetooth failure.
4. On normal completion, close car capture before releasing mode/device requests and return to phone wake. Separate graceful release from interruption cancellation: the current `stopCarCapture()` increments `commandSequence` and cancels playback, so calling the existing normal close from `executeCommand()` would invalidate its own pending player request.
5. Real calls, route loss, permission loss, focus loss and service stop must still cancel safely. Recovery in hybrid mode returns to phone wake. Keep old route/ready/focus/TTS callbacks from affecting a newer session. Preserve the incoming-call fix and do not explicitly resume Spotify after a real call.
6. Account for asynchronous Spotify failure feedback after the car session has been released: do not accidentally speak through the handset when car conversation mode is selected. A car reply needs a verified bounded car route.
7. Expose the selected wake policy clearly during parked setup. Both continuous-car and hybrid behavior must be unambiguous if retained as choices. Applying a setup preference must not require phone touches during normal driving operation.

### Source hazards already identified

- `WakeService.carInputEnabled` currently means both saved preference and active capture. `carReady()` would reject idle phone wake under that preference unless these meanings are separated.
- `listen(true)` currently checks `carReady()` before opening command capture. Hybrid acquisition needs a recorder to establish actual readiness; copying this order creates a connection timeout.
- `listen(false)` currently runs inside ordinary command dispatch. Graceful route release there must preserve `commandSequence`, the playback request and valid result callbacks.
- Reused audio-focus callbacks need ownership/generation checks when sessions end and another begins.
- Publish Sally's mode lease before synchronous mode callbacks and retain it through release, as the existing code does.

### Required checks and parked acceptance

- Focused tests: idle stereo/no car request; phone close before car acquire; no beep/command before actual Bluetooth input; retained car recorder through prompts; successful playback survives graceful release; timeout/disconnect/focus/call cleanup; stale callback rejection; phone-wake recovery.
- Existing suite runner: `.superpowers/sdd/2026-10-22-car-voice-integration/verify.ps1`. Build debug/release and lint after source changes; review the new transition before installation.
- Same-signer private update only, with Spotify preference hashes compared before/after. Original signer is `.tools/debug.keystore`; do not use this computer's newly generated default debug key. Never print or commit keys or preference contents.
- Parked trial: stereo while idle; normal-volume Hey Sally from the phone's usual position with music; actual car input and audible readiness; music quiet through the user's turn; NEXT/PAUSE takes effect; stereo returns afterward; repeat with screen off. Wake reliability remains a hardware gate because Steve reported the phone microphone is weak.
- Contacts, finalized conversational answers and SMS reading remain subsequent work. No actual call was placed, and no SMS body was read. Never call 000 for testing.

## Local tools and evidence

- ADB: `.tools/android-sdk/platform-tools/adb.exe`; SDK: `.tools/android-sdk`; JDK: `.tools/jdk/jdk-21.0.12.1+1`; Gradle cache: `.tools/gradle-cache`; Python: `C:\Users\steve\AppData\Local\Programs\Python\Python313\python.exe`.
- Build/sign/runtime device commands generally need the existing sandbox escalation flow. Authorization for the current private trial and data-preserving update already exists; do not repeatedly ask permission.
- Private evidence: `.tools/car-audio-check/`; local ledger/build logs: `.superpowers/sdd/2026-10-22-car-voice-integration/` (ignored). Last repeat diagnostics: `continuous-retrial-final-diagnostics.xml`.
- Use `adb logcat -d -s TAG:LEVEL ...` for focused buffered events. The first live wildcard-filtered collection did not preserve its marker; the later buffered collection did and is the valid button-check evidence.
- More results: `docs/CAR_AUDIO_CHECK.md`, `docs/CAR_VOICE_TRIAL.md`; existing plans/specs: `docs/superpowers/`.

Allowance checked at checkpoint: five-hour window 10% remaining, weekly 75% remaining. Five-hour reset reported as **22 October 2026, 8:13 pm Australia/Sydney**. The user's UI reported about 9%; these are point-in-time snapshots, not a per-task token budget.
