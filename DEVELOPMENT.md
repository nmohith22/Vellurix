# Vellurix Development Tracker

**Status:** Core reader and library implemented; unit tests pass. Debug APK packaging still needs a dependency download that has timed out in this environment.  
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
- [x] EPUB appearance options: white, sepia, dark/night, custom background color, text size, publisher font default, generic fonts, and bundled Readium accessibility fonts.
- [x] Custom hue wheel for background color; text color is chosen from background luminance.
- [x] Reader auto-rotation toggle; remembers its setting and restores the system orientation policy on close.
- [x] Previous/Next controls offer none, fade, slide, and Readium page-turn behavior; flowing text scrolls smoothly.
- [x] Configurable 1×1 individual-book home-screen widget. Launcher resizing scales the cover artwork; tapping opens that book.
- [x] Configurable shelf home-screen widget with a 2×2 minimum. Resizing shows more books when space permits or spreads the existing books across more space; tapping a cover opens that book.
- [x] Widgets refresh after library and shelf changes.
- [x] App ID is `io.github.nmohith22.vellurix`; the project has a Gradle wrapper, concise README, and Apache-2.0 license.

## Remaining work

### Reader behavior and accessibility

- [ ] Verify page transitions and gestures on device; current animation modes are wired to Previous/Next controls.
- [ ] Apply and verify appearance settings on PDF pages and fixed-layout publications.
- [ ] Let users set foreground and background colors independently and preview the result.
- [ ] Bundle and license additional font files; keep publisher default easy to restore.
- [ ] Audit screen-reader labels, large text, contrast, reduced motion, and keyboard/switch access.
- [ ] Extract embedded EPUB/PDF cover images for home-screen widgets. Current widgets draw typographic cover art from each book's title and format.

### Library and formats

- [ ] Validate scans, imports, removal, and revoked folder permissions across storage providers.
- [ ] Add shelf rename/delete management and multiple shelf assignments per book; the current library assigns one shelf per book.
- [ ] Verify corrupt and locked file handling while preserving library entries on errors.
- [ ] Consider more formats after parser coverage and licenses are confirmed. CBZ is partial in Readium and is not accepted; MOBI/AZW and DJVU are unsupported.
- [ ] Improve RTF formatting and legacy code-page handling; current support strips common control words and preserves limited plain text.

### Foldable and release checks

- [ ] Add fold posture/window information handling and inspect layouts on cover and inner displays.
- [ ] Validate library, reader, animations, and widgets on a small phone, large phone, and Samsung foldable. No connected device is available in the current environment.
- [ ] Profile large EPUB/PDF files, rotation, process recreation, and memory use.
- [ ] Add a launcher icon, review the file-access disclosure, and configure release signing.
- [ ] Install and exercise a release build on a Samsung foldable and a conventional phone.

## Decisions and known limits

- **Framework:** Kotlin and Jetpack Compose provide native access to Android's document picker, orientation control, home-screen widgets, and adaptive layouts.
- **Reader engine:** Readium Kotlin Toolkit 3.2.0 handles EPUB and PDF navigation. Some appearance preferences may be ignored by fixed-layout publications.
- **Format policy:** Accepted extensions are `.epub`, `.pdf`, `.txt`, `.html`, `.htm`, `.fb2`, and `.rtf`. Only formats with a real open path are listed as supported.
- **Folder behavior:** Scans are explicit and non-destructive. Removed books remain excluded from later scans until the exclusion is cleared. Source files are never deleted.
- **Widgets:** Android app widgets support launcher-controlled horizontal and vertical resizing. Their current artwork is generated from title and format; embedded cover extraction remains open.
- **Build:** JDK 17, Android SDK 36, Gradle wrapper 8.13, AGP 8.13.1, Kotlin/Compose 2.3.20, min SDK 24. The unit test task passed after the reader and widget implementation. A post-fix rerun could not finish because the fresh Gradle distribution download was interrupted; widget provider class references and app theme links were checked. `assembleDebug` has been blocked by a timeout downloading `desugar_jdk_libs:2.1.5` from Google Maven; an alternate public mirror also did not respond.
- **Repository:** Project folder is `Development/Vellurix`; changes are pushed to `https://github.com/nmohith22/Vellurix` on `main`. The GitHub description is “Offline-first Android EPUB and PDF reader with customizable themes, responsive shelves, and resizable book widgets.”
- **License:** Project code is licensed under Apache-2.0. Readium Kotlin Toolkit is BSD-3-Clause; dependency notices should be reviewed as dependencies are finalized.

## References

- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)
- [Readium navigator preferences](https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/navigator/preferences.md)
- [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
- [Android adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)
