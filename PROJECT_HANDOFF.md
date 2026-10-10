# Public project handoff

Updated 10 October 2026 (Australia/Sydney).

## Current state

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement ; branch `main`.
- Limited-test release: 0.2.1-alpha.4, versionCode 6, ARM64, Android 14+, target API 37. Read README.md, INSTALL.md and TESTING_AND_DISTRIBUTION.md for current setup, commands, privacy and testing.
- Licence: GPL-3.0-only with the limited independent-library permission in LINKING_EXCEPTION.md. Complete project source/build scripts accompany the APK; third-party licences and copyright attribution remain intact. SOURCE_BUILD.md and THIRD_PARTY_NOTICES.md describe source/input provenance.
- Package `com.steve.spotifywakeprobe`, launcher label Spotify Wake Probe, exact wake Hey Sally, public certificate and existing signing inputs are fixed compatibility boundaries. Do not relabel, move/copy keys, expose credentials, overwrite the Pixel installation or rewrite Git history. Ponytail is off.

## Wake false-activation correction

Ordinary speech from a video session was reported to trigger the wake listener at normal volume. Current source removes the wake-only grammar, allowing the existing local model's full vocabulary to compete before the finalized exact Hey Sally gate. Synthetic acoustic regression reproduced five false wakes with the constrained grammar; corrected decoding rejected all 42 unrelated samples and accepted all four intentional wakes. See tests/WAKE_CHECKS.md for runtime qualification and reproduction. This is a source correction; the currently published APK does not contain it. Phone background-speech/intentional-wake/CPU/battery tests remain required before declaring it device-validated. Preserve the Pixel installation. No replacement release was published for this correction.

## Implementation and verification

Wake recognition is local Vosk with privacy-sensitive microphone capture. Android's configured recognizer captures one command after the beep. Vosk command capture is used only when no system recognition service is available; complete offline commands are not guaranteed. Spotify App Remote controls playback; Web API resolves named music/saved playlists. Replies use media audio routing; the app does not manage Bluetooth pairing or use a car/headset microphone.

Supported commands include basic controls, named song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ, Daily Mix and bounded Radio/Local Files destinations. Artist requests play one matching search track; saved names can match fuzzily. Availability depends on Spotify/account. Missing destinations produce a generic spoken failure. Own Spotify Developer Client ID and both authorization paths are required.

Numeric filler and bounded recognition alternatives preserve playlist intent without overriding genuine number-prefixed titles. Local OAuth setup errors retain fixed details/HTTP status without provider messages. Ordinary operations cancel on accepted wake/new command/service stop, guard late player/media-key effects and have a 30-second completion deadline; connection/consent limits are 12/120 seconds. Name comparisons preserve Unicode, fold accents and reject empty matches. Microphone permission is checked at capture creation. Diagnostics store categories/counts/timing, never recognized words or audio.

Run ./tests/check-playlists.ps1 before each commit. Relevant focused checks are tests/check-controller.py (Python 3, ANDROID_HOME) and the local token HTTP cleanup harness. Build/sign the release from committed source; verify embedded commit, unchanged certificate, package/version/non-debuggable/ABI, pinned SDK/model, all licence assets, 16KB ZIP/ELF alignment and checksums. The source ZIP must match every tracked file and contain build instructions/provenance. Publish only verified assets and matching source tag. Use the installed SDK/dependency cache only with an explicit cache qualification; no fresh-cache or whole-APK byte reproducibility claim is established.

The project source ZIP has been independently rebuilt using pinned SDK/model inputs and the existing cache. Compiled classes, manifest and native libraries were verified against the signed build. This does not prove audible device operation.

## Outstanding tests and limitations

The exact public APK has not received a fresh phone installation/registration or audible locked-screen test. Short Pixel/Bluetooth observations do not establish a success rate, battery-use figure or all-day reliability. Permission grants/denials, speech failure/recovery, cold Spotify start, beep routing, locked-screen/overnight survival, reboot and call/camera/recorder coexistence remain device tests. The Pixel trial is untouched by release work.

Preserve Claude's README banner, Bluetooth observations, scope and unbuilt roadmap. Broader assistant functions are not implemented. Setup controls can overlap system bars; scroll them into view. The Spotify Wake Probe naming issue remains deferred under the unchanged-identity instruction and must not be described as cleared compliance.

Private audit evidence is in ignored claude-audit/HANDOVER_TO_CODEX.md, TRACKER.md, LOG.md and CODEX_FULL_REVIEW_2026-10-10.md. Keep it outside public Git. Verify current source/remote state before work, preserve unrelated edits, stage only task files, checkpoint meaningful changes and push without force. No paid services or Pixel installation changes are authorized by documentation/release maintenance.
