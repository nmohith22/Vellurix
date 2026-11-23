"""Black-box regression checks against a Vellurix APK on a disposable emulator."""
import argparse
from collections import Counter
from contextlib import suppress
from io import BytesIO
import os
from pathlib import Path
import re
import subprocess
import time
import unittest
import uuid
from xml.etree import ElementTree

from PIL import Image

from fixtures import write_books

PACKAGE = "io.github.nmohith22.vellurix"
ARTIFACTS = Path(".tmp/android-regression")


class Device:
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]

    def adb(self, *args):
        return subprocess.run(
            [*self.command, *map(str, args)], check=True, capture_output=True, timeout=45,
        ).stdout

    def shell(self, *args):
        return self.adb("shell", *args).decode("utf-8", errors="replace").strip()

    def open_library(self):
        self.shell("am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        self.find("Library")

    def nodes(self):
        for _ in range(4):
            if "dumped to:" in self.shell("uiautomator", "dump", f"{self.remote}/window.xml"):
                xml = self.adb("exec-out", "cat", f"{self.remote}/window.xml")
                return list(ElementTree.fromstring(xml).iter("node"))
        raise ElementTree.ParseError("UI hierarchy was not ready")

    def type_text(self, text):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if any(n.get("class") == "android.widget.EditText" and n.get("focused") == "true"
                   for n in self.nodes()):
                break
        else:
            raise AssertionError("Expected a focused text field")
        for char in text:
            self.shell("input", "text", "%s" if char == " " else char)
            time.sleep(.08)

    def find(self, label, timeout=15, condition=lambda _: True):
        deadline = time.monotonic() + timeout
        visible = []
        while time.monotonic() < deadline:
            try:
                nodes = self.nodes()
            except (ElementTree.ParseError, subprocess.CalledProcessError):
                continue
            visible = []
            for node in nodes:
                for key in ("text", "content-desc"):
                    value = node.get(key, "")
                    if not value or node.get("bounds") == "[0,0][0,0]":
                        continue
                    visible.append(value)
                    if (value == label or (isinstance(label, re.Pattern) and label.fullmatch(value))) and condition(node):
                        return node
        raise AssertionError(f"Could not find {label!r}. Visible labels: {visible[:35]}")

    @staticmethod
    def bounds(node):
        return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))

    def tap_node(self, node, hold=False):
        x1, y1, x2, y2 = self.bounds(node)
        x, y = (x1+x2)//2, (y1+y2)//2
        if hold:
            self.shell("input", "swipe", x, y, x, y, 750)
        else:
            self.point(x, y)

    def tap(self, label, hold=False):
        self.tap_node(self.find(label), hold)

    def point(self, x, y):
        self.shell("input", "tap", x, y)

    def resize(self, width, height):
        self.shell("wm", "size", f"{width}x{height}")
        self.width, self.height = width, height

    def screenshot(self, name=None):
        png = self.adb("exec-out", "screencap", "-p")
        if name:
            (ARTIFACTS / f"{name}.png").write_bytes(png)
        return Image.open(BytesIO(png)).convert("RGB")

    def import_book(self, filename):
        self.tap(re.compile("Import a book|Import book"))
        try:
            recent = self.find(filename, timeout=4)
        except AssertionError:
            recent = None
        if recent is not None:
            self.tap_node(recent)
            self.find("Library")
            return
        self.open_fixture_folder()
        for _ in range(4):
            try:
                self.tap_node(self.find(filename, timeout=4))
                break
            except AssertionError:
                self.shell("input", "swipe", self.width//2, self.height-130, self.width//2, 270, 400)
        else:
            raise AssertionError(f"Document picker did not show {filename}")
        self.find("Library")

    def open_fixture_folder(self):
        texts = {n.get("text") for n in self.nodes()}
        if f"Files in {self.folder}" in texts:
            return
        if self.folder not in texts:
            if "Download" not in texts:
                self.tap("Show roots")
                # The Downloads provider may omit files copied by ADB.
                self.tap(self.shell("getprop", "ro.product.model"))
            self.tap("Download")
        self.tap(self.folder)

    def populate_fixtures(self):
        self.tap("Populate library")
        self.open_fixture_folder()
        self.tap(re.compile(r"(?i)use this folder"))
        self.tap(re.compile(r"(?i)allow"))
        self.find("Library")

    def center(self):
        self.point(self.width//2, self.height//2)

    def book_position(self):
        node = self.find(re.compile(r"Book · \d+ / \d+"))
        return tuple(map(int, re.findall(r"\d+", node.attrib["text"])))


class TrackerRegression(unittest.TestCase):
    def setUp(self):
        self.d.resize(360, 760)
        self.d.shell("pm", "clear", PACKAGE)
        self.d.open_library()
        result = self._outcome.result
        self.failure_count = len(result.failures) + len(result.errors)
        self.addCleanup(self.capture_failure)

    def capture_failure(self):
        result = self._outcome.result
        if len(result.failures) + len(result.errors) > self.failure_count:
            with suppress(subprocess.SubprocessError, OSError):
                self.d.screenshot(self._testMethodName)
                crashes = self.d.adb("logcat", "-b", "crash", "-d")
                (ARTIFACTS / f"{self._testMethodName}-crash.log").write_bytes(crashes)

    def test_library_import_search_shelf_rename_and_non_destructive_removal(self):
        """Tracker: responsive library, covers, shelves, direct open and non-destructive removal."""
        self.d.import_book("Regression Reader.epub")
        cover = self.d.find("Cover of Regression Reader")
        x1, y1, x2, y2 = self.d.bounds(cover)
        self.assertEqual(self.d.screenshot().getpixel(((x1+x2)//2, (y1+y2)//2)), (44, 135, 100))

        self.d.tap("Search your library")
        self.d.type_text("no-match")
        self.d.find("No matching books")
        self.d.tap("Close search")
        self.d.find("Regression Reader")

        self.d.tap("New shelf")
        self.d.tap("Shelf name")
        self.d.type_text("Travel")
        self.d.tap("Create")
        self.d.find("This shelf is empty")
        self.d.tap("Travel", hold=True)
        self.d.find("Rename shelf")
        self.d.tap("Travel")
        self.d.shell("input", "keyevent", "KEYCODE_MOVE_END")
        self.d.type_text("Notes")
        self.d.tap("Rename")
        self.d.resize(700, 820)
        self.d.find("Shelves")
        self.d.shell("am", "force-stop", PACKAGE)
        self.d.open_library()
        self.d.find("This shelf is empty")
        self.d.find("TravelNotes")
        self.d.tap("All books")
        self.d.tap("Regression Reader", hold=True)
        self.d.tap("Remove")
        self.d.find("Your library is empty")
        # Explicit import can restore a removed book and proves its source still exists.
        self.d.import_book("Regression Reader.epub")
        self.d.find("Cover of Regression Reader")

    def test_reader_progress_bookmarks_and_foldable_pagination(self):
        """Tracker 0.1.18, 0.1.24–28: controls, chapter counts, resume, gutter and no drift."""
        self.d.import_book("Regression Reader.epub")
        self.d.tap("Cover of Regression Reader")
        self.d.find("Harbor chapter")
        self.assertFalse(any(n.get("content-desc") == "Reader settings" for n in self.d.nodes()))
        self.d.center()
        first, total = self.d.book_position()
        self.assertEqual(first, 1)
        self.assertGreater(total, 10)
        self.d.tap("Reader settings")
        self.d.find("Preview")
        customize = self.d.find("Customize this book")
        _, top, right, bottom = self.d.bounds(customize)
        toggle = next(
            (n for n in self.d.nodes() if n.get("checkable") == "true"
             and self.d.bounds(n)[0] >= right and self.d.bounds(n)[1] <= bottom
             and self.d.bounds(n)[3] >= top),
            None,
        )
        self.assertIsNotNone(toggle, "Customize this book must expose a switch")
        self.d.tap_node(toggle)
        self.d.tap("Layout")
        self.d.tap("Double")
        self.d.tap("Close settings")
        self.d.resize(820, 700)

        # Compare the ink guides drawn at both ends of each paragraph box.
        self.d.tap("Reader settings")
        self.d.tap("Layout")
        minimum_gap = self.set_gap(wide=False)
        self.measure_spread(minimum_gap)
        self.d.center()
        self.d.tap("Reader settings")
        self.d.tap("Layout")
        wide_gap = self.set_gap(wide=True)
        self.measure_spread(wide_gap)
        # A fractional scale and an odd physical width exercise WebView pixel rounding.
        self.d.resize(1851, 1553)
        self.d.shell("wm", "density", 360)
        self.d.find("Harbor chapter")
        widest = self.measure_spread(wide_gap, scale=2.25)

        # Repeated turns must move content while preserving the same column starts.
        previous = self.d.screenshot().tobytes()
        for _ in range(5):
            self.d.point(self.d.width*9//10, self.d.height//2)
            current = self.d.screenshot()
            deadline = time.monotonic() + 5
            while current.tobytes() == previous and time.monotonic() < deadline:
                current = self.d.screenshot()
            time.sleep(.5)  # Sample the final spread, outside the page-turn animation.
            current = self.d.screenshot()
            self.assertFalse(previous == current.tobytes(), "Page turn left the same spread on screen")
            edges = self.measure_spread(wide_gap, scale=2.25, image=current)
            self.assertLessEqual(max(abs(a-b) for a, b in zip(edges, widest)), 2)
            previous = current.tobytes()

        self.d.shell("wm", "density", 160)
        self.d.resize(700, 820)
        self.d.find(re.compile(r"Passage \d+\..*"))
        portrait = self.guide_edges()
        deadline = time.monotonic() + 8
        while len(portrait) != 2 and time.monotonic() < deadline:
            portrait = self.guide_edges()
        self.assertEqual(len(portrait), 2, "Double mode must collapse in portrait")
        self.d.center()
        position, count = self.d.book_position()
        self.assertGreater(position, 1)
        chapter = self.d.find(re.compile(r"Chapter · \d+ / \d+")).attrib["text"]
        self.assertEqual(int(re.findall(r"\d+", chapter)[0]), position)
        self.d.tap("Add bookmark")
        self.d.find("Remove bookmark")
        self.d.tap("Close reader")
        self.d.find("Continue reading")
        self.d.shell("am", "force-stop", PACKAGE)
        self.d.open_library()
        self.d.tap("Continue reading")
        self.d.find(re.compile(r"Passage \d+\..*"))
        self.d.center()
        self.assertEqual(self.d.book_position(), (position, count))
        self.d.find("Remove bookmark")
        self.d.tap("Bookmarks")
        self.d.find("Close drawer")
        self.d.find("Harbor chapter")
        self.d.find(f"Page {position}")

    def set_gap(self, wide):
        label = self.d.find(re.compile(r"Middle column gap · \d+ dp"))
        bottom = self.d.bounds(label)[3]
        # The slider immediately follows its label. Resolve its bounds from accessibility.
        slider = next(
            n for n in self.d.nodes() if n.get("class") == "android.widget.SeekBar"
            and bottom <= self.d.bounds(n)[1] <= bottom + 24
        )
        left, top, right, end = self.d.bounds(slider)
        y = (top+end)//2
        self.d.shell("input", "swipe", (left+right)//2, y, right+4 if wide else left-4, y, 300)
        expected = r"6[0-4]" if wide else r"[0-4]"
        label = self.d.find(re.compile(rf"Middle column gap · {expected} dp"))
        value = int(re.search(r"\d+", label.get("text")).group())
        self.d.tap("Close settings")
        self.d.find("Reader settings")
        self.d.center()
        self.d.find("Harbor chapter")  # Let the controls finish fading before sampling page colors.
        return value

    def measure_spread(self, gap, scale=1, image=None):
        image = image or self.d.screenshot()
        left, inner_left, inner_right, right = self.column_edges(image)
        center = image.width / 2
        print(f"Spread {image.width}x{image.height} at {gap} dp: edges "
              f"{left}, {inner_left}, {inner_right}, {right}", flush=True)
        self.assertAlmostEqual((inner_right+inner_left)/2, center, delta=2)
        self.assertAlmostEqual(inner_left-left, right-inner_right, delta=4)
        # Each guide is painted two CSS pixels inside its paragraph box.
        self.assertAlmostEqual(inner_right-inner_left, gap*scale, delta=4*scale)
        if gap >= 10:
            strip = image.crop((int(center-gap*scale/2+3*scale), image.height//6,
                                int(center+gap*scale/2-3*scale), image.height*5//6))
            self.assertEqual(len(strip.getcolors(2) or []), 1, "Ink crossed the center gutter")
        return [left, inner_left, inner_right, right]

    def guide_edges(self, image=None):
        image = image or self.d.screenshot()
        crop = image.crop((0, 105, image.width, image.height-100))
        counts = Counter(
            i % crop.width for i, pixel in enumerate(crop.get_flattened_data())
            if pixel == (90, 32, 160)
        )
        runs = []
        for x in sorted(x for x, amount in counts.items() if amount > 20):
            if not runs or x > runs[-1][-1] + 1:
                runs.append([])
            runs[-1].append(x)
        return [run[len(run)//2] for run in runs]

    def column_edges(self, image=None):
        edges = self.guide_edges(image)
        self.assertIn(len(edges), (3, 4), f"Expected two EPUB columns, guide edges: {edges}")
        # At zero gap the two touching inner guides are one run.
        return [edges[0], edges[1], edges[-2], edges[-1]]

    def test_supported_documents_render(self):
        """Tracker format policy: all seven extensions have a real open path."""
        self.d.populate_fixtures()
        self.d.tap("Search your library")
        for extension, body in (
            ("TXT", "Plain text sample"), ("HTML", "HTML sample"), ("HTM", "HTM sample"),
            ("FB2", "FB2 sample"), ("RTF", "RTF sample"), ("PDF", None),
        ):
            with self.subTest(extension=extension):
                title = f"Format {extension}"
                self.d.type_text(title)
                self.d.tap_node(self.d.find(title, condition=lambda n: n.get("class") != "android.widget.EditText"))
                if body:
                    self.d.find(re.compile(rf"(?s).*{body}.*"))
                else:
                    deadline = time.monotonic() + 15
                    while time.monotonic() < deadline:
                        red, green, blue = self.d.screenshot().getpixel((self.d.width//2, self.d.height//2))
                        if green > max(red, blue)+25:
                            break
                        time.sleep(.25)
                    else:
                        self.fail("The PDF page did not render its original green")
                self.d.center()
                self.d.find("Reader settings")
                if extension == "PDF":
                    self.assertEqual(self.d.book_position(), (1, 1))
                self.d.tap("Close reader")
                self.d.find("Library")
                self.d.tap("Close search")
                if extension != "PDF":
                    self.d.tap("Search your library")

    def test_folder_resume_discovers_new_books_and_keeps_removals(self):
        """Tracker folder behavior: discovery on resume and persistent exclusions."""
        self.d.populate_fixtures()
        self.d.tap("Search your library")
        self.d.type_text("Format TXT")
        title = self.d.find("Format TXT", condition=lambda n: n.get("class") != "android.widget.EditText")
        self.d.tap_node(title, hold=True)
        self.d.tap("Remove")
        self.d.find("No matching books")

        arrival = ARTIFACTS / "New arrival.txt"
        arrival.write_text("This file appeared after the first folder scan.", encoding="utf-8")
        self.d.adb("push", arrival, self.d.remote + "/")
        self.d.shell("am", "force-stop", PACKAGE)
        self.d.open_library()
        self.d.tap("Search your library")
        self.d.type_text("New arrival")
        self.d.find("New arrival", condition=lambda n: n.get("class") != "android.widget.EditText")
        self.d.tap("Close search")
        self.d.tap("Search your library")
        self.d.type_text("Format TXT")
        self.d.find("No matching books")

        self.d.tap("Settings")
        self.d.tap("Populate from folder")
        self.d.find("Populate from folder", condition=lambda n: n.get("enabled") == "true")
        self.d.tap("Done")
        self.d.find("No matching books")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="ADB serial of an empty disposable emulator")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    loader = unittest.defaultTestLoader
    methods = loader.getTestCaseNames(TrackerRegression)
    parser.add_argument("--test", choices=methods, action="append", help="Run only the named test")
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error("APK does not exist")
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("A disposable Android emulator is required")
    d = Device(args.adb, args.serial)
    if d.shell("getprop", "sys.boot_completed") != "1":
        parser.error("Wait for the emulator to finish booting")
    if any(line == f"package:{PACKAGE}" for line in d.shell("pm", "list", "packages", PACKAGE).splitlines()):
        parser.error("Vellurix is already installed. Use a fresh emulator to protect its data.")

    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    d.folder = f"VellurixTests-{uuid.uuid4().hex[:8]}"
    fixtures = ARTIFACTS / d.folder
    fixtures.mkdir()
    write_books(fixtures)
    d.remote = f"/sdcard/Download/{d.folder}"
    original_size = re.search(r"Override size: ([0-9x]+)", d.shell("wm", "size"))
    original_density = re.search(r"Override density: (\d+)", d.shell("wm", "density"))
    installed = False
    try:
        d.shell("mkdir", "-p", d.remote)
        for fixture in fixtures.iterdir():
            if fixture.suffix != ".png":
                d.adb("push", fixture, d.remote + "/")
        d.adb("install", args.apk.resolve())
        installed = True
        d.shell("wm", "density", 160)
        TrackerRegression.d = d
        suite = unittest.TestSuite(TrackerRegression(name) for name in (args.test or methods))
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    finally:
        if installed:
            d.adb("uninstall", PACKAGE)
        # Only the unique folder created by this invocation is removed.
        d.shell("rm", "-r", d.remote)
        d.shell("wm", "size", original_size[1] if original_size else "reset")
        d.shell("wm", "density", original_density[1] if original_density else "reset")


if __name__ == "__main__":
    raise SystemExit(main())
