# Public project handoff

## Resume here

- Repository: https://github.com/Fudnut/hey-sally-pixel-phone-assistant-replacement
- Branch: `main`.
- Read `README.md` for build/setup, commands, privacy and limitations, and `THIRD_PARTY_NOTICES.md` for dependency terms.
- This repository starts from one cleaned public source baseline. Earlier private development history and personal test records are excluded.
- Current project source is GPL version 3 only (`GPL-3.0-only`) from the 10 October 2026 licensing checkpoint. Earlier alpha.1/alpha.2 releases retain their MIT terms. The baseline uses a generic contributor identity rather than a personal email.

## Next session: full review before the limited test release - 10 October 2026

Steve requests a full review of all work currently done and a clear list of anything that needs fixing, changing or addressing before the final APK for a limited test release. Start with review and a written recommendation before further implementation or publication. This section takes precedence over older next-step instructions below.

### Read and establish the current state

1. Read this handoff, then README.md, INSTALL.md, TESTING_AND_DISTRIBUTION.md, RELEASE_SIGNING.md and THIRD_PARTY_NOTICES.md. Claude's README checkpoints 5b9a4b7 and 8c593ed are incorporated below. Preserve his work and any further edits; review the latest completed version before the release decision. Leave README changes to Claude.
2. For the local review, read the ignored claude-audit/HANDOVER_TO_CODEX.md, TRACKER.md, LOG.md and AUDIT_2026-10-10.md. Use the latest tracker/log rather than repeating the original completed Do now list or answered questions. Never force-add private audit files.
3. Verify the branch, working tree and remote before reviewing. Source main was `1148f33` when this handoff was refreshed, including Claude's README/banner/Bluetooth checkpoints and the previous review handoff. Re-resolve HEAD to include this handoff and any later Claude changes. Review the full current implementation and the changes since published alpha.1 (`1f0f9b7..HEAD`), rather than only the most recent fixes.
4. Distinguish source, staged APK and published APK. Alpha.1 remains the public download. The unpublished alpha.2 review APK at .tools/releases/v0.2.1-alpha.2/Hey-Sally-0.2.1-alpha.2-arm64.apk embeds source `d803b5b35beba3286b7c80913a593ec5d933332e` and has SHA-256 `a3113817bb1a782b3ef1ac9396a68de2c935f3677ed9e27d52504825d05f0240`. It precedes subsequent documentation/banner changes and is a review artifact, not an approved final release. Earlier review bundles are preserved under review-1d12354 and review-79b0988.

### Latest Claude review and README updates

- Claude's latest review of a96e370/19f0367/d803b5b reports the existing Java checks passing but adds **N-07 and N-08 as open**. The N-05/N-06 fixes are not fully cleared for the final APK while these follow-up regressions remain unresolved. Source inspection confirms the relevant parser/arbitration and setup classification paths; reproduce them with focused tests during the full review.
- **N-07:** a top recognition alternative such as `play number tree` becomes an auto title search and can beat a correct lower `play number two` alternative in WakeService.onResults. Trailing `please` or punctuation after a number also becomes a title search. Evaluate numeric-only filler normalization and a bounded alternative-selection rule. Add a regression for multiple recognition alternatives, preserving genuine number-prefixed titles such as `number of the beast`, explicit numeric selection and browse expiry. Do not broadly prefer every number alternative over a genuine title.
- **N-08:** setupDetail reports REMOTE for the app's own authorization-declined, state-mismatch and unexpected-callback errors. Restore fixed setup-only details for those cases without exposing provider text, tokens or URLs. Test both these local failures and the existing safe reason/HTTP status handling; keep ordinary failure speech generic.
- Treat N-07/N-08 as priority items to verify and resolve before producing the final limited-test APK. Report them in the full review rather than silently treating the existing tests as coverage; any authorized fixes need separate commits and the required Java gate. Rebuild and reverify the APK afterwards.
- README now includes a banner, the current Spotify-only scope, Bluetooth setup and a clearly unbuilt assistant roadmap. The app uses the phone microphone and normal media routing; it does not pair Bluetooth devices, auto-start on connection, set up a car/headset microphone or integrate with Android Auto. Roadmap candidates are not current features or authorization to implement them.
- README commit 8c593ed records short Pixel 8 Pro tests over Bluetooth in a car and with connected devices at home. Preserve these reported results without inferring success on every car/headset, ready-beep route, silent/DND configuration or the exact public-release APK. F-06 and R-06 still require the remaining evidence; do not close them from README wording alone.
- Review the README's new platform, Google integration and regulatory statements against dated authoritative sources; distinguish platform restrictions, untested behavior and features merely outside current scope. Reconcile its claims with the command/install/privacy docs and the remaining tracker items.

