"""Build small, redistributable documents with known visible content."""
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile

from PIL import Image


def write_books(directory: Path) -> None:
    cover = directory / "cover.png"
    Image.new("RGB", (120, 180), "#2c8764").save(cover)
    with ZipFile(directory / "Regression Reader.epub", "w", ZIP_STORED) as book:
        book.writestr("mimetype", "application/epub+zip")
        book.writestr("META-INF/container.xml", """<container version="1.0"
          xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
          <rootfile full-path="book.opf" media-type="application/oebps-package+xml"/>
          </rootfiles></container>""")
        book.writestr("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:vellurix:regression</dc:identifier>
          <dc:title>Regression Reader</dc:title><dc:language>en</dc:language>
          <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>
          <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
          <item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>
          <item id="first" href="first.xhtml" media-type="application/xhtml+xml"/>
          <item id="second" href="second.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="first"/><itemref idref="second"/></spine></package>""")
        book.write(cover, "cover.png")
        book.writestr("nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"
          xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body>
          <nav epub:type="toc"><ol><li><a href="first.xhtml">Harbor chapter</a></li>
          <li><a href="second.xhtml">Lighthouse chapter</a></li></ol></nav></body></html>""")
        for href, title, count in (
            ("first.xhtml", "Harbor chapter", 120),
            ("second.xhtml", "Lighthouse chapter", 200),
        ):
            paragraphs = "".join(
                f"<p>Passage {n}. The lamps along the harbor flickered in the evening mist. "
                "When the ferry reached the pier, a gull lifted off and disappeared into the rain."
                + (" Catalogue " + "AB1234567890" * 6 if n % 4 == 0 else "") + "</p>"
                for n in range(1, count + 1)
            )
            book.writestr(
                href,
                f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title}</title>'
                '<style>p { box-shadow: inset 2px 0 #5a20a0, inset -2px 0 #5a20a0; }</style></head>'
                f"<body><h1>{title}</h1>{paragraphs}</body></html>",
            )

    (directory / "Format TXT.txt").write_text("Plain text sample\n" * 100, encoding="utf-8")
    (directory / "Format HTML.html").write_text("<h1>HTML sample</h1><p>Reader text.</p>", encoding="utf-8")
    (directory / "Format HTM.htm").write_text("<p>HTM sample</p>", encoding="utf-8")
    (directory / "Format FB2.fb2").write_text(
        '<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><body>'
        "<section><p>FB2 sample</p></section></body></FictionBook>", encoding="utf-8",
    )
    (directory / "Format RTF.rtf").write_text(r"{\rtf1\ansi RTF sample\par Reader text.}", encoding="ascii")
    write_pdf(directory / "Format PDF.pdf")


def write_pdf(path: Path) -> None:
    """One green page, for testing rendering and cover extraction."""
    stream = b"0.15 0.55 0.35 rg 0 0 200 300 re f"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Resources << >> /Contents 4 0 R >>",
        f"<< /Length {len(stream)} >>\nstream\n".encode() + stream + b"\nendstream",
    ]
    data = bytearray(b"%PDF-1.4\n")
    offsets = []
    for n, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f"{n} 0 obj\n".encode() + obj + b"\nendobj\n")
    table = len(data)
    data.extend(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for offset in offsets:
        data.extend(f"{offset:010d} 00000 n \n".encode())
    data.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{table}\n%%EOF\n".encode())
    path.write_bytes(data)
