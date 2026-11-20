# Test generated answer sheets

Run the production PDF writer, rasterize the single A4 page with Poppler and send the grayscale pixels through the same Java analyzer used for camera frames. The test reads each blank form in Auto, then adds a synthetic student ID of 0033166 and a repeating A–D answer pattern to the raster, and checks the ID and every detected mark. It also checks that the 37-question sheet is rejected for an explicitly different layout or after damage to its count code.

Install Poppler (`pdftoppm`), JDK 17 and Android SDK platform 36. Set `ANDROID_HOME`. From the Android project root run:

```sh
tools/forms/check-printed-sheets.sh /tmp/teacher-forms-check
```

Use an empty directory. The default checks every count from 1 through 100. For a faster run, append specific counts, for example `1 17 37 73 99 100`. The 37-question PDF, blank/filled/damaged PNG files and blank/filled luminance samples are retained there. Copy both `.luma.gz` files into `app/src/test/resources/custom-v1/` to refresh the Gradle regression fixtures after intentional layout changes. Other counts' temporary files are deleted after they pass. These tests exercise software rendering and recognition; use separate physical print-and-photograph trials to measure camera performance.
