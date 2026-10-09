# Public project handoff

## Resume here

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement
- Branch: `main`.
- Read `README.md` for build/setup, commands, privacy and limitations, and `THIRD_PARTY_NOTICES.md` for dependency terms.
- This repository starts from one cleaned public source baseline. Earlier private development history and personal test records are excluded.
- Original project code is MIT licensed. The baseline uses a generic contributor identity rather than a personal email.

## Current implementation

The Android app is still named **Spotify Wake Probe**, its package is `com.steve.spotifywakeprobe`, and its wake phrase is **Hey Sally**. The activation grammar, exact matcher, setup instructions, notifications and retry replies now use the new phrase. The previous Hey Spotify phrase is rejected by the matcher. On 10 October the user confirmed Hey Sally activation and audible resume/next playback. One next-song attempt needed two wakes; screen/lock state was not separately confirmed. A signing-compatible local test APK has now updated the existing Pixel installation without resetting app data.

The app provides hands-free wake detection, Spotify playback controls, song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ and Daily Mix requests. Radio matches stations in recommendations or saved playlists. Saving a missing station in Spotify and retrying has passed a user test. Native Local Files playback was not established; a playlist named Local Files is the existing fallback.

Wake detection uses offline Vosk with protected microphone capture. Commands use Android's speech recognizer with an offline fallback. Spoken replies use media audio routing; car-speaker output has passed a short check. The phone keeps up to 1,024 private, redacted diagnostic events without saving recognized words or audio.

## Verification before this handoff

- The cleaned source copy passed the parser, language, playlist paging/expiry and destination-matching checks.
- The cleaned source copy built a debug APK successfully, using the installed Android SDK and shared dependency cache. This was not a completely fresh dependency-cache build.
- Spotify SDK download passed its pinned SHA-256 check. The model archive was reused and checksum-verified.
- Public files were screened for known private identifiers, credentials, signing keys, diagnostics and development-history files. This was a targeted scan, not an exhaustive security or dependency audit.
- Initial source-baseline checks did not install an APK. The later Hey Sally checkpoint below installed a locally signed public-source test build over the existing trial without clearing data.

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

## Wake phrase checkpoint - 10 October 2026

- Changed activation from Hey Spotify to Hey Sally, including the Vosk wake grammar, exact matcher, on-screen instructions, notifications and retry replies. App name, package, command handling and capture policy remain unchanged.
- The wake self-check failed against the old matcher and passed after the change. It accepts case/outer-whitespace variants and rejects the former phrase, surrounding words, near matches, empty and null input. The existing PowerShell check now runs this self-check alongside command/language/playlist/destination checks; all passed.
- Offline debug APK build passed using installed Android tools and a shared dependency cache, with checksum-verified SDK/model assets. Existing SDK XML, deprecation and native-symbol stripping warnings remain. This was not a fresh dependency-cache build.
- A local test APK was signed with the existing device-compatible key, verified against the installed certificate, and installed over the Pixel app with data preservation. Redacted diagnostics were backed up privately; all previous events remain after installation. The wake foreground service returned to LISTENING_READY. No key or private diagnostic export was added to this repository.
- User follow-up confirmed Hey Sally activation and audible resume/next playback; one next-song attempt required two wakes. Locked/screen-off state was not separately confirmed. Explicit old-phrase rejection, unwanted wakes and longer reliability remain pending.

## Installation and distribution documentation - 10 October 2026

- Added TESTING_AND_DISTRIBUTION.md with all current commands/aliases, installation/setup, troubleshooting, privacy, diagnostic bug reporting, PR steps and a concrete first-prerelease checklist. README links to it.
- Verified the repository is public, Issues are enabled and no Releases are published. Public APK release signing, packaged dependency notices and exact-artifact/device verification remain outstanding. No APK publication, key creation, runtime changes or repository-settings changes were performed.

- Command-guide clarification: all 17 preferred command rows now show Hey Sally → wait for beep → command; the alternative-forms heading also states the wake sequence.

## Public APK prerelease - 10 October 2026

- Prepared 0.2.1-alpha.1 (versionCode 3), ARM64 Android 14+, non-debuggable release variant; no playback/wake behavior changes. Package and app label remain unchanged.
- Dedicated public-release signing key is local/ignored, separate from private trial signing. Password is protected with Windows user encryption, never committed/published. RELEASE_SIGNING.md records public certificate fingerprints, update continuity and secure recoverable backup requirement.
- INSTALL.md gives a direct APK download, phone-first install, own Spotify Developer Client ID setup, commands, privacy and bug reporting. README links it; bug/PR templates added.
- Licence/notice texts embedded in APK assets and bundled as a release ZIP; inventory includes Gradle runtime dependencies and conservative native inputs. Full source provenance in assets/third-party/SOURCES.txt. Exact upstream native build revisions are not proved by the Maven artifact.
- Checks: Java command/wake/language/playlist checks pass; release assembly and Android vital lint pass after fetching previously uncached lint dependencies. Signed APK certificate, alignment, version, ARM64 ABI, non-debuggable manifest, model/notices and public registration default reviewed. Existing XML/deprecation/native-strip warnings remain.
- Public release APK is not installed on the existing Pixel trial because the certificates differ. Exact-release fresh installation, Spotify registration and real audible playback remain unverified; prerelease notes disclose this. Existing phone installation/history were preserved. Initial user confirmed Hey Sally/resume/next on the public-source debug trial, with one repeated wake; no new screen-off result is inferred.

- Published prerelease: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases/tag/v0.2.1-alpha.1 . Tag points to source commit 1f0f9b74fb5ad6e4c25de89f063386d8d10d5240. Five assets: APK, notices ZIP, INSTALL.md, RELEASE_SIGNING.md and SHA256SUMS.txt. GitHub SHA-256 digests match all five local uploaded files; direct APK download returned HTTP 200.
- APK SHA-256: 1504b4b4f20c98b2fa3305452cc2bb3ce5aa534c698e6f5be58646700b9a3b37. Local release outputs remain ignored. Keep the dedicated release keystore/credential recoverable and backed up securely for compatible updates; Windows user encryption alone is not a portable backup.
