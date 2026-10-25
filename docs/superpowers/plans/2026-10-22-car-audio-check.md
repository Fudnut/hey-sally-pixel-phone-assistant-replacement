# Car Audio Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish whether the 2009 Pajero exposes a usable microphone/speaker path for Sally before implementing contact calls and SMS reading around it.

**Architecture:** Add a debug-only, foreground diagnostic with explicit start/stop controls and a 30-second limit. Separate a testable session controller from Android routing/capture so actual device routing, cleanup and call preemption are observable. Keep the working phone-microphone path and call interruption fix available after the check.

**Tech Stack:** Existing Java 17 Android app, minSdk 34, compile/target SDK 37; AudioManager communication devices, AudioRecord routing, TextToSpeech, existing standalone Java regression checks.

**Spec:** docs/superpowers/specs/2026-10-22-calls-texts-car-audio.md, Stage 1.

## Global Constraints

- Use the car microphone and speakers. A successful speaker test alone does not satisfy this requirement.
- Use an explicit, foreground-only debug diagnostic with a 30-second limit.
- Keep samples in memory only and discard them immediately.
- Do not place calls or access contacts/messages during the audio check.
- Signing keys, contacts, numbers, message contents, audio and private device dumps remain outside Git.
- Preserve the package/signing identity, Spotify settings and incoming call interruption behavior.

## Review Focus

- Route selection succeeds but capture still uses the phone: session may report ready only for actual Bluetooth input.
- Car microphone connection stalls or Bluetooth disconnects: release resources within the session deadline and report failure.
- An incoming call arrives during the probe: close capture/TTS and yield to Phone without forcing call routing back to media.
- Activity leaves the foreground: stop capture and remove timers/listeners; restarting must require another explicit start.
- The car's speech session prevents Spotify playback: report the observed limitation before choosing the product's activation design.

## Task 1: bounded diagnostic and local regression

**Files:**
- Create app/src/debug/java/com/steve/spotifywakeprobe/CarAudioProbeActivity.java: explicit start/stop screen, transient status and input level only.
- Create app/src/debug/java/com/steve/spotifywakeprobe/CarAudioProbe.java: session controller and Android boundary interface.
- Create app/src/debug/java/com/steve/spotifywakeprobe/AndroidCarAudioProbe.java: route request, actual AudioRecord route, disposable sample level, TTS and cleanup.
- Create app/src/debug/AndroidManifest.xml: diagnostic launcher and MODIFY_AUDIO_SETTINGS permission in debug builds only.
- Create tests/CarAudioProbeCheck.java and tests/check-car-audio.ps1: real session behavior with deterministic boundary doubles.
- Update docs/CAR_AUDIO_CHECK.md with the built probe's actual controls and limits.

**Interfaces:**
- CarAudioProbe.start(), stop(), onRouteChanged(boolean bluetoothInput), onCallChanged(boolean active), onDeadline().
- CarAudioProbe.Platform: requestCarRoute(), beginCapture(), speakTestPhrase(), releaseOwnedResources(), report(String category). Production Android operations remain behind this interface.
- AndroidCarAudioProbe must verify AudioRecord.getRoutedDevice() during active recording; no device names, addresses, speech or contact/message data enter persistent status.
- The Activity suspends Sally's existing capture for the explicit probe and restores prior listening only after probe cleanup, with microphone permission and call state checked again.

- [x] Write behavior tests with hand-derived expected outcomes: accepted preferred route plus built-in actual input never reports ready; actual Bluetooth input reports ready; timeout/disconnect/call/stop each release owned resources once; repeated callbacks cannot restart or extend a stopped session; the 30-second deadline is not extended by route callbacks.
- [x] Run .\tests\check-car-audio.ps1 and confirm the intended missing behavior fails before implementation.
- [x] Implement the controller, Android boundary and foreground Activity. No silent phone-microphone fallback. Only the explicit Start button opens a session. Use MODE_IN_COMMUNICATION and a Bluetooth communication device when available; leave calls under Telecom's control.
- [x] Add lifecycle boundary coverage for foreground exit, microphone permission denial and restoration of Sally's prior listening state. Keep the existing call monitor from confusing the diagnostic's own communication mode with a live phone call by suspending the normal listener for this isolated check.
- [x] Run the new check and existing check-playlists.ps1, check-controller.py and check-call-interruption.py; build/lint the debug app. Verify the diagnostic Activity and added permission are absent from a release build.
- [x] Review the diff, sign a local debug artifact with the existing private key, and verify its public certificate matches the installed app. Install only as a compatible update with app data preserved.
- [x] Keep source/tests/docs local until a commit is directed; key, APK and device dumps remain excluded.

Task 1 completed locally on 22 October 2026. Independent review found one timeout-result issue; a failing never-ready regression reproduced it before the fix. Both controller and real Activity/Android adapter tests now cover explicit connection failure. The installed inactive screen was checked and normal Sally listening restored. The actual car-microphone session has not been started.

## Task 2: parked hardware evidence and next-stage decision

**Files:** update docs/CAR_AUDIO_CHECK.md with aggregate results and user observations. Keep detailed route dumps under ignored .tools/car-audio-check/.

**Consumes:** the bounded debug diagnostic from Task 1; phone/car/laptop arranged by the user later.

**Produces:** a documented decision on continuous car-microphone wake feasibility, temporary speech sessions, or unsupported hardware. This is the prerequisite for separate contact-calling and SMS-reading implementation plans.

- [x] Inspect available communication devices while the paired car is connected and the phone is USB-authorised.
- [x] Run the bounded microphone/speaker check while parked; verify actual Bluetooth input and audible output with the user.
- [x] Check the effect of the hands-free route on music and recovery. Exercise explicit stop, timeout and Bluetooth disconnect. Record real-call preemption only with an agreed ordinary call; never dial 000 as a test. Outcome: music returned while the meter remained active; all three cleanup paths passed. No actual call trial was performed.
- [x] Record supported behavior and remaining gaps without claiming callbacks prove audible results. If continuous wake through the car mic is unavailable, ask the user about activation by a car button or other hardware before building around a different input method. Outcome: short simultaneous capture/music works; longer wake accuracy remains unverified. No alternative activation method selected.
- [x] Prepare separate contact-calling and SMS-announcement/readout plans using the verified routing result and the selected execution approach. Both depend on the new car-voice integration plan and retain inline implementation with independent final review.

## Self-review

The diagnostic covers all five review-focus cases locally or in the parked check. Contact calls and SMS reading remain the intended next features; this plan implements their shared hardware prerequisite only. Direct emergency calling is not included; its default-dialer requirement remains an explicit decision in the spec. Parked routing, user-audible output, short music coexistence and cleanup now have evidence. Actual-call preemption on this route, long-running wake recognition and natural-volume command recognition remain acceptance gates for the next implementation.
