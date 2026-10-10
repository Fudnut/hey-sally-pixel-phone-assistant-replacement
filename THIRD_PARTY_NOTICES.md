# Third-party components

The current project source as a whole is licensed under GNU GPL version 3 only (`GPL-3.0-only`); see [LICENSE](LICENSE) and the [limited third-party linking permission](LINKING_EXCEPTION.md). Copyright (c) 2026 Spotify Wake Probe contributors. This does not replace third-party licences or Spotify's service terms.

The alpha.1/alpha.2 releases were published under MIT and remain under those terms. `Project-MIT-LICENSE.txt` preserves the earlier copyright/permission notice; it does not offer subsequent GPL-only changes under MIT. `Project-GPL-3.0-LICENSE.txt` embeds the GPL text and `Project-LINKING-EXCEPTION.txt` embeds the approved additional permission. The previous alpha.1/alpha.2 release downloads and tags are retired in favour of alpha.3. Dependencies retain their existing notices and licences, including JNA's Apache 2.0 option.

The alpha.3 release supplies Hey Sally project source and build scripts alongside the APK. Under the limited linking permission, the listed independent libraries may remain precompiled and separately licensed without including their implementation source in Hey Sally's Corresponding Source. Their own licensing obligations still apply. Input versions, hashes and notice provenance remain documented; this permission does not claim exact upstream native source revisions are known. Rebuild instructions are included in SOURCE_BUILD.md.

## Files distributed in this source repository

- **Gradle 9.3.1 wrapper:** generated wrapper scripts and JAR. The scripts retain their upstream headers. Upstream licence text, including its bundled-component notices, is preserved in [licenses/Gradle-LICENSE.txt](licenses/Gradle-LICENSE.txt), obtained from [the versioned upstream source](https://github.com/gradle/gradle/blob/v9.3.1/LICENSE).
- Wrapper JAR SHA-256: `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`. Verify against [Gradle's official checksum](https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar.sha256).

## Components fetched when building

These libraries and the model are not checked into this source repository. Their licences and any applicable notices must accompany redistributed binaries as required by their respective terms.

| Component | Version | Upstream licensing/provenance |
| --- | --- | --- |
| Spotify App Remote SDK | 0.8.0 | [Official release](https://github.com/spotify/android-sdk/releases/tag/v0.8.0-appremote_v2.1.0-auth), [upstream licence](https://github.com/spotify/android-sdk/blob/master/LICENSE), [SDK terms reference](https://github.com/spotify/android-sdk/blob/master/app-remote-lib/README.md#terms-of-use) |
| Gson | 2.13.2 | [Apache 2.0 licence](https://github.com/google/gson/blob/main/LICENSE) |
| JNA | 5.18.1 | [Upstream dual-licensing and bundled notices](https://github.com/java-native-access/jna/blob/master/LICENSE) |
| Vosk Android | 0.3.75 | [Upstream licence](https://github.com/alphacep/vosk-api/blob/master/COPYING) |
| Vosk small US English model | 0.15 | Apache 2.0, as listed on the [official model page](https://alphacephei.com/vosk/models) |

The SDK download script checks SHA-256 `b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198`; the model download script pins its own SHA-256. Gradle downloads the other dependencies from the configured repositories.

The alpha APK includes licence/notice texts in [app/src/main/assets/third-party](app/src/main/assets/third-party), also supplied as a GitHub release notices ZIP. The inventory below covers the Gradle release runtime classpath and conservatively includes native build inputs; links and fetched text provenance are recorded in SOURCES.txt. JNA is redistributed under its Apache 2.0 option.

Release runtime inventory: Spotify App Remote 0.8.0; Gson 2.13.2; Error Prone annotations 2.41.0; JNA 5.18.1 (including libffi); Vosk Android 0.3.75; Kotlin stdlib 2.2.10; JetBrains annotations 13.0; and the embedded Vosk small US English 0.15 model (Apache 2.0, with Vosk licence text included). Spotify upstream NOTICE includes its Jackson/Gson notices. Vosk Android upstream build inputs include Kaldi, OpenFST, OpenBLAS 0.3.20, CLAPACK/libf2c and statically linked C++ runtime; their licence texts are included. Exact internal native source revisions are not encoded by the Maven artifact; these notices are preserved conservatively rather than claiming a source reproducibility audit.

Spotify is a trademark of Spotify AB. This is an independent experimental project, not an official Spotify product.
