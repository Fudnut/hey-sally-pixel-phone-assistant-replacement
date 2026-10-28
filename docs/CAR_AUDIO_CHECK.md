# Parked Pajero microphone check

Checked on 22 October 2026 with Steve's Pixel 8 Pro connected to the parked 2009 Pajero. Bluetooth microphone capture and audible car-speaker output worked during the bounded diagnostic. Wake recognition and command recognition through that path have not yet been implemented or tested.

The reviewed diagnostic is installed on Steve's Pixel 8 Pro using the original signing key. Spotify settings were preserved, its idle screen was checked without starting test capture, and the normal Sally listener was restored with LISTENING_READY observed. The installed APK matched the signed artifact. Local debug/release builds and lint passed with warnings; the release APK excludes the diagnostic Activity, classes and added permission.

## Quiet conversation follow-up — 22 October 2026

The installed diagnostic now retains temporary audio focus until the whole 30-second session ends. It no longer releases focus at the end of the test phrase. The updated phrase and screen ask for normal-volume speech with music kept quiet.

- Steve confirmed Spotify was quiet during Sally's spoken phrase, stayed quiet throughout his speaking turn, and resumed automatically after the check ended.
- Device snapshots agreed: Spotify was `PAUSED(2)` during the phrase and during the reply-capture phase. Both snapshots showed Sally actively capturing `AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET`. The reply screenshot showed a live input level of 15%.
- After the deadline, Spotify was `PLAYING(3)`, Sally's communication mode was released and audio mode was normal. The diagnostic's Bluetooth capture was closed and normal Sally listening was restored.
- Focus-retention and cleanup checks passed, debug/release builds and lint passed, and independent review found no actionable important defects in this follow-up. The original signer and Spotify settings were preserved during the private update.

This proves the requested sound behavior in the bounded parked diagnostic. It does not yet prove recognition of a request/answer, contact calling or SMS reading through the car microphone. Production conversation integration remains in the next plan.

## Initial routing results — 22 October 2026

- Android's active audio-policy input was `AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET`, with Sally's active `VOICE_COMMUNICATION` recording client at mono PCM16, 16 kHz. The ready state also requires the recorder's actual routed device to be Bluetooth.
- Steve confirmed Sally's phrase came from the car speakers and reported input levels of 1–13% while speaking. This is an input-level observation, not a speech-recognition accuracy score.
- On the repeat check, Steve confirmed music returned while the microphone meter was still active. Spotify held audio focus after the phrase, with Bluetooth recording still active. This proves short simultaneous operation; music fidelity, long-running wake capture and wake accuracy remain unmeasured.
- The 30-second deadline and explicit Stop control both closed the diagnostic. Audio mode returned to normal, and the foreground Sally listener restarted.
- A controlled Bluetooth-off check, performed through USB after confirming active Bluetooth recording, produced **Bluetooth microphone route was lost. Check stopped.** Audio mode returned to normal. Bluetooth was re-enabled and the car reconnected.
- The car displayed **incoming call** when the speaker test opened its speech path. Steve confirmed that display disappeared when music returned. The diagnostic contains no call-placement action; subsequent phone call-state checks were idle. This display appears to be the car's presentation of the speech connection, rather than an actual incoming mobile call. Android reported a virtual SCO connection during the check. Retain this observation when designing prompts and activation.
- No real ordinary or emergency call was placed. Actual-call preemption during this diagnostic remains untested on the car.

Steve reported that speaking over resumed music required a raised voice. The first diagnostic version deliberately released temporary audio focus at the end of the speaker phrase to test music coexistence. The quiet conversation follow-up above corrected that lifetime for the requested follow-up test and confirmed audible silence in both phases. The product must retain temporary audio priority throughout an active conversation: prompt, command/answer capture, clarification, final confirmation and readout. Passive wake listening and active conversation need separate music policies.

