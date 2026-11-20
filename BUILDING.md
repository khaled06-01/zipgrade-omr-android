# Build the Android scanner

Install JDK 17 and Android command-line tools. Set `ANDROID_HOME` to your SDK directory. Install the platform and build tools, then run from the repository root.

```sh
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows use `gradlew.bat`. The wrapper verifies the Gradle 8.13 distribution SHA-256. The Android Gradle Plugin version is 8.10.1. The application compiles and targets API 36 and supports Android 7.0 (API 24) and later. Allow network access on the first build so Gradle and Robolectric can download their pinned dependencies and Android runtime images.

The Java implementation in `app/src/main/java/com/zipgrade/omrscanner` is active. Historical Kotlin and versioned Java copies are in `legacy/` outside the source set. The app runs OMR on the device. SDK and version metadata live in `app/build.gradle.kts`.

## Test build

Install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`. The debug application ID `com.zipgrade.omrscanner.debug` gives it its own app data and allows installation alongside existing installations. The Android toolchain generates a development signing key locally.

## Check count detection on generated PDFs

With JDK 17, the Android 36 SDK and Poppler's `pdftoppm` on your path, run the form check from the project root. Pass an empty directory for generated artifacts. Supplying question counts limits the run to those forms.

```sh
tools/forms/check-printed-sheets.sh "$(mktemp -d)" 1 37 100
```

Omit the counts to check all 100 supported values. The tool writes each PDF with the production writer, rasterizes its page, and sends image data to the camera analyzer in Auto mode. It checks that blank sheets retain their encoded counts and contain no answers or student ID. It then fills the answer and ID positions digitally and compares the detected results with the expected marks. The Gradle suite also covers the layout code, camera-image fixtures and the Android creation/review flow.

For a device check, create a 37-question PDF in the Scan tab and print it at actual size on A4. In Key, set the expected answers and tap **Use this answer key**. Photograph the entire page in Auto, first blank, then completed. Check the detected 37 questions, student ID, question-level marks, review prompts and saved result. Repeat with 1 and 100 questions and more than one printer and phone. Digital image tests do not measure camera optics, printer scaling or pencil-mark quality.

## Google Play

Run `./gradlew :app:bundleRelease` for an **unsigned** App Bundle at `app/build/outputs/bundle/release/app-release.aab`. The release application ID is `com.zipgrade.omrscanner`. Before uploading, set up Play App Signing and sign the bundle with a fresh protected upload key. The previously tracked release keystore is considered exposed. Keep signing secrets outside Git. For an existing Play listing, follow its established signing configuration.

Use the internal test track to verify camera permissions, edge-to-edge and large-screen layout, real prints, and update behavior on representative devices. Unit tests and static checks cover a different set of failure modes from a physical camera test.
