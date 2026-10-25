# Handover for the next Sally session

Work was put aside at Steve's request on 22 October 2026. No background work or phone listener should be running for this handover.

## Paste this into a new chat in the existing folder

> Continue Sally in `C:\AI-Development\hay-sally`. Read `docs/NEXT_SESSION_HANDOVER.md`, then `docs/CHECKPOINT_2026-10-22.md` and `PROJECT_HANDOFF.md`. Preserve the existing local changes on `feature/car-audio-probe`; do not reset or re-clone the repository. Implement option 1: use the phone microphone only for “Hey Sally,” then pause music and use the Pajero's microphone and speakers for the whole conversation. Release the car route afterward so music returns to stereo, and return to phone wake listening. Driving operation must be voice-only. Add focused regression checks, preserve the incoming-call fix, review the transition, build and install a private update using the original signer while preserving Spotify settings, then arrange the parked test. Contacts and SMS are the larger objective, after this audio transition works. Keep me updated on account usage and save checkpoints before the allowance runs low.

## Start with these facts

- Workspace exists locally. Branch `feature/car-audio-probe`, HEAD `1cb3af6`. Car-audio work is uncommitted; inspect and preserve it. Do not automatically commit or push.
- Sally is force-stopped on the Pixel. Its installed build still uses **continuous car capture** when the saved car setting is enabled, producing mono music. Hybrid option 1 is not implemented. Do not restart that old mode expecting stereo between conversations.
- Successful parked evidence: real car mic/speakers, music quiet through speech and reply capture, PAUSE/NEXT, and screen-off NEXT. Continuous car capture keeps music mono between commands; stopping it previously restored stereo.
- The car's Speak button opened its own menu; no phone-side assistant request was observed. Do not spend the next session assuming that button can launch Sally.
- No hybrid source edits or failing tests were started before this handover. The detailed checkpoint records the intended transition, callback/cancellation hazards, tools, build verification, installed APK hash and private evidence paths.
- Original signing key is the existing ignored `.tools/debug.keystore`; the computer's default debug key has a different signer. Preserve app data. Never print preference contents or upload keys/device dumps/APKs to this public repository.
- Calling contacts and announcing/reading Messages-app SMS remain unimplemented. Always confirm ordinary calls by voice. No real recipient is agreed for testing, and 000 must never be called as a test.
- Device connectivity, phone unlock and car availability need a fresh check when Steve returns. Setup and physical trials take place while parked.

The account allowance/reset values in the detailed checkpoint are historical snapshots. Read current usage when resuming. A new chat uses the same account allowance.
