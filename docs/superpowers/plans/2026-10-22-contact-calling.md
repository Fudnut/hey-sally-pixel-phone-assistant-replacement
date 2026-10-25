# Contact Calling Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation, followed by independently authorised final review. Preserve the agreed rules in `PROJECT_HANDOFF.md` and the calls/texts spec.

**Goal:** Say Hey Sally, request a phone contact, resolve person/number and always obtain a final voice confirmation before submitting one ordinary call.

**Dependency:** Complete the normal-volume conversation gate in [car voice integration](2026-10-22-car-voice-integration.md). Pure contact/conversation rules may be implemented independently; do not claim car calling ready before that gate and an agreed recipient trial.

**Architecture:** Local on-demand contact lookup, a pure conversation controller, strict finalized-answer parsing and a small Android Telecom dispatch boundary. Reuse the shared car audio/conversation lifecycle. Phone owns actual call audio.

## Task 1: contact resolution

**Files:** create `ContactDirectory.java`, `ContactResolver.java` and `tests/ContactCallingCheck.java` under existing source/test roots; add `tests/check-contact-calling.py`.

- [ ] Write failing cases for duplicate names, same-label numbers, missing/custom labels, explicit mobile/home/work, unknown names, number edits and shared numbers belonging to different people.
- [ ] Query Contacts Provider only on demand. Resolve person before number; present bounded numbered choices when needed. Deduplicate equivalent numbers only within one person. Use exact/explicitly confirmed choices; never reuse fuzzy music matching.
- [ ] Preserve emergency leading zeros. Reject unsupported MMI/service/post-dial sequences without stripping them into another number. Do not save names, numbers or recognition transcripts in diagnostics or hints.

## Task 2: confirmation conversation

**Files:** create `CallConversation.java` and `ConversationAnswer.java`; extend the focused check.

- [ ] Test person -> number -> final prompt -> ready answer -> consumed confirmation -> one dispatch. Even one contact with one number requires final voice confirmation; expected replies need no wake phrase.
- [ ] Accept only a clear finalized affirmative for the current successfully completed prompt. No partial fallback, contradictory primary answer, lower-ranked yes, stale/duplicate callback or expired request may dispatch.
- [ ] Apply the shared 10-second ready-answer timeout and 90-second conversation deadline. Cancel on no/cancel, new request, permission loss, failed/interrupted prompt, recognition failure, route loss, incoming call or service restart.
- [ ] Consume the confirmation before dispatch and recheck the exact selected contact/number immediately beforehand. Changed data requires a new prompt/confirmation. No retry after uncertain submission.

## Task 3: Android setup and one-call handoff

**Files:** create `AndroidContactDirectory.java`, `PhoneCallDispatcher.java`; modify `VoiceCommand.java`, `WakeService.java`, `MainActivity.java`, `AndroidManifest.xml`.

- [ ] Add optional contacts/calling setup using READ_CONTACTS, CALL_PHONE and existing READ_PHONE_STATE. Denial leaves music usable. Verify hands-free SIM selection on this Pixel; an unresolved SIM chooser blocks readiness.
- [ ] Add calling intent parsing without changing music parsing. Keep all conversation output/capture inside the quiet car session.
- [ ] Close Sally recognition/TTS and release its speech-route ownership before Telecom submission. Log only CALL_REQUESTED for a submission, never connected. Bound recovery without redial. Preserve incoming-call suspension and post-hang-up listening recovery.
- [ ] Recognize explicit 000 separately from contacts. With the existing Phone app retained, prepare ACTION_DIAL tel:000 and clearly describe its required system confirmation. Do not silently become default dialer or claim hands-free emergency placement. Test dispatch with doubles only.

## Task 4: evidence and delivery

- [ ] Run focused resolver/conversation/dispatch regressions plus existing music, wake, car route and call interruption checks; build/lint debug and release.
- [ ] Independently review the source before private device acceptance. Preserve signer and preferences when installing an authorised trial.
- [ ] Obtain an explicitly agreed ordinary test recipient before any real call. Verify both directions of car audio, quiet selection/confirmation, one submission, real incoming-call interruption and post-hang-up recovery while parked. No emergency test call.
- [ ] Update setup, limitations and handoff; keep all contact/device data excluded. Commit/push only when directed.

**References:** [TelecomManager](https://developer.android.com/reference/android/telecom/TelecomManager), [Contacts phone data](https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone), [default Phone requirements](https://developer.android.com/develop/connectivity/telecom/dialer-app).
