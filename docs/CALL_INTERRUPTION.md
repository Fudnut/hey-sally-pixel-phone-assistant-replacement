# Incoming call interruption (unreleased source)

The listener now observes phone state and Android call audio modes. On a ringing or active call it cancels pending Spotify operations, stops recognition and spoken replies, abandons assistant audio focus, and sends a media **pause** key to the active player. It does not change the car's audio route or volume. After calls end, wake listening returns; Sally does not explicitly resume Spotify. Say **Hey Sally**, wait for the beep, then **resume** if music remains paused.

Open the updated app and tap **Grant permissions and start probe**, then allow **Phone** permission. This allows ringing calls to be detected even before the call audio route opens. The permission is used for call state only; this change does not read contacts, call logs or caller numbers. Declining Phone permission keeps Spotify commands available, with interruption based on Android call audio modes. Some third-party calling apps may not expose those modes.

The monitor checks existing calls when the service starts and checks Telecom's combined call state before restoring listening, including when another SIM still has an active call. Incoming state broadcasts also block capture while Telecom's state catches up. Pending recognizer/reply/retry callbacks cannot reopen capture during a call. A denied audio focus request no longer starts command recognition.

## Local checks

With JDK 17+ on PATH and ANDROID_HOME set:

```powershell
.\tests\check-playlists.ps1
python -X utf8 .\tests\check-controller.py
python -X utf8 .\tests\check-call-interruption.py
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

The call check exercises the real WakeService and CallAudioMonitor with deterministic Android, microphone and player boundary doubles. It covers incoming interruption, media pause keys, cancellation, reply/focus cleanup, blocked retries/commands, service recovery during a call, delayed Telecom state, another active SIM, permission denial and observer cleanup. It cannot prove audible results in a car.

## Device check, while parked

1. Install a build signed with the same certificate as the existing installation to preserve its data. A debug build on another computer may use a different key; do not uninstall the existing app just to bypass a certificate mismatch.
2. Grant Phone permission, start the listener, connect the normal car Bluetooth route, start Spotify and lock the phone.
3. Receive a call. Confirm Spotify stops at ringing, Sally is silent, and both directions of call audio work through the car.
4. Hang up, then confirm **Hey Sally** works again. Repeat with a rejected or missed call, and with a call arriving during a Spotify command or spoken playlist reply. Confirm old work does not start music during the call.
5. If applicable, repeat with each SIM and with one call held while another ends. Check permission denial separately; early ringing detection requires Phone permission.

Safe diagnostics record CALL_INTERRUPTION_START, CALL_MEDIA_PAUSE_DISPATCHED (dispatch only, not proof of audible silence), CALL_MEDIA_PAUSE_ERROR and CALL_INTERRUPTION_END. No caller identity is recorded.

## Device result — 22 October 2026

The updated debug APK was signed with the original installation's key and installed over the existing app on Steve's Pixel 8 Pro. The installed APK matched the verified build, Spotify settings were preserved, Phone permission was granted, and LISTENING_READY was observed.

Steve then tested the incoming call behavior through his car system and reported that it worked. This is user-reported validation of the original issue in that setup. The additional permission, dual-SIM and interrupted-reply scenarios above remain separate device checks.
