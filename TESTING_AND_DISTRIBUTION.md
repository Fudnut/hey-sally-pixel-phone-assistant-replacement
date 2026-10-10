# Hey Sally: installation, commands, testing and GitHub distribution

Updated 10 October 2026. App version: `0.2.1-alpha.3` (`versionCode 5`).

## Overview

Say **Hey Sally**, wait for the ready beep, then speak one Spotify command. Use a new wake phrase for each command. Initial installation, permissions, assistant selection and Spotify authorization require taps; everyday voice commands are intended to work without touching the phone.

The Android app is still called **Spotify Wake Probe** and uses package `com.steve.spotifywakeprobe`. Hey Sally is the current wake phrase; Hey Spotify is no longer accepted by the exact text matcher. This remains an experimental Spotify controller, not a general phone assistant or an official Spotify product.

The source is public, Issues are enabled, and contributions can be proposed now. A maintainer-signed **0.2.1-alpha.3** limited-test prerelease APK is offered with [phone installation instructions](INSTALL.md). The existing locally installed personal trial remains a debug build with a different certificate; it was not replaced by the public APK.

The user confirmed Hey Sally activation, audible resume and audible next-song playback on 10 October. One next-song test needed the wake phrase twice. Those short results do not establish long-duration reliability; the screen/lock state was not separately confirmed in that follow-up. Earlier feature tests used the previous wake phrase.

## Contents