### Review scope and required result

- Recheck each completed audit fix against current code and meaningful regressions, including number-title/playlist intent, natural controls, notification grants/denials, generic spoken failures, setup reason/HTTP status, connection timeouts/late callbacks and private wake aggregation. Do not accept a fixed label as proof.
- Review the whole user flow: installation, own Spotify Client ID registration, both authorization paths, wake/beep/command/reply recovery, audible playback, song/artist/playlist/special requests, lifecycle and supported Android/Bluetooth conditions. Examine privacy/security, token/error handling, dependency/license provenance and any regression or misleading promise.
- Triage every remaining finding and any new finding for a **limited test release**. Identify release blockers, advisable fixes before the test, limitations that may be accepted with clear instructions, and later work with reasons. Include R-07/W-02 offline-command claims, R-03 identity/branding, R-06 exact-APK validation, N-03 cold-start timeout and the other pending device findings. A proposed refactor is not automatically a release blocker.
- Check that README/install/command/privacy/bug-report instructions match the implementation and exact downloadable artifact. Reconcile old handoff statements with the latest evidence. Verify the pinned SDK/model inputs, packaged notices, build/signing manifest, certificate continuity, alignment, checksums and source provenance. State whether checks used the existing cache or a fresh environment.
- Run ./tests/check-playlists.ps1 and relevant focused checks. Existing local OAuth/controller harnesses are .tools/audit-token-check.py and .tools/audit-f05-check.py. Record what was actually run and what remains unverified. Device evidence must come from real observations; successful callbacks, mocks and lint do not prove audible or locked-screen operation.
- Deliver a review report with severity, file/line evidence, reproduction or verification, proposed remedy, and an ordered pre-release action list. Give a go/no-go recommendation for the limited test release, listing unresolved blockers, accepted risks requiring Steve's decision, and the exact device/test evidence needed. Update the private tracker/log with verified statuses; do not silently close device findings.
- After review and any authorized fixes, rebuild the final APK from the final committed source and verify its embedded commit and assets. Keep versionCode/release metadata consistent, publish accurate known limitations and tester installation/bug-report instructions, and seek Steve's release decision on the concrete verified bundle. Do not publish, tag or install the current review APK merely because it exists.

### Preserve the agreed boundaries

Keep the exact wake phrase Hey Sally, package com.steve.spotifywakeprobe, launcher label Spotify Wake Probe, public certificate and existing signing inputs. Steve explicitly requested continued use of the current signing location; backup details belong only in private audit notes. Relabeling and the Pixel migration test are separate tasks. Do not uninstall/overwrite the Pixel trial, move/copy keys, expose credentials/private diagnostics, change contributor identity, rewrite history or add paid services. Keep Claude's README work intact. If later fixes are authorized, use one commit per item, run ./tests/check-playlists.ps1 before every commit, stage only relevant files and push checkpoints without force.

## Current implementation

The Android app is still named **Spotify Wake Probe**, its package is `com.steve.spotifywakeprobe`, and its wake phrase is **Hey Sally**. The activation grammar, exact matcher, setup instructions, notifications and retry replies now use the new phrase. The previous Hey Spotify phrase is rejected by the matcher. On 10 October the user confirmed Hey Sally activation and audible resume/next playback. One next-song attempt needed two wakes; screen/lock state was not separately confirmed. A signing-compatible local test APK has now updated the existing Pixel installation without resetting app data.

The app provides hands-free wake detection, Spotify playback controls, song/artist/playlist lookup, spoken playlist pages and numbered selection, Liked Songs, DJ and Daily Mix requests. Radio matches stations in recommendations or saved playlists. Saving a missing station in Spotify and retrying has passed a user test. Native Local Files playback was not established; a playlist named Local Files is the existing fallback.

Wake detection uses offline Vosk with protected microphone capture. Commands use Android's speech recognizer; the audit findings about offline command fallback and recognizer dependence remain unresolved (R-07/W-02). Spoken replies use media audio routing; car-speaker output has passed a short check. The phone keeps up to 1,024 private, redacted diagnostic events without saving recognized words or audio.

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


## Audit fixes and alpha.2 checkpoint - 10 October 2026

