# Public APK signing identity

The GitHub alpha APK uses a dedicated public-release certificate. These fingerprints are public registration metadata, not signing credentials.

- Android package: `com.steve.spotifywakeprobe`
- Certificate SHA-1: `80:15:A9:66:D9:B7:94:16:77:9B:B4:45:96:16:03:8E:2A:5D:D1:C7`
- Certificate SHA-256: `A3:DF:4B:D2:E6:B8:3C:D3:08:8C:18:80:5C:8F:33:44:6C:0D:4F:FE:71:52:C3:92:A9:52:4B:BA:12:65:13:ED`

Use this SHA-1 fingerprint if Spotify's Developer Dashboard offers Android fingerprint registration for the published APK. A self-built debug APK has its own fingerprint; use its signing report instead.

Public releases must retain this certificate and package identity for compatible updates, and increase versionCode. A separately signed private trial/self-built APK cannot be updated with this public APK under the same package. Do not uninstall an existing trial without saving diagnostics and making a deliberate migration decision.

## Maintainer signing process

1. Build and check the release: `./tests/check-playlists.ps1`, then `./gradlew.bat :app:assembleRelease` (offline only with dependencies cached).
2. Align the unsigned APK with Android build tools: `zipalign -P 16 -f 4 app-release-unsigned.apk aligned.apk`.
3. Sign with the existing dedicated release key using `apksigner`. Supply passwords through a secure prompt or an ephemeral environment reference, never literal command text or Git files. Do not generate a new key for each version.
4. Verify with `apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4`. Confirm the certificate above, package/version/ABI, non-debuggable manifest and all required notices/model assets.
5. Before a future GPL-covered APK release, provide complete Corresponding Source for the distributed program, including applicable dependency source/build scripts; see THIRD_PARTY_NOTICES.md. Then create SHA-256 sums of the exact signed assets, tag their source commit, and publish only approved files to a GitHub prerelease.

The private key and Windows-user-encrypted password remain in an ignored local signing directory; neither is in source or release assets. Windows encryption is tied to the current user/machine and is not a portable backup. Maintain a secure recoverable offline backup of the keystore and signing credential before relying on this identity for ongoing releases. Loss of the key prevents compatible updates under this identity.
