# Public project handoff

## Resume here

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement
- Branch: `main`.
- Read `README.md` for build/setup, commands, privacy and limitations, and `THIRD_PARTY_NOTICES.md` for dependency terms.
- This repository starts from one cleaned public source baseline. Earlier private development history and personal test records are excluded.
- Original project code is MIT licensed. The baseline uses a generic contributor identity rather than a personal email.

## Current implementation

The Android app is still named **Spotify Wake Probe**, its package is `com.steve.spotifywakeprobe`, and its wake phrase is **Hey Spotify**. The repository name does not change these. Renaming the app or changing its wake phrase to Hey Sally requires a separate implementation and real-device test; do not assume that has happened.

The app provides hands-free wake detection, Spotify playback controls, song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ and Daily Mix requests. Radio matches stations in recommendations or saved playlists. Saving a missing station in Spotify and retrying has passed a user test. Native Local Files playback was not established; a playlist named Local Files is the existing fallback.

Wake detection uses offline Vosk with protected microphone capture. Commands use Android's speech recognizer with an offline fallback. Spoken replies use media audio routing; car-speaker output has passed a short check. The phone keeps up to 1,024 private, redacted diagnostic events without saving recognized words or audio.

## Verification before this handoff

- The cleaned source copy passed the parser, language, playlist paging/expiry and destination-matching checks.
- The cleaned source copy built a debug APK successfully, using the installed Android SDK and shared dependency cache. This was not a completely fresh dependency-cache build.
- Spotify SDK download passed its pinned SHA-256 check. The model archive was reused and checksum-verified.
- Public files were screened for known private identifiers, credentials, signing keys, diagnostics and development-history files. This was a targeted scan, not an exhaustive security or dependency audit.
- No public-copy APK was installed on the test phone. Its existing private trial installation was left intact.

## Build and identity boundaries

1. Follow the README to install a suitable JDK and Android SDK/API 37.
2. Run `fetch-spotify-sdk.ps1` and `fetch-model.ps1`; downloaded binaries/model are ignored.
3. Run `gradlew.bat :app:assembleDebug` and `tests/check-playlists.ps1`.
4. Supply your own Spotify Developer Client ID and register your own debug signing fingerprint as appropriate. No personal Client ID or signing key is supplied.

The public build uses standard debug signing. A different signing key cannot update an existing installation of the same package. Do not uninstall or overwrite an existing trial to resolve that without agreeing how its settings and diagnostics will be preserved.

Do not copy private handoffs, diagnostics, registration values, keys, local caches or Git history into this repository. For any APK distribution, first prepare release signing and a complete notice/licence inventory for packaged dependencies; this baseline is a source release.

## Remaining work

- Agree the next objective before implementing a feature. A repository rename alone is not approval to change the wake phrase or application identity.
- Long-duration wake reliability, acoustic false wakes, battery use, call/camera/recorder coexistence and wider device support remain unmeasured or incomplete.
- Keep no-touch/locked-screen operation as a requirement. Validate real audible playback with the user; a Spotify success callback alone is insufficient.
- No paid APIs or hosted model integration are configured or authorized.
- Preserve existing work, use focused checks, and checkpoint task-related changes with commits/pushes. Do not claim new device tests from the earlier short tests.
