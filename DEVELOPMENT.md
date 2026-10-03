# Vellurix Development Tracker

**Status:** Core reader and library implemented; global and per-book reader appearance, update-safe preference handling, reader state restoration, lightweight progress saving, and cached widget artwork are in place. Unit checks and the release build pass.
**Updated:** 2026-10-03

## Product scope

Vellurix is an offline-first Android reader for phones and foldables. The interface takes visual cues from the local `workout_app` project. The target is the Samsung Galaxy Z Fold 8 and other phones, with layouts driven by available window size rather than fixed device dimensions.

## Implemented

- [x] Native Kotlin Android app using Jetpack Compose and Readium Kotlin Toolkit 3.2.0 for EPUB and PDF.
- [x] Responsive library with import picker, search, user-created shelves, shelf assignment, and non-destructive removal.
- [x] User-selected folder scanning, started only by the **Populate** button; folder access and exclusions persist across scans.
- [x] Manage and clear hidden book exclusions.
- [x] Open EPUB, PDF, UTF-8 TXT, HTML/HTM, FB2, and basic RTF. Unsupported MOBI/AZW and DJVU files are not advertised as supported.
- [x] Save and restore EPUB/PDF reading locators and flowing-text scroll position.
- [x] EPUB and flowing-text appearance options: white, sepia, dark/night, custom background color, text size, publisher font default, generic fonts, and bundled Readium accessibility fonts. PDF pages retain their document appearance.
- [x] Global appearance defaults and URI-keyed per-book overrides for theme, text/page colors, font, and text size; unset book options inherit global values and can be reset.
- [x] Custom hue wheel can edit background and text colors independently and preview the reading contrast before applying.
- [x] Reader auto-rotation toggle; remembers its setting and restores the system orientation policy on close.
- [x] Previous/Next controls offer none, fade, slide, and Readium page-turn behavior; flowing text scrolls smoothly.
- [x] Configurable 1×1 individual-book home-screen widget. Launcher resizing scales the cover artwork; tapping opens that book.
- [x] Configurable shelf home-screen widget with a 2×2 minimum. Resizing shows more books when space permits or spreads the existing books across more space; tapping a cover opens that book.
- [x] Widgets refresh after library and shelf changes; generated cover artwork uses a small in-memory LRU cache to avoid repeated drawing.
- [x] Reader remains open through orientation recreation using stable fragment container IDs; flowing-text position writes are debounced and flushed when the reader view closes.
- [x] Folder scans report revoked access or scan errors and preserve the current library on failure.
- [x] Preserve existing library, shelf, widget, settings, SAF grant, and reading-progress storage keys; release updates retain the same application ID and signing identity.
- [x] App ID is `io.github.nmohith22.vellurix`; the project has a Gradle wrapper, concise README, and Apache-2.0 license.

## Open validations and deferred enhancements

### Reader behavior and accessibility

- [x] Implement none, fade, slide, and page-turn transitions for EPUB, PDF, and flowing-text reader controls.
- [ ] Verify page transitions and gestures on phones and foldables.
- [ ] Apply and verify appearance settings on PDF pages and fixed-layout publications. The current PDF renderer preserves page colors and reports this limitation in its appearance settings.
- [x] Let users set foreground and background colors independently and preview the result.
- [ ] Add separately licensed font files beyond system fonts and Readium accessibility fonts; keep publisher default easy to restore.
- [ ] Audit screen-reader labels, large text, contrast, reduced motion, and keyboard/switch access.
- [ ] Extract embedded EPUB/PDF cover images for home-screen widgets. Current widgets draw typographic cover art from each book's title and format.

### Library and formats

- [ ] Exercise scans, imports, removal, and revoked folder permissions across storage providers and confirm provider-specific behavior.
- [ ] Add shelf rename/delete management and multiple shelf assignments per book; the current library assigns one shelf per book.
- [ ] Verify corrupt and locked file handling while preserving library entries on errors.
- [ ] Consider more formats after parser coverage and licenses are confirmed. CBZ is partial in Readium and is not accepted; MOBI/AZW and DJVU are unsupported.
- [ ] Improve RTF formatting and legacy code-page handling; current support strips common control words and preserves limited plain text.

### Foldable and release checks

- [ ] Add fold posture/window information handling and inspect layouts on cover and inner displays.
- [ ] Validate library, reader, animations, and widgets on a small phone, large phone, and Samsung foldable. No connected device is available in the current environment.
- [ ] Profile large EPUB/PDF files, rotation, process recreation, and memory use on representative Android hardware. Static review reduced repeated widget rendering and scroll persistence work; no device profiler is available here.
- [x] Add a Vellurix book launcher icon for adaptive and legacy launchers.
- [ ] Review the file-access disclosure and configure production release signing.
- [x] Publish a signed, installable APK in `release/` and link it from the README.
- [ ] Install and exercise a release build on a Samsung foldable and a conventional phone.

## Decisions and known limits

- **Framework:** Kotlin and Jetpack Compose provide native access to Android's document picker, orientation control, home-screen widgets, and adaptive layouts.
- **Reader engine:** Readium Kotlin Toolkit 3.2.0 handles EPUB and PDF navigation. Some appearance preferences may be ignored by fixed-layout publications.
- **Format policy:** Accepted extensions are `.epub`, `.pdf`, `.txt`, `.html`, `.htm`, `.fb2`, and `.rtf`. Only formats with a real open path are listed as supported.
- **Folder behavior:** Scans are explicit and non-destructive. Removed books remain excluded from later scans until the exclusion is cleared. Source files are never deleted.
- **Widgets:** Android app widgets support launcher-controlled horizontal and vertical resizing. Their current artwork is generated from title and format; embedded cover extraction remains open.
- **Build:** JDK 17, Android SDK 36, Gradle wrapper 8.13, AGP 8.13.1, Kotlin/Compose 2.3.20, min SDK 24. `testDebugUnitTest` and `assembleRelease` completed successfully. D8 emits Kotlin metadata compatibility warnings for Readium dependencies, but produces the APK. Releases keep the existing package ID and local signing certificate, which is excluded from Git; in-place upgrade behavior still needs on-device confirmation. `release/Vellurix-0.1.2.apk` is signed and verified.
- **Repository:** Project folder is `Development/Vellurix`; changes are pushed to `https://github.com/nmohith22/Vellurix` on `main`. The GitHub description is "Offline-first Android EPUB and PDF reader with customizable themes, responsive shelves, and resizable book widgets."
- **Transitions:** None uses direct navigation, fade and slide animate the reader surface, and page-turn uses Readium navigation for EPUB/PDF plus smooth page-sized scrolling for text formats.
- **License:** Project code is licensed under Apache-2.0. Readium Kotlin Toolkit is BSD-3-Clause; dependency notices should be reviewed as dependencies are finalized.

## References

- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)
- [Readium navigator preferences](https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/navigator/preferences.md)
- [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
- [Android adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)
