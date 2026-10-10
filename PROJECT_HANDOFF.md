# Public project handoff

Updated 10 October 2026 (Australia/Sydney).

## Current state

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement ; branch `main`.
- Limited-test release: 0.2.1-alpha.4, versionCode 6, ARM64, Android 14+, target API 37. Read README.md, INSTALL.md and TESTING_AND_DISTRIBUTION.md for current setup, commands, privacy and testing.
- Licence: GPL-3.0-only with the limited independent-library permission in LINKING_EXCEPTION.md. Complete project source/build scripts accompany the APK; third-party licences and copyright attribution remain intact. SOURCE_BUILD.md and THIRD_PARTY_NOTICES.md describe source/input provenance.
- Package `com.steve.spotifywakeprobe`, launcher label Spotify Wake Probe, exact wake Hey Sally, public certificate and existing signing inputs are fixed compatibility boundaries. Do not relabel, move/copy keys, expose credentials or rewrite Git history. Pixel updates are authorized only for the current in-place private wake test; preserve its signing identity and app data. Release maintenance does not authorize installation changes. Ponytail is off.

## Stop checkpoint for Claude - 10 October 2026

Steve requested stopping the wake/battery investigation and handing it to Claude. Read the local ignored claude-audit/HANDOVER_TO_CLAUDE.md first for precise evidence, artifacts and safe continuation. The Pixel remains on the private debug candidate from 44f93f3; the checkpoint saves prepared debug-only aggregate microphone/stage probes, which have not been installed. No further phone tests, updates or cleanup occurred after the stop request.

Intentional live wake still fails at approximately 40 cm, with Zoom both running and paused. A known synthetic computer wake also produced no reported phone beep; Steve confirmed it played through Bluetooth-connected room speakers. The phone's Bluetooth/input/beep route is not established by that clarification. Isolated replay on the Pixel's actual Android native runtime passes four intentional and 42 unrelated synthetic samples, and cached model bytes match the APK; live microphone/service behavior remains unvalidated.

Process CPU averaged 32.46% of one core over 75.38 seconds for continuous full decoding and 20.92% over 100.99 seconds for the on-demand variant. These are uncontrolled, same-process counter intervals on a USB-charging phone, not battery-drain measurements. A permission-change restart invalidated the first optimized interval; it is excluded. Recommendation: no replacement release until intentional live wake is restored and background/locked-screen/battery trials pass. The published APK and release/tag remain unchanged.

## Wake false-activation correction

Ordinary normal-volume video-session speech was reported to trigger the listener. The wake-only grammar can coerce unrelated words into an exact Hey Sally; raising confidence alone does not fix it. Current source uses that cheap decoder only to propose candidates, then verifies the bounded segment with the existing model's full vocabulary. Full decoding runs only for candidates, with at most ten seconds of audio held in RAM and cleared after a completed/rejected segment or close. One finalized Hey result can be held through the next endpoint to verify a phrase split by native silence endpointing; native feature state is finalized between segments. A one-second tail after blank endpoints protects speech onset; overflow retains no tail. Overflow fails closed. Exact finalized-only acceptance and the five-second debounce remain unchanged. See tests/WAKE_CHECKS.md for actual-pipeline acoustic checks and runtime qualifications.

Synthetic checks reject all 42 unrelated samples and recognize all four intentional wakes. Steve explicitly authorized the same-certificate private Pixel update for testing, preserving app data and signing inputs. The initial continuous-full-decoder trial averaged 32.5% of one CPU core over 75 seconds; foreground listener/capture were active and unsilenced. Preliminary ordinary-speech testing produced no reported beeps, but repeated intentional wakes produced no response, so device validation failed. The on-demand optimization is under test; CPU improvement alone cannot close accuracy or extended unplugged battery tests. This source work is not in the currently published APK, and no replacement release/tag was published.

## Implementation and verification

Wake recognition is local Vosk with privacy-sensitive microphone capture. Android's configured recognizer captures one command after the beep. Vosk command capture is used only when no system recognition service is available; complete offline commands are not guaranteed. Spotify App Remote controls playback; Web API resolves named music/saved playlists. Replies use media audio routing; the app does not manage Bluetooth pairing or use a car/headset microphone.

Supported commands include basic controls, named song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ, Daily Mix and bounded Radio/Local Files destinations. Artist requests play one matching search track; saved names can match fuzzily. Availability depends on Spotify/account. Missing destinations produce a generic spoken failure. Own Spotify Developer Client ID and both authorization paths are required.

Numeric filler and bounded recognition alternatives preserve playlist intent without overriding genuine number-prefixed titles. Local OAuth setup errors retain fixed details/HTTP status without provider messages. Ordinary operations cancel on accepted wake/new command/service stop, guard late player/media-key effects and have a 30-second completion deadline; connection/consent limits are 12/120 seconds. Name comparisons preserve Unicode, fold accents and reject empty matches. Microphone permission is checked at capture creation. Diagnostics store categories/counts/timing, never recognized words or audio.

Run ./tests/check-playlists.ps1 before each commit. Relevant focused checks are tests/check-controller.py (Python 3, ANDROID_HOME) and the local token HTTP cleanup harness. Build/sign the release from committed source; verify embedded commit, unchanged certificate, package/version/non-debuggable/ABI, pinned SDK/model, all licence assets, 16KB ZIP/ELF alignment and checksums. The source ZIP must match every tracked file and contain build instructions/provenance. Publish only verified assets and matching source tag. Use the installed SDK/dependency cache only with an explicit cache qualification; no fresh-cache or whole-APK byte reproducibility claim is established.

The project source ZIP has been independently rebuilt using pinned SDK/model inputs and the existing cache. Compiled classes, manifest and native libraries were verified against the signed build. This does not prove audible device operation.

## Outstanding tests and limitations

The exact public APK has not received a fresh phone installation/registration or audible locked-screen test. Short Pixel/Bluetooth observations do not establish a success rate, battery-use figure or all-day reliability. Permission grants/denials, speech failure/recovery, cold Spotify start, beep routing, locked-screen/overnight survival, reboot and call/camera/recorder coexistence remain device tests. The current private Pixel wake trial is separate from the public release and was explicitly authorized; release maintenance itself did not change the installation.

Preserve Claude's README banner, Bluetooth observations, scope and unbuilt roadmap. Broader assistant functions are not implemented. Setup controls can overlap system bars; scroll them into view. The Spotify Wake Probe naming issue remains deferred under the unchanged-identity instruction and must not be described as cleared compliance.

Private audit evidence is in ignored claude-audit/HANDOVER_TO_CODEX.md, TRACKER.md, LOG.md and CODEX_FULL_REVIEW_2026-10-10.md. Keep it outside public Git. Verify current source/remote state before work, preserve unrelated edits, stage only task files, checkpoint meaningful changes and push without force. No paid services or Pixel installation changes are authorized by documentation/release maintenance.
