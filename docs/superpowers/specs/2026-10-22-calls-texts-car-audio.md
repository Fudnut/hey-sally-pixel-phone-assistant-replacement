# Calls, SMS reading and car audio requirements

Date: 22 October 2026, Australia/Sydney. The Stage 1 diagnostic is implemented, independently reviewed and installed. Parked testing confirmed actual Bluetooth input, car-speaker output, short microphone/music coexistence, timeout/Stop cleanup and Bluetooth-loss cleanup. Car wake/command recognition, contact calling, SMS announcement/reading and new emergency-number behavior remain implementation work. See [hardware evidence](../../CAR_AUDIO_CHECK.md).

## User requirements

- Use Hey Sally to call the phone's contacts, avoiding the 2009 Mitsubishi Pajero's older uploaded-contact recognition and pronunciation.
- Use the car microphone and speakers. The phone microphone/speaker are too weak for this use. A successful speaker test alone does not satisfy this requirement.
- Include a way to request Australian emergency number 000 even though it is not a contact.
- Read SMS texts in the phone's Messages app. Automatically announce a new text, ask whether to read it, and speak the message only after an affirmative answer. WhatsApp is outside the initial scope.
- A parked car check with the phone connected to the laptop by USB is possible later, when the user has access to the car.
- Preserve the previously agreed final voice confirmation for ordinary calls, including an unambiguous contact with one number. Do not require another wake phrase for an expected reply in the same conversation.
- Pause music throughout an active Sally conversation, including prompts, command capture, clarification, confirmation and SMS readout; the user should not have to raise their voice over music. Returning music during the diagnostic's input-level check was useful for coexistence testing, but does not establish acceptable conversation behavior.

## Existing implementation

The installed Pixel 8 Pro build contains the incoming call interruption fix, committed as 1cb3af6. Steve reported that the car incoming call test worked. Wake capture still uses the phone microphone. Replies use media audio routing; there is no explicit Bluetooth microphone routing. The call monitor treats MODE_IN_COMMUNICATION as a possible call, so Sally must distinguish any future speech session it owns from a real phone or third-party call.

The current package/signing identity and Spotify settings must be preserved. Signing keys, contacts, numbers, message contents, audio and private device dumps remain outside Git. The repository is public.

## Stage 1: establish the car microphone path

Prepare a bounded diagnostic that lists communication device types, requests the car hands-free route, and inspects AudioRecord.getRoutedDevice while capture is active. Requesting a preferred device or observing a Bluetooth output is insufficient evidence of the input route. Verify spoken test audio over the car speakers separately.

The check must establish whether the car supports continuous Hey Sally capture while music is playing, only a temporary hands-free speech session, or neither. Record music interruption/restoration, input level, route timeout/disconnection, and real-call preemption. Some older systems may not support the desired combination; do not silently use the phone microphone and report success. If a car voice/phone button is required, that changes the activation requirement and needs the user's choice.

Use an explicit, foreground-only debug diagnostic with a 30-second limit. Keep samples in memory only and discard them immediately. Do not place calls or access contacts/messages during the audio check. Stop Sally's existing capture during the diagnostic and restore its prior listening state afterward, respecting real calls and microphone permission. No test places a call to 000.

The parked check found that the car briefly displayed an incoming-call indication for the speech connection; Steve reported that it disappeared when music returned. Do not mistake that display for a real call or ignore real calls because Sally owns a communication-mode request. Check longer operation, music quality and wake accuracy before enabling continuous car capture. Active conversation must keep temporary audio focus until all expected replies/readout are complete. The installed normal listener continues using its existing microphone path until car integration is built and verified.

## Stage 2: ordinary contact calls

Build on the conversation rules in PROJECT_HANDOFF.md: resolve the person, clarify the requested home/mobile/work number, speak the final target, capture a finalized yes, consume that confirmation once, and submit one call through TelecomManager. Incoming calls, cancellation, permission loss, failed prompts, timeouts, service restart and superseding requests invalidate the target.

Contact lookup is local and on demand. Similar names and duplicate labels require clarification; no uncertain fuzzy match or silent home/mobile substitution. Recheck the exact contact/number immediately before dispatch. Unsupported service codes and post-dial sequences must not be stripped into a different number. A CALL_REQUESTED diagnostic means submission only; no automatic retry or claim of a connected conversation. Configure a default calling SIM for hands-free operation.

The verified car speech path must cover the contact request, clarification and confirmation, not just the connected call. The Phone app owns actual call audio. Sally releases recognition, TTS and any speech route before handing over to Telecom.

## Emergency number 000

Recognize an explicit request for 000 independently of contact lookup and preserve its leading zeros. Android allows direct emergency placement through TelecomManager.placeCall only for the default or preloaded dialer. Sally is currently an assistant, not a replacement Phone app.

With the existing Phone app retained, a supported first implementation can open tel:000 using ACTION_DIAL and explain that the system dialler still needs confirmation. This does not satisfy fully hands-free emergency calling. Making Sally a complete default dialer, including InCallService and incoming/outgoing call UI, is a separate product decision; do not change that role silently or claim it is unnecessary. Emergency behavior is verified with dispatch doubles, never a real 000 call.

## Stage 3: incoming SMS announcement and reading

Use separate optional setup for message access; denial must not disable Spotify or ordinary calls. Android's READ_SMS/RECEIVE_SMS access and installer restrictions must be checked on the actual Pixel. The current Messages app may also contain RCS/MMS; choosing SMS does not imply those are readable through the SMS provider.

On a new SMS, announce that a text arrived and ask whether to read it. Open the answer window only after the prompt finishes successfully. Accept a clear finalized yes for the current prompt; no partial recognition, contradictory lower-ranked alternative, stale yes or duplicate callback may disclose a body. No/cancel/timeout keeps the content unspoken. Do not interrupt an active call; queue the announcement for a suitable idle state. Bound queued messages and read one selected message at a time.

Retain only the minimum pending message identity in memory, retrieve the intended body on approval, and do not log/store bodies or sender identities in app diagnostics. Do not mark or delete messages automatically. Resume wake listening and restore the audio route after readout, cancellation, failure or a real call interruption.

## Evidence required before claiming completion

1. Verified car input device while capture is active, plus audible car output and a user check that speech is coming from the car microphone.
2. Car wake/activation feasibility with music, including the effect on music and bounded recovery if the hands-free route cannot open.
3. Mocked contact/SMS conversation tests for ambiguity, strict confirmation, permissions, deadlines, duplicate/stale callbacks, service restart and incoming call interruption.
4. A real ordinary-call trial with an explicitly agreed recipient: confirm both directions of car audio and post-hang-up recovery. A separate test SMS validates announce/approval/read/cancel behavior. No emergency call is part of testing.

## Primary references

- [BluetoothHeadset voice recognition and audio connection](https://developer.android.com/reference/android/bluetooth/BluetoothHeadset#startVoiceRecognition(android.bluetooth.BluetoothDevice))
- [AudioRecord actual input routing](https://developer.android.com/reference/android/media/AudioRecord#getRoutedDevice())
- [TelecomManager call placement and emergency restriction](https://developer.android.com/reference/android/telecom/TelecomManager#placeCall(android.net.Uri,%20android.os.Bundle))
- [Default Phone application requirements](https://developer.android.com/develop/connectivity/telecom/dialer-app)
- [SMS permission restrictions](https://developer.android.com/reference/android/Manifest.permission#READ_SMS)
