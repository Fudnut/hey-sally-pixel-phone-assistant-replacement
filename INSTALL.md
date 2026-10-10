# Install and test Hey Sally 0.2.1-alpha.3

This is an experimental, precompiled Android APK. You do not need Android Studio, a compiler or a USB cable to install it. The app appears on your phone as **Spotify Wake Probe**; its wake phrase is **Hey Sally**.

## Download

- [Download the ARM64 APK](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases/download/v0.2.1-alpha.3/Hey-Sally-0.2.1-alpha.3-arm64.apk)
- [Release notes and all assets](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases/tag/v0.2.1-alpha.3)
- [Project source and build instructions](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases/download/v0.2.1-alpha.3/Hey-Sally-0.2.1-alpha.3-source.zip)
- [APK/asset checksums](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/releases/download/v0.2.1-alpha.3/SHA256SUMS.txt)

Choose the `.apk` asset, not GitHub's source-code ZIP. Requires an **ARM64 phone on Android 14 or later**, Spotify installed, and Spotify Developer access. Device testing so far is limited to a Pixel 8 Pro on Android 17; other phones, battery use and long-term reliability are not established.

**You still need your own Spotify Developer Client ID.** The APK is precompiled, but it does not include a shared Spotify account or registration. A development app's owner needs Spotify Premium, and Spotify limits development apps to five allowlisted users. See [Spotify's access requirements](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

## 1. Install on the phone

1. Download the APK from the link above using the phone's browser.
2. Open the downloaded file. If Android asks, allow this browser/file manager to **install unknown apps** or **Allow from this source**, then return to install. Wording varies by phone.
3. Open **Spotify Wake Probe**. You can disable the browser's installation permission again afterward.

Optional checksum check on a computer:

```powershell
Get-FileHash ./Hey-Sally-0.2.1-alpha.3-arm64.apk -Algorithm SHA256
```

Compare with `SHA256SUMS.txt` from the same release. The APK is signed with a dedicated public-release certificate. Future releases should use that same certificate.

**If you already have a private trial or self-built APK:** a different signing key under the same package prevents installation over it. Stop if Android reports a conflict. Do not uninstall merely to get past it: uninstalling loses Spotify settings, authorization and private diagnostics. Copy your diagnostic history and deliberately choose whether to migrate. The public release was not installed over the maintainer's differently signed private trial.

## 2. Set up your Spotify Developer app

Do this while stationary and unlocked.

1. Sign in to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard), create an application and enable Android SDK/Web API use where requested.
2. Add this redirect URI exactly: **`http://127.0.0.1:8765/callback`**.
3. If Android registration fields are offered, enter package **`com.steve.spotifywakeprobe`** and the **SHA-1 certificate fingerprint for this published APK**, listed in [RELEASE_SIGNING.md](RELEASE_SIGNING.md). Do not use a fingerprint from another APK/debug build.
4. Ensure your test Spotify account has access through that app's Users Management/allowlist. A login can succeed while later API calls fail for a non-allowlisted account.
5. Copy the application's **Client ID** into Spotify Wake Probe and tap **Save Client ID**. Never enter the Client Secret.
6. Tap **Authorize Spotify playback**, then complete Spotify authorization.
7. Tap **Authorize named music and private playlists**, then complete the second authorization. It enables named searches and private-playlist access.

## 3. Enable hands-free listening