Detailed snapshots and the aggregate result record are under ignored `.tools/car-audio-check/`. No captured audio, contacts, numbers, message contents or private device dumps belong in Git.

## Repeating the check

1. Park the car, switch on its hands-free system and connect the Pixel 8 Pro through the normal Bluetooth pairing.
2. Bring this laptop, open this Sally project, connect the phone to the laptop with a USB data cable, and unlock the phone. Approve USB debugging if asked.
3. Tell this chat that the car and phone are ready. I can inspect the routes and open **Sally car microphone check**, the extra launcher in the debug build.
4. Tap **Start 30-second check**. Opening the screen alone does not capture audio. If permissions are requested, grant microphone and Phone access, then tap Start again. Sally's normal listener is suspended only for the explicit check.
5. Wait for **Bluetooth microphone input confirmed by Android** and the spoken speaker-test phrase. Before confirmation, a requested output route is not proof that the car mic is active. If no single hands-free route is available, connect the car and disconnect other Bluetooth headsets, then retry.
6. Confirm the phrase is audible through the car speakers with music quiet, then speak normally and observe the input meter. Music should remain quiet throughout your turn while the car microphone stays open for the remainder of the original 30 seconds. Check that music returns after the session ends.
7. Use **Stop check** to finish early. Leaving the screen or reaching the deadline also closes capture and releases the diagnostic route. A previously running Sally listener is requested again; one that was already stopped is not started. Returning to Sally provides the normal start control if Android prevents automatic restoration.

If Bluetooth input is never confirmed during the session, the screen reports **failed to connect**, rather than treating the 30-second timeout as a completed microphone check.

## What the check needs to prove

- Sally's test capture reports a Bluetooth hands-free input, rather than the built-in phone microphone.
- The spoken test phrase is audible through the car speakers.
- Normal seated speech is captured when the phone is positioned away from the speaker, with a controlled comparison near each microphone if needed.
- Whether opening the car mic pauses/replaces music, and whether media returns normally when that route closes.
- Whether Hey Sally can remain active through the car mic while music plays, or whether the car only permits a temporary speech session.
- Stopping, timing out, disconnecting Bluetooth and receiving a real call release diagnostic capture and leave normal calling/listening usable.

No contacts or message contents are needed for this stage. Diagnostic samples are discarded without saving audio. Any detailed device dumps stay under ignored .tools/car-audio-check/ and are not uploaded to the public repository. No diagnostic places an ordinary or emergency call.

The diagnostic Activity/classes are absent from release builds. The new optional main-app car input also needs MODIFY_AUDIO_SETTINGS, so that normal permission is now present in both build types. Bluetooth input confirmation alone does not establish wake/command accuracy, contact calling or SMS reading; main-app acceptance is recorded in [the car voice trial](CAR_VOICE_TRIAL.md).

## Local checks

```powershell
.\tests\check-car-audio.ps1
python -X utf8 .\tests\check-car-audio-android.py
```

These checks exercise the real debug session, Activity and Android adapter with deterministic platform boundaries. Full debug/release compilation and lint additionally check Android integration and release separation; physical microphone/speaker results require the parked test.

## Current decision

Proceed with car voice integration using the verified Bluetooth capture path. Short microphone/music coexistence is supported by this check; always-on wake reliability is still a separate acceptance gate. Keep music paused during the entire active Sally conversation, then release Sally's temporary focus when finished. Preserve real-call priority and avoid an explicit Spotify resume after a real call.

The next work is [car voice integration](superpowers/plans/2026-10-22-car-voice-integration.md), then separate [contact calling](superpowers/plans/2026-10-22-contact-calling.md) and [SMS reading](superpowers/plans/2026-10-22-sms-reading.md). The full requirements are in [the calls/texts design](superpowers/specs/2026-10-22-calls-texts-car-audio.md); the completed diagnostic implementation is described in [the first-stage plan](superpowers/plans/2026-10-22-car-audio-check.md).
