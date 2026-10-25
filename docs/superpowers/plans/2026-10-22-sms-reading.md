# Incoming SMS Announcement and Reading Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation, followed by independently authorised final review.

**Goal:** Automatically announce an incoming SMS, ask whether to read it, and read only after a clear affirmative answer through the car microphone.

**Dependency:** Complete the conversation gate in [car voice integration](2026-10-22-car-voice-integration.md). Reuse strict finalized answers and the shared quiet audio session; avoid a second competing conversation/recorder.

**Scope:** SMS in the Messages app. RCS, MMS and WhatsApp are outside this first implementation. Keep the existing Messages app; do not change the default SMS role silently. No sending, deleting or marking messages as read.

## Task 1: optional access and incoming identity

**Files:** create `IncomingSmsReceiver.java`, `SmsRepository.java`, `AndroidSmsRepository.java`; modify `MainActivity.java`, `AndroidManifest.xml`.

- [ ] Verify READ_SMS/RECEIVE_SMS installer allowlisting and runtime grant behavior for this private sideload on the Pixel. READ_SMS is hard restricted: a runtime dialog alone does not establish access. Use normal supported installer/setup mechanisms; denial must leave Spotify/calling usable.
- [ ] Receive only intended system SMS events and resolve a stable provider message identity, including multipart/duplicate delivery behavior. Do not start microphone capture from a background receiver in violation of foreground-service rules; hand work to the already-running, explicitly enabled Sally session or retain only bounded pending identities until eligible.
- [ ] Keep only the minimum pending identity in memory and query the intended body only after approval. Do not log bodies, sender identities, full broadcast payloads or provider rows. Do not write SMS bodies to app storage.

## Task 2: announcement, approval and queue

**Files:** create `SmsConversation.java`, `PendingSmsQueue.java`; add `tests/SmsReadingCheck.java`, `tests/check-sms-reading.py`; reuse `ConversationAnswer.java`.

- [ ] Write failing tests for new text -> generic announcement -> successful prompt completion -> ready answer -> read selected identity once. No body disclosure before approval.
- [ ] Accept only the current clear finalized yes. No/cancel/timeout, partial speech, contradictory alternatives, a failed prompt, superseding conversation or stale/duplicate callback must leave the body unspoken.
- [ ] Use a bounded queue of at most five identities with a ten-minute expiry; deduplicate messages and never silently switch the approved identity. Start with one message per approval. A missing/deleted/unreadable intended message produces a generic failure.
- [ ] Share conversation priority: real calls preempt everything; an active call request or expected answer is not interrupted by a new SMS. Defer announcements until idle. Invalidate pending approval on call/route loss, permission revocation or service restart; a later yes cannot resurrect it.

## Task 3: quiet car readout and recovery

**Files:** modify `WakeService.java` and the shared car/conversation components.

- [ ] Keep Spotify paused through the announcement, answer window and approved body readout. Open answer capture only after successful speech completion/drain. Respect the shared answer and overall deadlines, including long-message cancellation.
- [ ] Stop readout/capture immediately for a real call, cancellation or route loss. Release owned focus/routes and resume eligible wake listening without explicitly restarting Spotify after real-call interruption.
- [ ] Prevent the existing diagnostic history/last-command preference paths from persisting sender names, message contents or contact-calling transcripts.

## Task 4: evidence and delivery

- [ ] Run focused queue/approval/repository-failure/audio-ownership checks, existing call/music/wake checks, and debug/release build/lint. Review independently before acceptance.
- [ ] Arrange an agreed harmless test SMS while parked. Verify announcement, natural-volume yes and readout through the car, then no/cancel/timeout and call-interruption behavior. Do not collect unrelated inbox contents for testing.
- [ ] Record actual installer grants, feature limitations and private installation identity/data preservation. Update handoff; commit/push only when directed.

**References:** [READ_SMS restrictions](https://developer.android.com/reference/android/Manifest.permission#READ_SMS), [RECEIVE_SMS](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS), [SMS received action](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION).
