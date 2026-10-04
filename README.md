# Vellurix

An offline-first Android reader for EPUB, PDF, TXT, HTML, FB2, and basic RTF. It includes a responsive library, customizable reading themes, and resizable book and shelf widgets.

## Download

**[Download Vellurix 0.1.22 for Android](https://github.com/nmohith22/Vellurix/releases/download/v0.1.22/Vellurix-0.1.22.apk)** · [View release notes and other downloads](https://github.com/nmohith22/Vellurix/releases)

Download the APK on your Android device, open it from Downloads, and follow Android's install prompt. Install it over your existing Vellurix app without uninstalling first to keep its local library, shelves, settings, widgets, and reading progress. Android may ask you to allow your browser or file manager to install apps from that source.

## Features

- Read EPUB, PDF, TXT, HTML, FB2, and basic RTF files.
- For EPUB and flowing text, set global reading defaults or per-book overrides for themes, text and page colors, fonts, and text size. Reader appearance and layout controls stay disabled until **Customize this book** is enabled; unset per-book settings inherit the global defaults.
- The reader opens in a distraction-free view. Tap the center to reveal controls; tap either side to change pages. The compact toolbar provides themes, rotation, contents, and bookmarks; swipe from the left edge to open the book drawer.
- Choose from 18 shared global and per-book reading themes, including nine OLED-black palettes (seven Deep themes), or independently customize text and page colors. The library's global reader theme setting also updates the reader. PDF documents retain their page colors.
- Adjust text size, typeface, top/bottom margins, and EPUB layout. EPUB reading defaults to paginated single-column pages; double-column mode follows the live viewport and is available in landscape, while landscape single-column and continuous modes remain available. Appearance selections update immediately, and layout changes keep the current reading position.
- Increase line spacing globally or for an individual book. EPUB line-height changes turn off publisher text styling so the selected spacing can take effect.
- Choose no transition, fade, slide, or a page-sheet turning animation. Tap the reader's top-right corner to toggle a bookmark; its theme-colored ribbon drops in with a small bounce and slides away when removed.
- Choose from 27 Monkeytype-inspired app themes adapted from the local `workout_app` palette; all dark palettes use OLED-black app backgrounds. The reader bookmark is a slim, theme-colored ribbon at the top-right screen edge.
- Tap a book card or list row to read it. Choose card covers with or without titles; list rows always show both. Long-press to rename the in-app title, open live per-book reader settings, assign a shelf, select books for batch shelf moves, or remove a book.
- Switch the library between card and list views, resize the items, and change the app theme from Settings.
- Settings are grouped into Library, Reader, and Data tabs; reader controls are split into Appearance, Layout, and Controls.
- Return to the shelf you last selected when reopening the library.
- See embedded EPUB covers and first-page PDF previews in the library; each card and row picks up a tint from its cover.
- See each book's reading progress on library cards and list rows, displayed as a percentage or position count. A continue-reading banner opens your most recent book.
- On compact screens, settings can replace list-row progress bars with a percentage while cards retain their progress indicators.
- Let the library discover new files when it opens, or run **Populate from folder** in Settings. Removed books stay excluded from scans; import a removed book explicitly to add it back. Create shelves and add book or shelf widgets to the home screen.
- Open library search from the top bar; the field expands on demand and collapses when cleared.
- Adapts to phones and foldable displays; the reader hides system bars and reveals them temporarily on a swipe.
- Book and shelf widgets show book covers, resize with the launcher, and open the selected book when tapped. The picker shows cover previews and keeps clear of display cutouts.
- Swipe horizontally through every book in a shelf widget; each book tile shows only its cover, and tapping it opens that book.
- Export and import JSON backups for reading positions, bookmarks, shelves, library settings, and reader customization. Backups map existing EPUB/PDF files by content hash and do not contain book files; add those books to the library before restoring on another device.
- The launcher icon uses an orange and black book design by default; its One UI monochrome version keeps page lines and the center spine as cutout details.
- Scrub through a book from the reader's chapter-segmented progress seeker; it previews the section under your finger and navigates when released.

## License

Vellurix is licensed under Apache-2.0. See [LICENSE](LICENSE).