- Completed six ordered audit items in separate commits: removed personal artist aliases/hints (`a103e59`); runtime notification request and disabled-notification status (`8c643e8`); numeric song fallback outside an active playlist browse (`da835cb`); natural control phrasing with title preservation (`d1890b4`); generic spoken failures and bounded Spotify connections (`fb39c7a`); private wake classification/timing and debounce diagnostics (`a75e13a`).
- Version is now 0.2.1-alpha.2, versionCode 4. Package, launcher label, exact Hey Sally phrase, five-second wake debounce and release signing configuration remain unchanged. The Pixel installation was not touched during this audit.
- Required Java checks passed before each commit. A deterministic local controller harness covered 12-second playback-connect timeout, late callbacks, cancellation after success, generic errors and synchronous failures; manual authorization allows 120 seconds for consent. Offline release assembly and Android vital lint passed. New permission behavior, audible failure speech and wake reliability still need device verification; INSTALL step 3.2 remains unchanged until then.
- Alpha.1 remains the published GitHub download. README and the command guide identify which behavior belongs to alpha.2 source. Alpha.2 publication requires Steve's approval after reviewing the prepared release artifacts; no alpha.2 tag or release was published during this audit.
- Alpha.1 reproducibility check: a fresh public clone at tag 1f0f9b7 built using checksum-verified SDK/model inputs and the existing dependency cache. Its classes.dex is byte-identical to the downloaded published APK. All 18 notice texts match; 16 differ only in Windows checkout line endings. This does not establish whole-APK byte reproducibility.
- Next work is review and a separately authorized release migration/device test. App relabeling is deferred to another task. Retain the existing public release certificate and make a portable encrypted offline signing backup outside the repo; no signing files were moved or copied in this audit. Other audit findings remain open.


## Claude review follow-up - 10 October 2026

- N-01 fixed in 3680769: routine other/empty wake results produce five-minute count summaries and a partial summary on stop; accepted wakes and debounce rejections remain individual. Fresh trial clears pending counts. Privacy wording describes nearby speech/noise counts and remaining timing exposure.
- N-02 fixed in 480c691: explicit play number two/2 retains playlist intent without or after a list; bare play two/2 keeps numeric song fallback. This intentionally keeps word/digit recognition variants equivalent in intent rather than treating digits differently.
- N-04 fixed in 3ab3492: ordinary failures carry only AUTH/TIMEOUT/NETWORK/NO_MATCH/REMOTE diagnostic codes, derived from types and bounded cause chains; recognized words, URLs and SDK messages are not copied. Normal failure speech stays generic. HTTP token connections now close on error as well as success.
- N-03 instrumented in 9c494c7: App Remote connection outcomes include elapsed milliseconds and PLAYBACK/AUTH mode. Ignored late connections remain disconnected. Timeout thresholds stay 12 seconds for playback and 120 seconds for manual consent; cold-start performance/audible playback remains needs-device. The command guide has a separate cold-start comparison procedure.
- Required Java checks passed before every fix commit; regressions cover diagnostic bursts, trial reset, explicit number intent and safe failure classification. The deterministic controller/SDK harness covers timing, timeout, late callbacks, authorization errors and sanitization. Offline release compilation and vital lint pass.
- Alpha.2 remains unpublished, versionCode 4. Its earlier reviewed artifacts from source 1d12354 are preserved privately under .tools/releases/review-1d12354; rebuild the current review bundle at committed HEAD under .tools/releases/v0.2.1-alpha.2 using the existing public certificate. No identity/signing configuration/Pixel changes, no new tag or release. All device claims still require real tests.

## Number-prefixed titles and setup error detail - 10 October 2026

- N-05 fixed in a96e370: `play number of the beast`, `play number one crush` and `play number 9 dream` retain their full title query, with or without an active playlist browse. Whole-number requests still select playlists under the existing explicit/bare-number rules.
- N-06 fixed in 19f0367: setup authorization failures expose only fixed reason codes and, for token endpoint failures, numeric HTTP status (for example `AUTH (HTTP 400)`). Ordinary spoken failures and diagnostic reports remain generic; provider messages, bodies, tokens and URLs are not copied into setup errors.
- Both regressions failed before the fixes. Required Java checks passed before each commit. Local mock token checks cover 400/401/403/429/500/503 and connection cleanup; the deterministic controller harness covers setup AUTH/NETWORK/TIMEOUT, consent timeout and earlier connection/race behavior. Source commits are pushed to public main.
- Preserve the reviewed source 79b0988 bundle under .tools/releases/review-79b0988 alongside review-1d12354; rebuild the current local alpha.2 review bundle at committed HEAD using the existing public certificate. Alpha.2 remains unpublished with versionCode 4; alpha.1 remains the public download. No identity/signing configuration or Pixel changes. Exact-APK/device verification remains pending.
- Steve independently completed signing recovery backups and a restore test; R-02 is closed by Steve. Original signing inputs remain for the scripts. An additional offline USB copy is optional. No keys were moved/copied by Codex.