1. Tap **Grant microphone and start probe**, and grant microphone permission. If needed, tap again after the permission prompt.
2. Enable notifications for the app in Android settings if they are disabled.
3. Tap **Open Default apps for assistant setup**, then select **Spotify Wake Probe** as the phone's **Digital assistant app**. This replaces your previous assistant while selected.
4. Choose your **Command language** (US/Australian/UK/New Zealand English, or the phone's English language). It applies on the next wake and does not change the local US-English wake model.
5. Check that status/notification says **Listening for Hey Sally**.

Alpha.3 requests microphone and notification permissions together when needed. Denying notifications does not prevent startup if microphone permission is granted; status warns when notifications are disabled. Denying the microphone prevents capture. This revised first-run/denial flow still needs testing on the exact release APK.

The microphone indicator stays visible because hands-free wake uses continuous local microphone processing. Stop the listener when another recording app needs the microphone. After reboot, unlock once before testing. If setup controls overlap system bars, scroll the button into the middle of the screen.

## 4. Use it

For every command, say **Hey Sally**, **wait for the beep**, then speak the command. The beep means the app is ready to listen; it is not confirmation of successful playback.

| What you want | Full voice sequence |
| --- | --- |
| Start/resume existing music | **Hey Sally** → beep → **resume** |
| Pause | **Hey Sally** → beep → **pause** |
| Next track | **Hey Sally** → beep → **next song** |
| Previous track | **Hey Sally** → beep → **previous song** |
| Named song | **Hey Sally** → beep → **play song Stand by Me** |
| Named artist | **Hey Sally** → beep → **play artist The Beatles** |
| Saved playlist | **Hey Sally** → beep → **play my playlist followed by its name** |
| Hear numbered playlists | **Hey Sally** → beep → **list my playlists** |
| Select one you heard | **Hey Sally** → beep → **play two** |
| More playlist names | **Hey Sally** → beep → **more playlists** |
| Liked Songs / DJ / Mix | **Hey Sally** → beep → **play Liked Songs**, **play DJ** or **play Daily Mix two** |

If Spotify was paused, **next song** does not explicitly start it; use **resume** first. If there is no music context to resume, start a song/playlist in Spotify or request named music. Wait for a spoken reply to finish before the next wake. Playlist numbers expire after three minutes.

See [all commands, alternate forms and limitations](TESTING_AND_DISTRIBUTION.md#every-current-command), including Radio and Local Files. This is a Spotify controller, not a general assistant for calls/messages or other phone functions.

## 5. Test and report a bug

Test while stationary. Confirm actual audible results, first with the screen on and then locked/screen off. Note whether music was playing, your audio route, repeated wake attempts, unwanted beeps and spoken failures. One repeated Hey Sally wake has already been reported; no overall success rate is established.

Open [GitHub Issues](https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement/issues). Include version **0.2.1-alpha.3**, phone/Android/Spotify versions, command language, lock/screen state, phone/Bluetooth output, steps, expected/actual result and approximate time with timezone.

In the app, tap **Refresh status** then **Copy diagnostic history**. Paste into a private note and review before sharing only relevant events. Do not post passwords, tokens, account emails or full ADB logs. The log holds the newest 1,024 events; **Start fresh diagnostic trial** clears prior history. Save it first if needed.

[Contribution and pull-request instructions](TESTING_AND_DISTRIBUTION.md#contribute-a-pull-request) are available for developers.

## Stop or remove it

Tap **Stop probe** to stop microphone listening. Select your previous digital assistant in Android's Default apps settings to end this app's assistant role. Copy diagnostics before uninstalling; uninstalling/clearing storage deletes them and Spotify authorization. Data is excluded from Android backup/transfer.

## Privacy and prerelease status

This alpha.3 release uses GPL version 3 only with a limited permission for its independently licensed libraries; see [LICENSE](LICENSE) and [LINKING_EXCEPTION.md](LINKING_EXCEPTION.md). The release includes the project source and build scripts. Earlier alpha.1/alpha.2 downloads are retired; copies already obtained retain their original MIT rights.

Wake recognition runs locally with Vosk. App-owned code does not save audio/transcripts. Commands may use the configured Android speech provider online, spoken playlist names go to the TTS engine, and music searches/authorization go to Spotify. Tokens are encrypted using Android Keystore. There is no Gemini integration or hosted AI backend.

This prerelease is non-debuggable and includes third-party notices in `assets/third-party/` plus a separate notices ZIP. It is not a Play Store release or a reliability guarantee. Exact release-build device installation, fresh Spotify registration and longer battery/audio/coexistence tests remain unverified; prior short functional tests used the debug build from the same application code. Read the release notes before testing.
