# Public project handoff

Updated 10 October 2026 (Australia/Sydney).

## Current state

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement ; branch `main`.
- Limited-test release: 0.2.1-alpha.4, versionCode 6, ARM64, Android 14+, target API 37. Read README.md, INSTALL.md and TESTING_AND_DISTRIBUTION.md for current setup, commands, privacy and testing.
- Licence: GPL-3.0-only with the limited independent-library permission in LINKING_EXCEPTION.md. Complete project source/build scripts accompany the APK; third-party licences and copyright attribution remain intact. SOURCE_BUILD.md and THIRD_PARTY_NOTICES.md describe source/input provenance.
- Package `com.steve.spotifywakeprobe`, launcher label Spotify Wake Probe, exact wake Hey Sally, public certificate and existing signing inputs are fixed compatibility boundaries. Do not relabel, move/copy keys, expose credentials or rewrite Git history. Pixel updates are authorized only for the current in-place private wake test; preserve its signing identity and app data. Release maintenance does not authorize installation changes. Ponytail is off.

## Wake status - 10 October 2026

Commit 80c0bf9 replaces the candidate-plus-verifier design with one closed-vocabulary decoder: grammar `hey sally`, `[unk]` and about 255 common decoy words (WakeGrammar), exact finalized-only matching, a one-result hold for a Hey split across endpoints, and the unchanged five-second debounce. The verifier, its audio replay buffer and the verifier probe fields are removed. The earlier verifier builds (498f1c2, 44f93f3) rejected false wakes but missed many intentional ones live: a probe run showed the candidate stage firing while the verifier never produced a matching Hey. Evidence is in the ignored claude-audit/REVIEW_WAKE_2026-10-10.md (addenda 1-5) and CODEX_REPORT_decoy-grammar.md.

Pixel results (private in-place debug install, same certificate, app data kept; Steve's voice only): wakes were accepted at about 40 cm (6/6 on the experimental predecessor), about 1.5 m (4 accepted, attempts not counted) and in a car (5 accepted) - all with the built-in microphone, input type 15. There were zero false wakes in a five-minute video-call speech window (77 rejected results, quiet playback) and about twelve minutes of loud car audio. One CPU measurement of an equivalent build: about 24% of one core, USB-powered, not a battery figure. Not tested: a Bluetooth microphone route, wake while music plays, a pocket or beyond 1.5 m, other voices and accents, unplugged battery, long idle. The decoy build is committed locally; the published APK, release and tag are unchanged, and main is not a validated release.

## Wake false-activation correction

Ordinary normal-volume video-session speech triggered the original wake-only grammar, which can coerce unrelated words into an exact Hey Sally; raising confidence alone does not fix it. The current source gives unrelated speech other words to land on (closed decoy vocabulary) instead of verifying afterwards. Exact finalized-only acceptance and the five-second debounce are unchanged. See tests/WAKE_CHECKS.md for the actual-pipeline acoustic checks, the gain/noise matrix and runtime qualifications.

Desktop synthetic checks (Vosk 0.3.45; four intentional and 42 unrelated synthetic clips) detect 4/4 and reject 42/42 at clean, 20 dB, 10 dB and quiet plus 10 dB; at 5 dB one unrelated clip wakes. Steve explicitly authorized same-certificate private Pixel updates for testing, preserving app data and signing inputs. Earlier trials: continuous full decoding averaged 32.5% of one CPU core over 75 seconds and on-demand verification 20.9% over 101 seconds, and both missed intentional live wakes. This source work is not in the currently published APK, and no replacement release/tag was published.

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
