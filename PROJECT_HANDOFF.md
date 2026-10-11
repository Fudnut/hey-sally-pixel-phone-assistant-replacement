# Public project handoff

Updated 11 October 2026 (Australia/Sydney).

## Contact calling planning checkpoint - 11 October 2026

**Status: design and plan review only. No calling code, permissions, phone installation, or real calls have been performed.** Steve requested the feature, a plan, a conflict review, and this checkpoint. Implementation and device trials are the next objective, not authorized by this checkpoint request alone. Ponytail remains off.

### Agreed requirements

- Add outgoing ordinary phone calls using the phone's contact list, hands-free after setup, with the existing exact wake phrase **Hey Sally**. Steve explicitly chose **always confirm by voice**, even for one contact with one number.
- Contacts can have home and mobile numbers. Resolve the person first, then the requested number. Example: wake -> `Call John Smith` -> `Home or mobile?` -> `Mobile` -> `Call John Smith, mobile?` -> `Yes` -> submit the call. Replies within this conversation should not require another wake phrase.
- `Call John Smith on mobile` skips number selection, but never skips final confirmation. Multiple people or multiple numbers with the same label require clarification. No uncertain match or silent substitution of home for mobile.
- Preserve Spotify behavior, package/signing identity, wake implementation, settings and private diagnostics. Calling setup is optional; declining its permissions must not disable Spotify.

### Proposed implementation and limits

- Use Android Contacts Provider for on-demand local name/number lookup and TelecomManager.placeCall() for normal calls through the existing Phone app. Proposed runtime permissions: READ_CONTACTS, CALL_PHONE, READ_PHONE_STATE. No contact upload, new paid service, call-log permission or replacement dialer is proposed. Existing recognition/TTS providers may process spoken names online; document that distinction.
- Add contact lookup/matching, a small explicit calling-conversation state machine, and Android call dispatch/state handling. Integrate through VoiceCommand.java, WakeService.java, MainActivity.java and AndroidManifest.xml under app/src/main/. Keep matching and conversation rules independently testable; do not reuse fuzzy Spotify matching for call targets.
- Conversation stages: resolve person -> resolve number -> speak final confirmation -> capture final answer -> consume confirmation once -> submit. Each prompt must finish successfully before opening its answer window; allow audio to drain and start the approximately 10-second answer timeout when recognition is ready. Add an overall conversation deadline during detailed design.
- No, cancel, timeout, recognition failure, failed/interrupted TTS, incoming call, permission loss, service stop/restart or superseding request must clear pending confirmation. Keep pending contact data in memory only. A later yes must never revive an expired request.
- Support home/mobile/work and custom or missing labels. Deduplicate equivalent numbers within a person without merging distinct people who share a number. Use numbered choices and distinguishing last digits where labels are insufficient. Before dispatch, recheck permissions and the exact selected contact/number; changes require a new confirmation.
- Initial scope excludes answering/hanging up by voice, messaging, WhatsApp/VoIP calling, raw spoken-number dialing, emergency numbers, service/MMI codes and unsupported extension/post-dial sequences. Do not strip unsupported characters into a different dialable number. Cancellation prevents submission only; it cannot promise to hang up an already submitted call.

### Review findings that must reach implementation and tests

1. WakeService.finishReply() currently returns to wake listening. Calling needs explicit continuation into the expected answer stage, separate from normal music parsing and playlist selection.
2. WakeService.fail(), delayed reply callbacks, startup and other listen() paths can reopen the microphone. All must respect shared pending/active-call state. Incoming calls cancel the conversation; recognition and TTS remain suspended during calls. Reconcile actual call state on service recovery before listening again.
3. VoiceCommand.choose() searches recognition alternatives, and some ordinary commands accept partial speech. Confirmation needs a separate strict parser: only a clear finalized affirmative for the current prompt; never partial fallback or a lower-ranked yes contradicting the primary result. Duplicate callbacks must submit at most once.
4. placeCall() is a submission API, not proof of connection. Log CALL_REQUESTED, not connected/successful conversation. No automatic redial after uncertain submission. Define bounded recovery for submission failure/no call-state transition without risking a duplicate call.
5. TelephonyCallback.CallStateListener observes its registered subscription. Dual-SIM needs monitoring across active subscriptions or an explicit initial single-active-SIM support boundary. A default calling SIM must be configured for hands-free operation; an unresolved system SIM picker is a blocker, not a successful call. Do not claim mobile-call callbacks cover every third-party calling app.
6. Existing Bluetooth music/TTS observations do not prove telephone audio. Test both directions of car call audio, prompt/beep audibility, incoming interruption, full-call microphone suspension and post-hang-up wake recovery. Restore listening without explicitly sending Spotify Resume.
7. Confirmation is not speaker authentication. Anyone whose speech is accepted may initiate a locked-screen call; background speech remains a risk. Do not add contact names/numbers to logs, notifications, error text, persistent diagnostics or recognizer hint lists. Spoken selection/confirmation necessarily discloses the chosen name to nearby listeners.

