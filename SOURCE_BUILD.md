# Build the alpha.3 project source

The release source ZIP contains the exact tracked project tree at the APK's embedded Git revision, plus this build guide and release provenance. Hey Sally is GPL-3.0-only with the limited additional permission in LINKING_EXCEPTION.md. Included independent libraries retain their licences; licence texts are in app/src/main/assets/third-party.

Requirements: Git, PowerShell, Python 3 for the controller check, JDK 17 or a newer version supported by Gradle 9.3.1, Android SDK API 37 and build tools. The verified build used JDK 25 and an existing dependency cache. This is not a claim of whole-APK byte reproducibility.

1. Extract the source ZIP, or clone the public repository and check out the source commit recorded in SOURCE_PROVENANCE.json in the release source ZIP.
2. Run `./fetch-spotify-sdk.ps1` and `./fetch-model.ps1`. They download the independent SDK/model and verify pinned SHA-256 values. The fetched inputs remain outside Git.
3. Set `ANDROID_HOME` to your Android SDK. Gradle obtains the pinned Java/native runtime artifacts from Maven Central/Google; the first build needs network access.
4. Run `./tests/check-playlists.ps1` and `python -X utf8 ./tests/check-controller.py`.
5. Run `./gradlew.bat :app:assembleRelease :app:lintRelease`. The unsigned APK is app/build/outputs/apk/release/app-release-unsigned.apk. `--offline` requires an already populated cache.
6. To install your own build, align/sign it with your own key using Android's tools, or build `:app:assembleDebug` for standard debug signing. The maintainer's private signing key is not part of Corresponding Source. A differently signed build cannot update an existing public/private installation under the same package; preserve diagnostics and deliberately plan any migration.

The limited linking permission covers the listed independent precompiled libraries; it does not remove GPL source obligations for Hey Sally's own changes. The source ZIP includes project Java, resources, Gradle/build/download/test scripts, wrapper and notices; no private keys, credentials, private audit logs or APKs are included. Third-party binary input hashes and runtime versions are recorded in the release source provenance. Spotify account authorization is needed to use the app, not to compile it.