## Full review corrections - 10 October 2026

Steve authorized the necessary corrections, commits and pushes after the full review. N-07 is fixed by numeric-only filler normalization and bounded recognition-alternative selection, preserving number-prefixed titles. N-08 retains fixed local OAuth setup details without provider messages. N-09/N-10 ordinary operations now cancel on new wake/command and service stop, guard late side effects and have a 30-second completion deadline; the 12-second connection and 120-second manual-consent limits remain. N-11 preserves Unicode names, folds accents and rejects empty matches. N-12 checks microphone permission at AudioRecord creation; full release lint passes. Focused parser/controller checks pass; exact-device behavior remains unverified.

Claude's README banner, Bluetooth observations, roadmap and structure are preserved. Narrow factual corrections clarify app-function permissions, OAuth assessments, Android verification rollout, Android Automotive versus Android Auto and the command fallback boundary. Artist single-track and fuzzy saved-playlist matching limits are documented. No relabeling, package/wake/signing change, key relocation or Pixel action was performed. The existing identity is retained at Steve's request; Spotify naming guidance remains an accepted/deferred release risk, not a cleared compliance finding.

The final limited-test bundle must be built from committed source with embedded commit verified, signed with the unchanged public certificate and checked for model/notices, alignment and checksums. It remains experimental: fresh public-APK installation/registration, permission denial/grants, locked-screen audible playback, cold starts, long-duration wake/battery and call/camera/recorder coexistence need actual device observations. A successful local harness or callback does not close those tests. The private full review and tracker retain evidence and outstanding device gates.


## Final alpha.2 limited-test release preparation

The current download/install/command documentation targets 0.2.1-alpha.2 (versionCode 4); alpha.1 remains an earlier release. The five intended release assets are the signed ARM64 APK, third-party notices ZIP, INSTALL.md, RELEASE_SIGNING.md and SHA256SUMS.txt. The tag must identify this committed source, and the APK's embedded version-control metadata must match it. Release notes must disclose that the exact public APK has not received a fresh installation/registration or audible locked-screen test. All existing device findings stay open for the limited test; the differently signed Pixel trial is untouched. No signing key or credential belongs in an asset.


## GPL licensing checkpoint - 10 October 2026

Steve requested changing the project licence to GPL after discussing GPLv3. Current source is GPL-3.0-only; LICENSE and the embedded Project-GPL-3.0-LICENSE.txt contain the standard SPDX GPLv3 text. The earlier Project-MIT-LICENSE.txt is retained as historical copyright/permission attribution, and third-party terms are unchanged. README, contribution guidance, install notes and third-party provenance identify the transition. Earlier alpha.1/alpha.2 tags/APKs retain MIT; no published asset, release/tag or application identity/signing/Pixel installation was changed for this checkpoint.

Before the next GPL-covered binary release, satisfy the complete Corresponding Source requirement for the actual combined program, including required dependency source/build scripts. The precompiled Spotify SDK and exact native-library provenance are not resolved merely by publishing licence texts or the project repository. Treat this as a release preparation gate; do not claim the existing MIT APK has become GPL or reuse the old notices-count checks without accounting for the added GPL asset.


## Alpha.3 GPL release and retirement request - 10 October 2026

Steve requested updated alpha downloads and removal of the old ones, and approved GPLv3 with the limited third-party library exception after the exact pinned Spotify App Remote repository was found to contain no Java/Kotlin implementation source. LINKING_EXCEPTION.md records the GPLv3 section 7 permission for the listed independent SDK/native/runtime libraries; Hey Sally's own code remains GPL-3.0-only and its source/build scripts must accompany the binary. Source headers and the APK notice assets reference the permission. Third-party terms remain intact.

Alpha.3 is versionCode 5 / 0.2.1-alpha.3. Build/sign from final committed source with the same package, launcher label, Hey Sally wake and public certificate, using unchanged signing inputs. Publish GPL text, exception, corresponding project source/build guide, notices, install/signing docs and checksums with the verified APK. Verify new downloads before deleting the old alpha.1/alpha.2 GitHub releases and tags; preserve local public-asset archives and commit history. Deletion does not revoke MIT rights in copies already obtained. Exact-device observations remain outstanding. No Pixel action, signing-key move/copy or history rewrite is part of this release.