### Next steps and verification

1. Complete the concrete design for call-state recovery, timeouts and initial SIM support; obtain implementation direction. Keep this task's deliverable as a checkpoint.
2. First implementation slice: prove voice-confirmed calling with one agreed test contact on the locked Pixel, including car audio and recovery. Agree the actual test recipient before placing any real call; use mocks until then. Preserve the installed signing identity and data, and arrange installation authorization for this new feature trial.
3. Build/test the conversation and interruption rules, then contact/number selection, then integrate and run existing Spotify/wake regression checks. Include ambiguous/duplicate contacts, number edits, permission denial/revocation, stale/duplicate yes, TTS failure, recognizer errors, no network/service, SIM selection, incoming calls and service restart.
4. Checkpoint each verified slice with task-only commits and non-force pushes. Update README/INSTALL and this handoff with actual results and limitations; public release and broad reliability claims are separate work.

Checkpoint validation: tests/check-playlists.ps1 passed (command/parser/language/playlist checks, wake grammar and wake detector); git diff --check passed. No APK build, installation or call test was run for this documentation-only checkpoint.

Planning evidence: inspected local source at 9a89731 and Android documentation for ContactsContract.Phone, TelecomManager.placeCall/isInCall and TelephonyCallback.CallStateListener. No device behavior was verified during planning. An existing unrelated README.md edit was present when this checkpoint began and must be preserved.