- [Requirements](#requirements)
- [Install](#install)
- [Configure Spotify and Android](#configure-spotify-and-android)
- [Every current command](#every-current-command)
- [Everyday use and troubleshooting](#everyday-use-and-troubleshooting)
- [Test and report bugs](#test-and-report-bugs)
- [Contribute a pull request](#contribute-a-pull-request)
- [GitHub prerelease status](#github-prerelease-status)
- [Privacy and limits](#privacy-and-limits)

## Requirements

- An ARM64 Android phone running Android 14 or later. The app currently builds against and targets API 37. Device evidence is limited to a Pixel 8 Pro on Android 17.
- Spotify installed, signed in and able to play music normally. Use Spotify Premium for the documented testing path; the Spotify development-app owner must have an active Premium subscription.
- Your own Spotify Developer application and Client ID, or access to a specifically arranged tester application whose owner has allowlisted your account.
- Microphone permission, notifications enabled, and Spotify Wake Probe selected as the default digital assistant for background hands-free use.
- An Android speech-recognition provider and text-to-speech engine. Named searches and authorization require network access. The wake recognizer is local; this does not make the complete app an offline music service.

Spotify currently permits up to five authenticated users per development app, with an allowlist. A successful login can still be followed by API `403` errors if the account is not allowlisted. See [Spotify quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes). Its July 2026 update increased the developer Client ID limit to 25 and shares quota across that developer's apps; this is **not** a 25-user allowance. See [the July update](https://developer.spotify.com/blog/2026-07-23-web-api-quota-updates).

## Install

### Option A: download an APK when a test release is available

Use the [direct APK download and setup guide](INSTALL.md).

1. Open the project's [Releases page](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases).
2. Choose the intended prerelease and read its compatibility, setup and known-issue notes.
3. Download its signed `.apk` asset. GitHub's **Source code (zip)** download contains source; Android cannot install it as an app.
4. If supplied, compare the APK SHA-256 with the release's checksum file before installation. On Windows: `Get-FileHash ./downloaded-file.apk -Algorithm SHA256`.
5. Open the APK on the phone. Android may require **Allow from this source** for the browser or file manager used to open it; menu wording varies. Disable that permission again after installation if you do not need it.
6. Open **Spotify Wake Probe** and complete the configuration below.

Future updates must retain the chosen package identity and compatible signing certificate. If Android reports a signature conflict, stop and check the release notes. Uninstalling or clearing storage deletes settings, encrypted authorization and diagnostic history; Android backup/device transfer is disabled. Copy diagnostic history before any intentional migration. An APK signed with a new public-release key cannot replace an existing differently signed private trial under the same package.

### Option B: build the current source

For developers and testers who can build Android apps:

1. Install Git, PowerShell, a JDK compatible with Gradle 9.3.1, and an Android SDK with API 37 and the required build tools. This project has been built with JDK 25. Select a JDK supported by that Gradle version.
2. Clone the repository, then run:

```powershell
git clone https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement.git
Set-Location hey-sally-pixel-phone-assistant-replacement
./fetch-spotify-sdk.ps1
./fetch-model.ps1
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
./tests/check-playlists.ps1
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:signingReport
```

The SDK/model scripts verify pinned SHA-256 values. The first build downloads Gradle and dependencies. `--offline` works only after the required tools and dependencies are cached. On macOS/Linux use `./gradlew` and PowerShell (`pwsh`) for the scripts.

3. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Use Android's normal developer installation tools. With ADB available and the phone's USB debugging authorized:

```powershell
adb devices
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
```

`-r` preserves app data for a compatible update; it does not bypass signing conflicts. Each developer's standard debug key may differ. Keep the SHA-1 fingerprint from **your** signing report for Spotify registration. The repository includes no private signing key or prefilled Spotify registration.

## Configure Spotify and Android

Complete these steps while stationary, with the phone unlocked.

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard). Create your own app and register redirect URI **`http://127.0.0.1:8765/callback`** exactly. Choose the Android SDK/Web API options if the dashboard asks which APIs the app uses.
2. If package/fingerprint registration fields are offered, use package **`com.steve.spotifywakeprobe`** and the SHA-1 fingerprint for the APK you will install. For a future maintainer-signed release, use the fingerprint published with that release rather than a local debug fingerprint.
3. Ensure the Spotify account you will test is permitted by the development app's Users Management/allowlist settings. Never post account email addresses in public bug reports.
4. Open **Spotify Wake Probe**, enter the Developer Client ID and tap **Save Client ID**. Enter the Client ID, never the Client Secret.
5. Tap **Authorize Spotify playback** and accept the Spotify authorization.
6. Tap **Authorize named music and private playlists** and complete that authorization. The app uses PKCE and requests App Remote control plus private-playlist access. This enables named searches and saved-playlist lookup.
7. Tap **Grant microphone and start probe**, then grant microphone access. If it does not start after the permission prompt, tap the button again. Enable notifications in Android's app settings if needed; the current setup button requests the microphone permission.
8. Tap **Open Default apps for assistant setup**, then select **Spotify Wake Probe** under **Digital assistant app**. This replaces your previously selected assistant while active.
9. Select the appropriate **Command language**: US, Australian, UK or New Zealand English, or the phone's English language. The choice applies on the next wake. It changes command recognition, not the offline wake model or TTS voice.
10. Check the status/notification for **Listening for Hey Sally**. Start Spotify playback manually once for the first basic-control test.
11. Lock the phone and let the screen turn off. Say **Hey Sally**, wait for the beep, then say **pause**. Repeat with **resume**, then **next song**, confirming actual audible results.

After reboot, unlock once so the offline model in credential-protected storage is accessible. Background startup has earlier device evidence; repeat the reboot test with the current Hey Sally build before relying on it.

## Every current command

Every row includes the full sequence: say **Hey Sally**, **wait for the beep**, then speak the command. Use a fresh wake for every request. Alternate command forms use the same Hey Sally → beep sequence. Examples below are suggestions, not guaranteed search results. Names in angle brackets are replaced by your own request.

The table describes alpha.3. The earlier alpha.1 APK uses exact control phrases, always treats bare numeric requests as playlist selection and lacks the new ordinary-failure speech and wake-result events. Use the version shown in your release and include it in reports.

| Action | Preferred voice sequence | Other command forms after Hey Sally and the beep | Behavior / limits |
| --- | --- | --- | --- |
| Open Spotify | **Hey Sally** → **wait for beep** → `open Spotify` | None | Starts Spotify's activity. Showing it above the lock screen has not been established. |
| Pause | **Hey Sally** → **wait for beep** → `pause` | `pause music`, `pause the music`, `pause please`, `stop`, `stop music` | Pauses playback. |
| Resume | **Hey Sally** → **wait for beep** → `resume` | `resume music`, `resume the music`, `continue` | Resumes an existing playback context. If there is nothing to resume, request a song or playlist or start Spotify manually. |
| Next track | **Hey Sally** → **wait for beep** → `next song` | `next`, `next track`, `play next song`, `play next track`, `play the next song`, `skip`, `skip this song`, `next song please` | Skips the track; does not explicitly start paused playback. Use resume first. |
| Previous track | **Hey Sally** → **wait for beep** → `previous song` | `previous`, `previous track`, `play previous song`, `play previous track`, `play the previous song`, `play the previous track` | Requests the preceding track, accounting for Spotify's restart-current-track behavior after three seconds. Does not explicitly resume paused playback. |
| Named song | **Hey Sally** → **wait for beep** → `play song <title>` | `play the song <title>`; e.g. `play song Yesterday by The Beatles` | Searches for a song. Explicit song wording also handles a numeric title or a title that looks like a special command. |
| Named artist | **Hey Sally** → **wait for beep** → `play artist <artist>` | None | Starts one randomly selected track from up to ten search results whose artist name matches; it does not start an artist radio/context. |
| Named saved playlist | **Hey Sally** → **wait for beep** → `play playlist <name>` | `play my playlist <name>` | Matches an exact saved-library playlist name; ambiguous/missing results can fail. |
| General music request | **Hey Sally** → **wait for beep** → `play <name>` | None | Special destinations and numbers in an active playlist browse are checked first; otherwise tries an exact artist match, then a track search. Prefer explicit song/artist/playlist wording. |
| List saved playlists | **Hey Sally** → **wait for beep** → `list my playlists` | `list playlists`; singular `playlist`, split `play lists`/`play list`, punctuation and optional `please` are accepted | Reads five numbered names. Regular library playlists only; Liked Songs is a separate command. |
| Next playlist page | **Hey Sally** → **wait for beep** → `more playlists` | Singular/split playlist wording, punctuation and optional `please` | Reads the next five names with continuing numbers. `next` still means next track. |
| Choose announced playlist | **Hey Sally** → **wait for beep** → `play two` | `play number two`, `play 2`, `play number 2`; use another announced number | With an active browse, selects only a number already read from the current playlist snapshot. Without an active browse, bare `play two`/`play 2` searches the title. Explicit `play number two`/`play number 2` always means playlist selection and asks for a fresh list. |
| Liked Songs | **Hey Sally** → **wait for beep** → `play Liked Songs` | `play my Liked Songs` | Plays Spotify's exposed collection if available. |
| Spotify DJ | **Hey Sally** → **wait for beep** → `play DJ` | `play D J`, `play Spotify DJ`, `play the DJ` | Requests the actual DJ experience if exposed for the account. |
| Daily Mix | **Hey Sally** → **wait for beep** → `play Daily Mix two` | `play Made For You two`, `play Daily Mix 2`, `play Made For You 02`; optional `number`, `zero` or `oh` before a valid number | Numbers 1–6 only. Made For You means Daily Mix here, not every personalized Spotify collection. |
| Artist Radio | **Hey Sally** → **wait for beep** → `play radio <artist>` | `play <artist> radio`, `play radio by <artist>`, `play radio for <artist>`, `play radio artist <artist>` | Matches an exact recommended or saved Radio playlist; does not create arbitrary new artist stations. |
| Local Files | **Hey Sally** → **wait for beep** → `play Local Files` | `play my Local Files` | Uses the native collection if exposed, otherwise an exact saved playlist named Local Files. Real local-track fallback playback remains untested. |

A request such as `play number of the beast` or `play number 9 dream` searches the full title; `number` selects a playlist only when the whole remainder is a number. The parser also tolerates the observed fallback transcripts `regime` and `review` as resume, and some number homophones such as `to`/`too` for two. These are recognition accommodations, not recommended commands. There is no current voice command for volume, shuffle, repeat, queue editing, phone calls, messages, changing the wake phrase or stopping the probe.

### Browse and select playlists

1. **Hey Sally** → beep → **list my playlists**.
2. Wait until all five names have been spoken. Do not interrupt the reply.
3. **Hey Sally** → beep → **play two** selects the second announced playlist.
4. Or **Hey Sally** → beep → **more playlists** announces the next page, numbered 6–10.

The snapshot expires three minutes after the last successful page finishes speaking. Only announced numbers can be selected while the snapshot is active. Without an active snapshot, bare numeric requests search the song title; explicit `play number two` asks for a fresh list. `play song one` always searches even while browsing. A new list request replaces the snapshot; service restart/app update clears it. Start another list if it has expired. Listing currently reads at most 1,000 regular library playlists.

Named playlist lookup searches at most 1,000 saved playlists. It first compares names with Unicode letters/numbers preserved and accents/punctuation folded. It can then choose a uniquely best fuzzy match within a distance of at most two edits or 40% of the saved name length, whichever is larger. This can choose a similarly named playlist; numbered selection from a freshly read list is more predictable. Artist/song exact-name comparisons also preserve Unicode and fold accents.

### Radio and Local Files preparation

If Radio is missing from recommendations, open the artist in Spotify, choose **Go to Radio**, save the station, then retry. Save stations before your journey. The saved-station workflow has passed a user test, but availability is not guaranteed for every artist/account.

For Local Files, first make the tracks play normally inside Spotify on the phone, then create/populate a regular library playlist named **Local Files** for the fallback. The tested device did not expose a native Local Files collection. A missing destination receives a spoken explanation rather than an unrelated song search.

## Everyday use and troubleshooting

- **Beep, then nothing audible:** the beep means the recognizer is ready; it is not a success signal. Wait for it before speaking. If Spotify is paused, try **resume** or a named playback request before **next song**.
- **No wake beep:** wait until the prior reply/command ends, then try the exact **Hey Sally** phrase again. Check listener status, microphone permission, default-assistant selection and whether another app is using audio input. Record a missed first attempt rather than assuming the command failed.
- **Wrong music:** use explicit **play song**, **play artist** or **play playlist** wording, with an artist qualifier where helpful.
- **Quiet replies:** replies request the media route/volume. Check media volume and the chosen phone/Bluetooth/car output. One earlier car-route test succeeded; every route is not validated.
- **Playlist number rejected:** list again; it may have expired or the number may not have been announced.
- **Language support check fails:** some recognizers cannot answer the support query. This does not prove the language is unavailable; use an actual spoken test. The check does not download a model.
- **Buttons overlap system bars:** a setup-screen inset issue remains. Scroll the desired control into the middle of the screen before tapping.
- **Stop listening:** use **Stop probe**. Stopping disables hands-free wake. To stop background assistant activation as well, select your previous assistant in Android's Default apps settings.
- **Authorization fails:** check the exact redirect, Client ID, allowed Spotify account and APK fingerprint where applicable. Reauthorize while unlocked. Do not include tokens/passwords in reports.

## Test and report bugs

Open [GitHub Issues](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/issues) and create a bug report. A GitHub account is needed to submit it. Issues are enabled; a bug-report template is supplied.

Include:

- App version and APK/release or source commit; installation source.
- Phone model, Android version, Spotify version, command language, and audio route (phone, headphones, Bluetooth speaker/car).
- Whether screen was on/off and phone unlocked/locked; whether music was already playing.
- Approximate time **with timezone**, intended wake/command, number of attempts, expected result and what you actually heard.
- Whether the beep occurred, any spoken failure reply, and whether music resumed after the reply.
- Minimal steps to reproduce. Use neutral example music names or redact personal playlist names.
- Reviewed diagnostic history if useful. Do not post full ADB logs, OAuth tokens, passwords, private keys, account email addresses or screenshots revealing them.

### Copy diagnostic history

Open the app, tap **Refresh status**, then **Copy diagnostic history**. Paste into a private note, review it, and attach only relevant redacted events to an issue. The app stores up to 1,024 events; busy use can overwrite earlier entries. Copy history each evening during a multi-day trial. **Start fresh diagnostic trial** clears it, so save any history you need first. Do not reset it merely because a command failed.

In alpha.3, `WAKE_RESULT class=accepted ms=...` records exact wake-phrase matches individually. Other and empty results are counted in `WAKE_RESULTS_SUMMARY other=... empty=... ms=...`, normally once every five minutes (Android sleep can delay it), with a partial summary on service stop. Routine results do not carry individual timestamps or audio durations. Summary counts may reveal nearby speech/noise activity; accepted wakes and debounce rejections retain individual timestamps. Starting a fresh diagnostic trial also clears pending counts. Accepted means an exact wake-phrase match; `WAKE_IGNORED_DEBOUNCE` then identifies one rejected by the existing five-second gate. Summary milliseconds describe the elapsed counting window. Accepted-result milliseconds measure captured audio since recording started or the preceding finalized result, including silence; they are not latency from when you spoke the wake phrase. No recognized words or audio are stored.

Ordinary command failures carry only a fixed reason in `COMMAND_RESULT ERROR reason=AUTH|TIMEOUT|NETWORK|NO_MATCH|REMOTE`. These categories help distinguish authorization, timeouts, connectivity, missing matches and other remote failures; exception text, URLs and recognized words are omitted. The spoken failure remains generic. Setup-screen local authorization failures show DECLINED, STATE_MISMATCH or CALLBACK; other failures show the fixed reason; a failed token exchange also shows its HTTP status, such as `AUTH (HTTP 400)`. Include this safe detail in setup bug reports. Provider response bodies, exception messages, tokens and URLs are not displayed.

Diagnostics omit recognized words, so add your intended command separately if comfortable. A silent log cannot prove a missed wake; your observation is needed. Heartbeats are scheduled every 30 minutes but can be delayed by Android sleep.

`SPOTIFY_CONNECT_RESULT CONNECTED|TIMEOUT|ERROR|IGNORED ms=... mode=PLAYBACK|AUTH` records elapsed time from the App Remote connection attempt until its callback is handled. An ignored late connection is disconnected. Playback connection attempts keep their 12-second timeout; ordinary commands also have a 30-second deadline covering search through player completion. A new accepted wake, a superseding command or service stop cancels the old ordinary operation; late results cannot dispatch playback or media keys. Playback already issued to Spotify cannot be undone by cancellation. Manual authorization allows 120 seconds for consent. These timings do not prove audible playback.

### Suggested test sequence

While stationary, record observations for: wake with music paused and playing; pause/resume/next/previous; a named song; playlist list/number selection; a missing destination and recovery; phone and Bluetooth output; locked screen; reboot followed by first unlock; and several hours/overnight screen-off use. Note unwanted beeps, missed phrases and battery levels/charging. Call/camera/recorder coexistence needs separate checks. Passing one command is not an overall reliability or battery result.

For a separate cold-start trial on the updated build, while stationary: compare a known song request with Spotify already open against the same request after force-stopping Spotify in Android settings. Record the connection outcome/duration, any later ignored connection, and what you actually hear. Save diagnostics before changing installations. A device result is needed before choosing a different timeout.

## Contribute a pull request

1. Fork the [repository](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement) and clone your fork.
2. Create a focused branch, for example `fix/playlist-selection`. For a larger behavior change, discuss the scope in an issue first.
3. Make the smallest change that addresses the issue; add/update a meaningful regression check when behavior changes.
4. Run `./tests/check-playlists.ps1`, `python -X utf8 ./tests/check-controller.py` (Python 3 and `ANDROID_HOME` required), and `./gradlew.bat :app:assembleDebug :app:lintRelease`. Use `--offline` only with a complete cache. Report warnings or checks you could not run.
5. For microphone, speech, background playback or audio-focus changes, describe actual device tests and distinguish them from local Java checks. Verify audible output; callbacks alone are insufficient.
6. Commit and push to your fork, then open a PR against `main`. Explain the problem, resulting behavior, related issue and validation.
7. Keep credentials, keystores, model/SDK downloads, APKs, diagnostics, caches and personal listening records out of commits. Do not change public/private app identity or signing to bypass an installation problem.

No contributor receives direct write access merely by submitting a PR. Maintainers review and merge contributions. The current project source is GPL version 3 only (`GPL-3.0-only`) with the [limited linking permission](LINKING_EXCEPTION.md); contributions intended for the distributed app must be compatible with both. Earlier alpha.1/alpha.2 releases retain their MIT terms. Dependencies keep their own terms. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## GitHub prerelease status

The limited-test APK is **0.2.1-alpha.3**, signed with a dedicated public-release certificate. [INSTALL.md](INSTALL.md) is the phone-first download/setup guide; [RELEASE_SIGNING.md](RELEASE_SIGNING.md) identifies the public certificate and update policy.

| Item | State for this alpha |
| --- | --- |
| Public source and collaboration | Public repository, Issues enabled, bug-report and PR templates supplied. Forks and PRs are welcome. |
| APK | Non-debuggable ARM64 release build, Android 14+, versionCode 5; signature and alignment verified. |
| Signing | Dedicated release key kept locally outside Git; public certificate fingerprint supplied for Spotify setup. Maintainer must retain a recoverable key/credential backup for future updates. |
| Dependency notices | Full fetched licence/notice texts embedded in assets and supplied in a notices ZIP. Runtime/native inventory and provenance documented in THIRD_PARTY_NOTICES.md. |
| Downloads | Versioned APK, checksums, notices, GPL text/linking permission, project source and install guide are prerelease assets. Alpha.1/alpha.2 release downloads and tags are retired. Source-code ZIPs are not installable APKs. |
| Spotify access | Each tester configures their own Developer Client ID or an explicitly arranged allowlisted tester app. No shared registration is embedded. |
| Checks | Java command/wake/language/playlist checks and release build including full Android release lint pass (ten non-fatal warnings remain). Signature, package/version/ABI, non-debuggable manifest and notice assets verified. |
| Device limits | Public certificate APK not installed over the differently signed private trial. Fresh release installation/registration and exact-artifact audio tests remain unverified. Debug-build Hey Sally/resume/next have short user confirmation; one repeated wake was reported. |
| Maintenance | No CI yet. Manual verified prereleases are sufficient initially; broader device, battery, false-wake and call/camera/recorder checks remain. |

A precompiled APK removes the need for testers to compile Android code, but Spotify registration/authorization remains necessary. A development Client ID cannot offer unrestricted access to the public. Broader shared access needs a separate plan under [Spotify's current rules](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

Future releases must keep the chosen public certificate/package and increase versionCode. A GPL-covered APK also requires complete Corresponding Source for Hey Sally and its build scripts. The [limited linking permission](LINKING_EXCEPTION.md) allows the listed independent libraries to retain their own terms without requiring their implementation source in that bundle. Keep all dependency notices and pinned input provenance. Do not generate a new key for every release or publish signing material. Review exact assets, tag their source commit and attach them to a GitHub prerelease. CI can follow after the manual release path is proven.
## Privacy and limits

Wake audio is processed locally with Vosk and is not saved by app-owned code. Command audio may be sent to Android's speech provider; playlist names go to the configured TTS engine, whose voice may use network processing. Named searches/authorization go to Spotify. There is no Gemini connection or project-operated backend. Tokens are encrypted using Android Keystore; backup and device transfer are disabled.

Continuous wake capture shows Android's microphone indicator. Protected capture can interfere with background song identification/recording. Calls, camera and voice-recorder coexistence remain incompletely tested. Use the stop control when another app needs the microphone. Replies temporarily suspend recognition; interruption during readout is not supported.

This remains an ARM64 Android 14+ prototype tested on one phone. False wakes, missed commands, cold/background audio problems and device-specific behavior are possible. Test setup and troubleshooting while stationary. It does not establish a success rate, all-day survival or battery-use figure.

## Maintainer references

- [GitHub Releases and binary assets](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
- [GitHub issue and PR templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)
- [Android app signing and update continuity](https://developer.android.com/studio/publish/app-signing)
- [Spotify development access and quotas](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
- [Spotify July 2026 quota update](https://developer.spotify.com/blog/2026-07-23-web-api-quota-updates)
- [Third-party source notices and APK review boundary](THIRD_PARTY_NOTICES.md)
