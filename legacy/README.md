# Archived implementation experiments

These sources are retained for reference and are **not** part of the Android
source sets:

- `kotlin/com/zipgrade/omrscanner`: an incomplete OMR port and a WebView activity
  pointing at a localhost service that is not included in this repository.
- `java/com/zipgrade/omrscanv23`: a copy of the Java app under another package.

The production baseline is `app/src/main/java/com/zipgrade/omrscanner`.
The C++ and Rust experiments remain unwired to the Android build. Do not add any
of these implementations to a production source set without comparative tests.

Moving the files here prevents Java/Kotlin class-name collisions and accidental
shipping of a second application implementation. It does not migrate an already
installed app using the `com.zipgrade.omrscanv23` application ID.
