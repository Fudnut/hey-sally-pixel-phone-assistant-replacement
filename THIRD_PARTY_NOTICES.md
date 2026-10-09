# Third-party components

The MIT licence at the repository root covers this project's original code. It does not replace third-party licences or Spotify's service terms.

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

This is a source-only release preparation. Local debug APKs are build outputs, not part of the public source tree. Before distributing an APK, review the actual packaged native/transitive dependencies and include their complete required licence/notice texts. This table is not a complete APK licence manifest.

Spotify is a trademark of Spotify AB. This is an independent experimental project, not an official Spotify product.
