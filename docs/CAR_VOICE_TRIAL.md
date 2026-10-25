# Main-app car voice trial — 22 October 2026

## Implemented for the private trial

- Optional **Use car microphone (tap Start to apply)** setting on the main screen. Tap **Grant permissions and start probe** after selecting it. Without the setting, the existing phone-input behavior remains available.
- Car mode requests one connected Bluetooth hands-free device, waits up to ten seconds for actual Bluetooth microphone input, and stops on unavailable/ambiguous input. It does not silently fall back to the phone microphone.
- Exact Hey Sally wake grammar is unchanged. Both wake and car commands use caller-owned Bluetooth PCM. Initial car commands use the existing local English Vosk model, so Android's recognizer cannot silently substitute its own microphone. Contact-name accuracy and non-English car commands are not established.
- Active commands/replies take temporary audio priority. Ordinary music playback receives an intentional focus handoff after capture completes. Real phone calls suspend Sally, and normal recovery does not explicitly resume Spotify.
- Public Android APIs cannot guarantee detection of an arbitrary non-Telecom VoIP app taking the same mode and same device without an observable focus or capture change. Supported interruption evidence covers Telecom/phone state and observable mode, route, focus and capture preemption.

## Parked trial

1. Keep the Pajero parked with the Pixel paired, and start Spotify at a normal listening volume.
2. Select car microphone on Sally's main screen and tap the normal start control. Wait for listener readiness; no further screen interaction is needed during capture.
3. With the phone away from your mouth, say **Hey Sally** at normal seated volume. Confirm the readiness cue is audible through the car.
4. After the cue, say **pause**. Verify music stays quiet while speaking and the requested pause takes effect. Repeat the wake and say **resume** or **next**; confirm the requested player action and continued wake readiness.
5. Verify actual Bluetooth input during wake and command capture, screen-off wake behavior, longer operation, music quality, Bluetooth loss and recovery. An ordinary real-call trial needs an explicitly agreed recipient. Never dial 000 for testing.

## Current evidence

Code checks, complete existing regressions, debug/release compile and lint passed, including after the physical-recorder handover correction. Independent source review's two important focus-ordering findings were fixed with failing-then-passing regressions. The prior bounded diagnostic established car input/output and quiet speech/reply capture.

In the main app, the initial wake-to-command handover lost the communication mode. Retaining one physical Bluetooth recorder across wake, command and prompt phases corrected that observed failure. A subsequent parked repeat accepted Hey Sally, signalled readiness through the car and completed PAUSE; Steve confirmed Spotify paused. NEXT subsequently completed and Steve confirmed the track changed. The same Bluetooth recorder session remained active across these phases. This establishes these basic commands, not contact-name accuracy or finalized conversational answers.

**Stereo acceptance failed with continuous car capture.** Steve reported music through the car's telephone/mono channel (not the handset speaker). Android selected SCO for media while that mode was active. Sally was force-stopped; Android then selected A2DP and Steve confirmed normal stereo returned. He corrected an accidentally selected negative answer. No Bluetooth reset was needed. Sally remains stopped, with the car-input setting still selected; starting it again would repeat the continuous-capture trial.

The next design decision is activation between conversations. Keep stereo between sessions by releasing the hands-free route. Do not claim continuous car-microphone wake plus stereo playback is supported by this trial. Screen-off wake, longer operation, expected-answer recognition and actual call handoff remain unaccepted.

### Car voice-button check

With Sally stopped, the Pixel unlocked and no mobile call active, Steve pressed the Pajero's Speak button once. He reported only the car's own voice menu. The Pixel remained on its home screen; retained focused logs showed no remote headset voice-recognition request or Android VOICE_COMMAND launch. Android's log buffer contained the observation-end marker, validating access to that buffer. Headset recognition/virtual-call flags were idle afterward, audio mode was normal, and SCO was off. Default-assistant settings were not changed, and Sally remained stopped.

This single press did not establish a phone-assistant activation path. Missing production logs do not prove every possible car gesture unsupported. The exact-era official Pajero manual was located but its download failed; no alternate button gesture was verified. Sally's existing assistant declaration also returns an empty voice session, so a future supported button path would need an implemented bounded entry point.

Steve was offered three explicit activation choices: phone microphone for Hey Sally followed by car microphone/speakers for the conversation; a manual Sally start for each conversation; or continuous car capture accepting mono music. No change to wake-input policy has been made while that choice is pending.

### Repeat continuous-car trial

Steve initially selected phone-only wake, then withdrew that choice. He subsequently chose to try continuous car wake (option 3), switching to phone-only wake with car conversation (option 1) if unsatisfactory. Driving operation must require no phone touches. No hybrid-input code has been implemented yet.

The existing installed continuous mode was restarted. Actual Bluetooth input and listener readiness were reported at 16:42 local; the phone screen was then turned off. Steve confirmed music was mono, as expected for this mode. **He confirmed screen-off NEXT changed the track and music stayed quiet while he spoke.** Diagnostic categories agreed with completed NEXT at approximately 16:43.

Steve then explicitly chose **option 1: phone wake followed by car conversation**. Continuous car mode was force-stopped. Hybrid input has not been implemented; the installed APK would still reopen continuous capture if started under its saved car preference. See `CHECKPOINT_2026-10-22.md` for the next implementation slice and exact resume state.

Contacts, SMS announcement/reading and emergency-number behavior remain subsequent work. Preserve signing identity/settings and keep all device dumps, audio, keys, contacts and message contents outside Git.
