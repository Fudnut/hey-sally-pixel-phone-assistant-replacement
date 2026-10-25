# Quiet Conversation Check

**User direction:** Test whether music remains quiet while Sally speaks and while capturing the user's reply in the parked Pajero.

**Scope:** Follow-up to the reviewed debug diagnostic, keeping its fixed 30-second deadline and explicit Start/Stop. Keep temporary audio focus after the phrase until session cleanup. This checks sound and microphone capture, not recognition of a call request or text-reading approval. No contacts, SMS or actual calls are used.

- [x] Add a regression that fails when speech completion releases focus before the reply capture finishes.
- [x] Retain focus through reply capture and update the phrase/screen to explain the quiet conversation check.
- [x] Check focus release exactly once for Stop, deadline, route loss, call interruption, focus loss, TTS failure and background exit; denied focus must prevent speech. Run focused regressions, build/lint and independent review. All focused checks and builds/lint passed; independent review found no actionable important defects.
- [x] Sign with the original key, preserve settings and install the private diagnostic update.
- [x] With Spotify already playing in the parked car, confirm separately that music is quiet during Sally's phrase and during normal-volume user speech; inspect actual Bluetooth input and Spotify focus/player state in both phases. Confirm music recovery afterward. Do not equate successful focus callbacks with audible silence. Steve confirmed both quiet phases and automatic recovery; snapshots showed PAUSED/PAUSED/PLAYING, actual SCO capture in both active phases and normal mode after cleanup.
- [x] Restore normal Sally listening and record evidence/limitations. No commit/push unless directed.
