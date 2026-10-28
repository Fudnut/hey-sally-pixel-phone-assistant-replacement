# Car Voice Integration Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation, followed by the independently authorised final review. Track each step below. Do not mark a device acceptance gate complete using doubles alone.

**Goal:** Recognise Hey Sally and conversational replies through the Pajero microphone, speak through its speakers, and keep Spotify quiet throughout active conversation.

**Architecture:** Introduce one owned car-audio session shared by wake capture, command capture and replies. Separate passive wake listening from a bounded active conversation. Build on the diagnostic's verified recorder route, rather than treating a preferred output device as proof of microphone routing.

**Evidence:** [Parked results](../../CAR_AUDIO_CHECK.md). Short Bluetooth capture/music coexistence works. Steve reported a 1–13% input meter and a transient car incoming-call display. Wake recognition, ordinary-volume command quality, music fidelity and long-running behavior remain unverified.

**Constraints:** Preserve package, signing identity, app data, wake grammar and the incoming-call fix. Car mode must not silently fall back to the phone microphone. No contacts/messages are needed for this slice. Keep logs to fixed categories and device types. Do not place calls. Do not commit/push without direction.

## Parked checkpoint

Source implementation, focused/existing regressions, debug/release builds and lint passed, including the persistent-recorder handover fix. Actual car wake, PAUSE and NEXT succeeded. Continuous capture failed music fidelity: Spotify used the car's telephone/mono channel. Stopping Sally restored A2DP and Steve confirmed stereo returned, correcting an accidental negative response. Sally remains stopped. Activation between bounded car conversations needs a user decision and a device trial; car-button delivery has not been established. Expected answers, names, screen-off/longer operation and real-call handoff remain acceptance gates. This plan is not complete.

## Task 1: audio ownership and call priority

**Files:** create `app/src/main/java/com/steve/spotifywakeprobe/CarAudioSession.java`; modify `CallAudioMonitor.java`; create `tests/CarAudioSessionCheck.java` and `tests/check-car-voice.py`.

- [ ] Write failing behavior checks for start/stop, permission loss, one Bluetooth route, actual built-in-input rejection, connection deadline, route loss, stale callbacks and cleanup once.
- [ ] Model route ownership, passive capture and active conversation separately. Give connection attempts a fixed 10-second deadline. Callbacks carry session generations; no late callback reopens audio after cancellation.
- [ ] Reconcile real call state before requesting a route and before opening each capture. Distinguish Sally's own mode request from a real call without globally exempting `MODE_IN_COMMUNICATION`. Ringing/active Telecom calls, protected phone-state changes, competing capture/silencing and route ownership loss must preempt Sally. If ownership is uncertain, yield rather than override another caller.
- [ ] Release only Sally's mode/device/focus requests. Never restore a saved route over Phone or send Spotify Resume after an actual call. Keep the existing incoming-call regression green.

## Task 2: use the verified input for wake and commands

**Files:** modify `WakeMicrophone.java`, `WakeService.java`, `AndroidManifest.xml`, `MainActivity.java`; add `CarCommandCapture.java` if a separate owned PCM capture boundary is needed.

- [ ] Add explicit optional car setup/start using the foreground microphone service. Add only required permissions. Selecting car mode does not grant permission to use built-in input when Bluetooth is unavailable.
- [ ] Feed the unchanged Vosk wake detector from a privacy-sensitive mono PCM16, 16 kHz recorder after actual Bluetooth route confirmation. Wait for the previous recorder to release; never open competing wake and command captures.
- [ ] Establish a verifiable car-input path for Android command recognition. `RecognizerIntent.EXTRA_AUDIO_SOURCE` supports a caller-owned descriptor, but providers can ignore it and open their own microphone. Check provider support and actual behavior on the Pixel; do not claim route proof from the intent alone. If using the existing local recognizer as an alternative, establish usable names/commands before claiming support.
- [ ] Close descriptor, recorder, recognition and callbacks on every cancellation/error/timeout. Route loss ends a pending conversation. Keep a single generation across wake, command, prompt and expected replies.

## Task 3: quiet active conversations

**Files:** modify `WakeService.java` and `CarAudioSession.java`; extend `tests/check-car-voice.py` and existing controller/interruption checks.

- [ ] Add failing checks that temporary audio focus is held across prompt completion, answer readiness, clarification and final confirmation. Speech finishing alone must not resume music while a reply is expected.
- [ ] Request temporary audio focus before Sally speaks or signals readiness for a command. If focus is denied/lost, cancel rather than speak/listen over music. Measure whether the actual Spotify client pauses; a focus callback alone is insufficient audible evidence. If explicit playback control is needed, preserve prior playing/paused state and prevent stale recovery during real calls or user playback changes.
- [ ] Open an answer window only after successful prompt completion and audio drain; use a 10-second ready-to-answer timeout and a 90-second overall conversation deadline. Passive wake listening may coexist with music; active command/answer capture must be quiet.
- [ ] Release temporary focus on completion, cancel, timeout, failed speech/recognition, service stop or call handoff. Keep the car route only while its current listening policy still requires it.

## Task 4: build and parked acceptance

- [ ] Run focused car audio/call interruption/controller/wake checks after changes, then debug/release compile and lint. Preserve signing identity and Spotify preferences on an authorised private update.
- [ ] In the parked car, verify actual Bluetooth input through wake, initial command and an expected reply. Speak at normal seated volume with the phone away. Confirm Sally's prompt and readiness cue are audible.
- [ ] Confirm music is quiet for the complete conversation and recovers afterward; record the car display's behavior and music quality separately. Test wake with music and screen off for a longer observed window; do not infer all-day reliability from 30 seconds.
- [ ] Repeat Stop, route loss, service recovery and one explicitly agreed ordinary-call interruption trial. No 000 call. Independently review the completed source and update the handoff with exact evidence and remaining limits.

**Gate for calling/SMS:** The car must reliably hear a request and a finalized expected answer at normal speaking volume, with music quiet throughout the exchange.

**Primary references:** [Actual recorder routing](https://developer.android.com/reference/android/media/AudioRecord#getRoutedDevice()), [recognizer audio source and fallback behavior](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE), [audio focus](https://developer.android.com/media/optimize/audio-focus).
