# Public APK signing identity

The GitHub alpha APK uses a dedicated public-release certificate. These fingerprints are public registration metadata, not signing credentials.

From 0.2.1-alpha.7:

- Android package: `com.steve.spotifywakeprobe`
- Certificate subject: `CN=Hey Sally public release, O=Fudnut` (RSA 4096, valid until October 2056)
- Certificate SHA-1: `5D:E7:7A:3B:37:6F:82:45:14:90:D9:32:6C:B4:B5:08:08:AE:77:FE`
- Certificate SHA-256: `83:D5:2E:98:51:08:00:FE:70:B3:5A:83:C4:5A:E4:4C:B3:7E:05:0D:94:82:0C:95:F8:14:65:42:B7:10:E3:7E`

Use this SHA-1 fingerprint if Spotify's Developer Dashboard offers Android fingerprint registration for the published APK. A self-built debug APK has its own fingerprint; use its signing report instead.

**Certificate change in alpha.7.** Alpha.4 to alpha.6 were signed with an earlier certificate (SHA-1 `80:15:A9:66:D9:B7:94:16:77:9B:B4:45:96:16:03:8E:2A:5D:D1:C7`, SHA-256 `A3:DF:4B:D2:E6:B8:3C:D3:08:8C:18:80:5C:8F:33:44:6C:0D:4F:FE:71:52:C3:92:A9:52:4B:BA:12:65:13:ED`). Its signing password could not be recovered on the current build machine, so alpha.7 introduces the certificate above. Android will not update an alpha.6 installation with alpha.7: uninstall the earlier APK first, which clears Sally's settings and Spotify authorization. No public installations were known when the change was made.

Public releases from alpha.7 must retain the new certificate and package identity for compatible updates, and increase versionCode. A separately signed private trial/self-built APK cannot be updated with this public APK under the same package. Do not uninstall an existing trial without saving diagnostics and making a deliberate migration decision.

## Maintainer signing process

1. Build and check the release: `./tests/check-playlists.ps1`, then `./gradlew.bat :app:assembleRelease` (offline only with dependencies cached).
2. Align the unsigned APK with Android build tools: `zipalign -P 16 -f 4 app-release-unsigned.apk aligned.apk`.
3. Sign with the existing dedicated release key using `apksigner`. Supply passwords through a secure prompt or an ephemeral environment reference, never literal command text or Git files. Do not generate a new key for each version.
4. Verify with `apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4`. Confirm the certificate above, package/version/ABI, non-debuggable manifest and all required notices/model assets.
5. Provide complete Corresponding Source for Hey Sally, including build scripts, alongside a GPL-covered APK. Include LICENSE, LINKING_EXCEPTION.md and all third-party notices; the approved linking permission governs the independent library binaries. See THIRD_PARTY_NOTICES.md and SOURCE_BUILD.md. Then create SHA-256 sums of the exact signed assets, tag their source commit, and publish only approved files to a GitHub prerelease.

The private key is kept outside the repository; neither it nor its password is in source or release assets. Keep the password in a password manager rather than only in machine-bound storage: the earlier certificate was retired because its password was encrypted for one Windows user and machine. Maintain a secure recoverable offline backup of the keystore and signing credential before relying on this identity for ongoing releases. Loss of the key prevents compatible updates under this identity.