References: [TelecomManager](https://developer.android.com/reference/android/telecom/TelecomManager), [call-state subscriptions](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener), [contact phone fields](https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone).

## Current state

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement ; branch `main`.
- Limited-test release: 0.2.1-alpha.5, versionCode 7, ARM64, Android 14+, target API 37. Read README.md, INSTALL.md and TESTING_AND_DISTRIBUTION.md for current setup, commands, privacy and testing.
- Licence: GPL-3.0-only with the limited independent-library permission in LINKING_EXCEPTION.md. Complete project source/build scripts accompany the APK; third-party licences and copyright attribution remain intact. SOURCE_BUILD.md and THIRD_PARTY_NOTICES.md describe source/input provenance.
- Package `com.steve.spotifywakeprobe`, launcher label Spotify Wake Probe, exact wake Hey Sally, public certificate and existing signing inputs are fixed compatibility boundaries. Do not relabel, move/copy keys, expose credentials or rewrite Git history. Pixel updates are authorized only for the current in-place private wake test; preserve its signing identity and app data. Release maintenance does not authorize installation changes. Ponytail is off.

## Wake status - 10 October 2026

Commit 80c0bf9 replaces the candidate-plus-verifier design with one closed-vocabulary decoder: grammar `hey sally`, `[unk]` and about 255 common decoy words (WakeGrammar), exact finalized-only matching, a one-result hold for a Hey split across endpoints, and the unchanged five-second debounce. The verifier, its audio replay buffer and the verifier probe fields are removed. The earlier verifier builds (498f1c2, 44f93f3) rejected false wakes but missed many intentional ones live: a probe run showed the candidate stage firing while the verifier never produced a matching Hey. Evidence is in the ignored claude-audit/REVIEW_WAKE_2026-10-10.md (addenda 1-5) and CODEX_REPORT_decoy-grammar.md.

Pixel results (private in-place debug install, same certificate, app data kept; Steve's voice only, built-in microphone, input type 15 throughout): wakes accepted at about 40 cm (6/6 on the experimental predecessor), about 1.5 m (4 accepted, attempts not counted), in a car (5 accepted), with music playing (5/6), in a pocket with the screen off (5/6) and with a Bluetooth device connected (5/6). No false wakes in a five-minute video-call speech window (77 rejected results, quiet playback), about twelve minutes of loud car audio, or a 35-minute quiet-room battery run (all summaries other=0-1). Known limit, from Steve: with music from the phone's own speaker near full volume, wake failed at 600-700 mm and worked at 200 mm; at 50% volume or lower it worked from a metre or more (the mic clipped at full volume, peak 32,544). Untried idea: VOICE_COMMUNICATION capture for echo cancellation, which needs a measured A/B first. Battery (one 35-minute screen-off run on battery, 18:58-19:33 local): charge counter 4812 to 4736 mAh (-76 mAh, about 130 mA for the whole phone, no idle-phone control; level stayed 100% throughout); app process CPU 8m10s over 35m05s, about 23.5% of one core (UID total with wake-lock attribution up to 42%); microphone wakelock held 34m21s. If it continued that is very roughly 60% of the battery per 24 hours. Command window after each wake is about 5.4 s. Not tested: other voices and accents, long idle, a Bluetooth microphone (the app does not use one). The decoy build is released as 0.2.1-alpha.5 (versionCode 7) from the commit embedded in its APK; that exact public APK has not been installed on a phone, and the trials above used a private debug build of the same wake source.

## Wake false-activation correction

Ordinary normal-volume video-session speech triggered the original wake-only grammar, which can coerce unrelated words into an exact Hey Sally; raising confidence alone does not fix it. The current source gives unrelated speech other words to land on (closed decoy vocabulary) instead of verifying afterwards. Exact finalized-only acceptance and the five-second debounce are unchanged. See tests/WAKE_CHECKS.md for the actual-pipeline acoustic checks, the gain/noise matrix and runtime qualifications.

Desktop synthetic checks (Vosk 0.3.45; four intentional and 42 unrelated synthetic clips) detect 4/4 and reject 42/42 at clean, 20 dB, 10 dB and quiet plus 10 dB; at 5 dB one unrelated clip wakes. Steve explicitly authorized same-certificate private Pixel updates for testing, preserving app data and signing inputs. Earlier trials: continuous full decoding averaged 32.5% of one CPU core over 75 seconds and on-demand verification 20.9% over 101 seconds, and both missed intentional live wakes. The decoy-grammar source is in 0.2.1-alpha.5.

## Implementation and verification

Wake recognition is local Vosk with privacy-sensitive microphone capture. Android's configured recognizer captures one command after the beep. Vosk command capture is used only when no system recognition service is available; complete offline commands are not guaranteed. Spotify App Remote controls playback; Web API resolves named music/saved playlists. Replies use media audio routing; the app does not manage Bluetooth pairing or use a car/headset microphone.

Supported commands include basic controls, named song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ, Daily Mix and bounded Radio destinations (Local Files is not a command; use a normal playlist). Artist requests play one matching search track; saved names can match fuzzily. Availability depends on Spotify/account. Missing destinations produce a generic spoken failure. Own Spotify Developer Client ID and both authorization paths are required.

Numeric filler and bounded recognition alternatives preserve playlist intent without overriding genuine number-prefixed titles. Local OAuth setup errors retain fixed details/HTTP status without provider messages. Ordinary operations cancel on accepted wake/new command/service stop, guard late player/media-key effects and have a 30-second completion deadline; connection/consent limits are 12/120 seconds. Name comparisons preserve Unicode, fold accents and reject empty matches. Microphone permission is checked at capture creation. Diagnostics store categories/counts/timing, never recognized words or audio.

Run ./tests/check-playlists.ps1 before each commit. Relevant focused checks are tests/check-controller.py (Python 3, ANDROID_HOME) and the local token HTTP cleanup harness. Build/sign the release from committed source; verify embedded commit, unchanged certificate, package/version/non-debuggable/ABI, pinned SDK/model, all licence assets, 16KB ZIP/ELF alignment and checksums. The source ZIP must match every tracked file and contain build instructions/provenance. Publish only verified assets and matching source tag. Use the installed SDK/dependency cache only with an explicit cache qualification; no fresh-cache or whole-APK byte reproducibility claim is established.

The project source ZIP has been independently rebuilt using pinned SDK/model inputs and the existing cache. Compiled classes, manifest and native libraries were verified against the signed build. This does not prove audible device operation.

## Outstanding tests and limitations

The exact public APK has not received a fresh phone installation/registration or audible locked-screen test. Short Pixel/Bluetooth observations do not establish a success rate, battery-use figure or all-day reliability. Permission grants/denials, speech failure/recovery, cold Spotify start, beep routing, locked-screen/overnight survival, reboot and call/camera/recorder coexistence remain device tests. The current private Pixel wake trial is separate from the public release and was explicitly authorized; release maintenance itself did not change the installation.

Preserve Claude's README banner, Bluetooth observations, scope and unbuilt roadmap. Broader assistant functions are not implemented. Setup controls can overlap system bars; scroll them into view. The Spotify Wake Probe naming issue remains deferred under the unchanged-identity instruction and must not be described as cleared compliance.

Private audit evidence is in ignored claude-audit/HANDOVER_TO_CODEX.md, TRACKER.md, LOG.md and CODEX_FULL_REVIEW_2026-10-10.md. Keep it outside public Git. Verify current source/remote state before work, preserve unrelated edits, stage only task files, checkpoint meaningful changes and push without force. No paid services or Pixel installation changes are authorized by documentation/release maintenance.
