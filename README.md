# Smart Exam Grader for Android

Teachers can generate an A4 answer sheet for any whole number of questions from **1 to 100**, with four choices (A–D) per question. A printed strip records the form version and question count. The Android scanner reads the strip from a camera frame or imported photo, including on a blank sheet.

## Create and scan a sheet

1. In **Scan**, tap **Create answer sheet**, enter a count such as **37**, then tap **Create PDF** and choose a destination. Print the single-page PDF at **100% / actual size** on portrait A4. Keep the six square alignment marks and the count strip visible.
2. In **Key**, check the correct A–D answer for each question and tap **Use this answer key**. The app requires confirmation of a key for the detected count before saving a grade. For two different tests with the same number of questions, check the key again.
3. Fill the seven student-ID columns, including any leading zeros. Mark one choice per question.
4. Choose **Auto** in **Scan** and use the live camera or import a photo. Check the displayed question count. If the count does not match the printed heading, use a clearer photo showing the entire sheet. For an uncertain ID, blank, multiple or weak mark, tap **Review / save grade**, compare the reading with the paper, and enter the reason for any review.
5. Open a saved grade in **Class** to see the original reading, final answers and key snapshot. Optional auto-save starts off. It requires three matching clear frames. A different reading for the same ID, template and key requires manual confirmation to add an attempt.

Older unencoded Sheet-X 10Q and 16Q sheets use their corresponding manual presets and **Legacy A4 PDF**. The count strip on newly created sheets identifies the layout. It carries no student information or unique exam identity.

## Build and test

The Android app uses the Java camera and image-analysis implementation. Both the PDF writer and scanner read the custom form geometry from `CustomSheetLayout`. The old Kotlin and duplicate Java sources are under `legacy/`. [Build and test instructions](BUILDING.md)

For digital checks, render the actual generated PDFs, scan the blank pages, add known marks to the rendered images, and compare every detected answer, the count, and the student ID. The test tool supports every count from 1 to 100. For physical checks, print selected low-, medium- and high-count forms on classroom printers, complete them by hand, and test the camera with representative phones and lighting. Record missed or incorrect readings and the cases sent for review before using results for real grades.

## Scope and distribution

This pilot uses one local gradebook. The template and key snapshot distinguish readings within it. Exams with identical keys and layouts have no separate identities. Use demonstration data while testing. Teacher accounts, roster verification, exam IDs on paper, versioned regrading, cloud sync and paid subscriptions are future product work.

The release plan targets Saudi Arabia on Google Play. Use Play App Signing with a fresh private upload key and server-verified Play Billing for monthly and annual Pro subscriptions. Production release still needs physical acceptance testing, Arabic localization, privacy and deletion flows and Play Console configuration. The APKs in the original repository predate these changes.

Earlier design notes and unverified performance claims are in [the historical README](legacy/README-v2.3.md).
