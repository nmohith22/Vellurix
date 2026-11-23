# Vellurix regression tests

The expected behavior comes from the development tracker. When historical entries conflict, use the later behavior and the current product scope. For example, 0.1.27 supersedes the earlier wide portrait exception for Double layout.

## Run

Use JDK 17 and Android SDK 36 to build. Boot a fresh Android emulator with a document picker. The suite uses API 33 in CI and refuses physical devices or an emulator with an existing Vellurix installation. The APK may be a debug build or a signed release.

```sh
bash gradlew testDebugUnitTest assembleDebug
python -m venv .tmp/regression-venv
.tmp/regression-venv/bin/pip install -r tests/requirements.txt
.tmp/regression-venv/bin/python -B tests/android_regression.py --serial emulator-5554 --apk app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`, `py -3 -m venv`, and the executables in `.tmp\regression-venv\Scripts`. Use `--adb` if the Android platform tools are outside PATH. Run the script with `--help` for the available `--test` filters. The suite generates synthetic books and saves failure screenshots and the emulator's crash log under the ignored `.tmp/android-regression` directory.

## Coverage from the tracker

| Tracker behavior | Verification |
| --- | --- |
| EPUB cover extraction, search, shelf creation and long press rename, last shelf, and removal without deleting the source | Installed app library flow |
| New folder books appear on resume and removed books stay excluded | Adds a real file to the selected folder, reopens the app, and checks the exclusion after another manual scan |
| Supported EPUB, PDF, TXT, HTML, HTM, FB2, and basic RTF formats | Direct EPUB import and folder discovery through Android's picker, followed by opening books, checking displayed content, and returning to the library. This reproduces the 0.1.28 crash when leaving a text document before scrolling. |
| Reader opens with controls hidden, bookmark and resume survive a relaunch, and chapter count agrees with book position | Installed app reader flow |
| Double mode follows the viewport, the 0–64 dp gutter stays centered and clear, and columns stay aligned across page turns | Measures both column widths, the midpoint, and gutter pixels on an 820 × 700 viewport and an odd-width 1851 × 1553 viewport at 360 dpi. The fixture includes long unbroken identifiers. |
| Supported file policy, navigation rules, bookmark toggling, progress boundaries, shelf fallback, appearance inheritance, and backup matching | JVM checks under `app/src/test` |

## Remaining release coverage

| Tracker behavior still requiring a targeted check | Expected result |
| --- | --- |
| Revoke the selected folder's grant and try a manual scan. Repeat using alternate document providers. | The revoked grant leaves existing entries intact, explicit imports remain usable, and a renewed grant resumes discovery. |
| Export a backup, rename the source book and import it from a different URI, then restore. | Progress, bookmarks, shelves, and overrides map to the same file bytes. Unrelated library data stays intact. |
| Install a newly signed APK over the previous release with existing data. | Android accepts the upgrade. Library, locator, bookmarks, themes, shelves, widgets, and file grants survive. |
| Move between physical foldable displays while reading and turn pages with all four transition options. | The reader stays open at the same location. Landscape Double stays aligned, portrait uses one column, system bars remain transient in the reader, and the library respects safe areas. |
| Configure and resize both widgets on the launcher, scroll the shelf widget, and tap book covers. | Both widgets show usable covers and open the selected book. A renamed shelf remains selected by its widget. One UI applies the monochrome icon mask correctly. |
| Change global and per-book appearance, including custom colors and OLED presets. | Inheritance, reset, and live preview work. Page and margin colors match. PDF pages preserve document colors. |
