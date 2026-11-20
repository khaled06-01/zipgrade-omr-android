These synthetic images exercise the Java camera analyzer. The 10-question images use the 600 × 780 canonical sheet. The 16-question images include the entire A4 page at 840 × 1188 pixels. Each set contains a sheet filled with repeating A/B/C/D answers and scan code 0033166, an unmarked sheet, and a sheet with multiple answers, a blank answer and two selected digits in the first ID column.

To regenerate them with the companion Sheet-X changes, run `npm run fixtures -- /tmp/scan-fixtures` in Sheet-X, then export the corresponding SVGs with Inkscape. For example:

```sh
inkscape --export-width=600 --export-height=780 --export-filename=sheetx_10q-clean.png /tmp/scan-fixtures/sheetx_10q-clean.svg
inkscape --export-width=840 --export-height=1188 --export-filename=sheetx_16q-clean.a4.png /tmp/scan-fixtures/sheetx_16q-clean.a4.svg
```

Print, photography and camera-device coverage require separate physical tests.
