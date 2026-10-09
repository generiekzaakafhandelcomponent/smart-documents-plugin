#  Copyright 2026 Ritense BV, the Netherlands.
#
#  Licensed under EUPL, Version 1.2 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" basis,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""
SmartDocuments mock server for the example application.

It implements the two SmartDocuments endpoints that the plugin calls:

- GET  /sdapi/structure                    the template groups and templates (XML)
- POST /wsxmldeposit/deposit/unattended    generates a document from a template and the template data

Generated documents are real files (PDF, DOCX, HTML and XML) with the template data filled in. The web UI on / shows
every request, lets you preview and download the generated documents, edit the templates and simulate errors.

Only the Python standard library is used, so it runs in a plain python image without a build step.
"""

import base64
import datetime
import html
import io
import json
import os
import re
import threading
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
from xml.sax.saxutils import escape as xml_escape

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
DEFAULT_TEMPLATES_FILE = os.path.join(BASE_DIR, "templates.json")
TEMPLATES_FILE = os.path.join(DATA_DIR, "templates.json")
UI_FILE = os.path.join(BASE_DIR, "ui.html")

PORT = int(os.environ.get("PORT", "8080"))
USERNAME = os.environ.get("SMARTDOCUMENTS_USERNAME", "valtimo")
PASSWORD = os.environ.get("SMARTDOCUMENTS_PASSWORD", "valtimo")
MAX_LOG_ENTRIES = 100
FORMATS = ["DOCX", "PDF", "XML", "HTML"]
CONTENT_TYPES = {
    "PDF": "application/pdf",
    "DOCX": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "HTML": "text/html; charset=utf-8",
    "XML": "application/xml; charset=utf-8",
}

lock = threading.Lock()
request_log = []  # newest first
generated_files = {}  # request id -> {format: (filename, bytes)}
settings = {"failureStatus": None, "failureOnce": True, "delayMs": 0, "simulateWhitespace": True}
XML_SPACE = "{http://www.w3.org/XML/1998/namespace}space"
MAX_RAW_BODY = 100_000


# --------------------------------------------------------------------------------------------------------------------
# Templates
# --------------------------------------------------------------------------------------------------------------------

def load_templates():
    path = TEMPLATES_FILE if os.path.isfile(TEMPLATES_FILE) else DEFAULT_TEMPLATES_FILE
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def save_templates(templates):
    validate_templates(templates)
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(TEMPLATES_FILE, "w", encoding="utf-8") as f:
        json.dump(templates, f, indent=4, ensure_ascii=False)


def reset_templates():
    if os.path.isfile(TEMPLATES_FILE):
        os.remove(TEMPLATES_FILE)


def validate_templates(templates):
    if not isinstance(templates, dict) or not isinstance(templates.get("groups"), list):
        raise ValueError("Expected an object with a 'groups' list")

    def check(group, path):
        if not str(group.get("name", "")).strip():
            raise ValueError(f"A template group in {path or 'the root'} has no name")
        names = [t.get("name", "") for t in group.get("templates", [])]
        for t in group.get("templates", []):
            if not str(t.get("name", "")).strip():
                raise ValueError(f"A template in group '{group['name']}' has no name")
        if len(names) != len(set(names)):
            raise ValueError(f"Group '{group['name']}' contains two templates with the same name")
        for child in group.get("groups", []):
            check(child, f"{path}/{group['name']}")

    for g in templates["groups"]:
        check(g, "")


# --------------------------------------------------------------------------------------------------------------------
# Request parsing
# --------------------------------------------------------------------------------------------------------------------

def collapse_whitespace(text):
    """SmartDocuments turns the request into XML and XML ignores whitespace, so line breaks are lost."""
    return re.sub(r"\s+", " ", text).strip()


def simulate_json_whitespace(value, path, lost):
    if isinstance(value, str):
        if "\n" in value or "\r" in value:
            lost.append(path)
        return collapse_whitespace(value)
    if isinstance(value, dict):
        return {k: simulate_json_whitespace(v, f"{path}.{k}" if path else k, lost) for k, v in value.items()}
    if isinstance(value, list):
        return [simulate_json_whitespace(v, f"{path}[{i}]", lost) for i, v in enumerate(value)]
    return value


def xml_to_value(element, path, simulate, kept, lost):
    children = list(element)
    if not children:
        if element.text is None:
            return None
        text = element.text.replace("\r\n", "\n")
        has_line_break = "\n" in text
        if element.get(XML_SPACE) == "preserve":
            if has_line_break:
                kept.append(path)
            return text
        if has_line_break:
            lost.append(path)
        return collapse_whitespace(text) if simulate else text
    if all(child.tag == "item" for child in children):
        return [xml_to_value(c, f"{path}[{i}]", simulate, kept, lost) for i, c in enumerate(children)]
    return {c.tag: xml_to_value(c, f"{path}.{c.tag}" if path else c.tag, simulate, kept, lost) for c in children}


def parse_xml_request(raw, simulate):
    """Returns (customerData, templateGroup, template, kept, lost). Raises ValueError for an invalid request."""
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as e:
        raise ValueError(f"The request body is not valid XML: {e}")
    customer_data = root.find("customerData")
    selection = root.find("SmartDocument/Selection")
    if customer_data is None or selection is None:
        raise ValueError("The XML request needs <customerData> and <SmartDocument><Selection> elements")
    kept, lost = [], []
    data = {c.tag: xml_to_value(c, c.tag, simulate, kept, lost) for c in customer_data}
    return data, selection.findtext("TemplateGroup"), selection.findtext("Template"), kept, lost


def find_group(groups, name):
    for group in groups:
        if group.get("name") == name:
            return group
        found = find_group(group.get("groups", []), name)
        if found:
            return found
    return None


def structure_xml(templates):
    counter = [0]

    def next_id():
        counter[0] += 1
        return str(counter[0])

    def group_xml(group, indent):
        pad = " " * indent
        lines = [f'{pad}<TemplateGroup IsAccessible="true" ID="{next_id()}" Name="{xml_escape(group["name"], {chr(34): "&quot;"})}">']
        children = group.get("groups", [])
        if children:
            lines.append(f"{pad}    <TemplateGroups>")
            for child in children:
                lines.extend(group_xml(child, indent + 8))
            lines.append(f"{pad}    </TemplateGroups>")
        else:
            lines.append(f"{pad}    <TemplateGroups/>")
        lines.append(f"{pad}    <Templates>")
        for t in group.get("templates", []):
            lines.append(f'{pad}        <Template ID="{next_id()}" Name="{xml_escape(t["name"], {chr(34): "&quot;"})}"/>')
        lines.append(f"{pad}    </Templates>")
        lines.append(f"{pad}</TemplateGroup>")
        return lines

    lines = ["<SmartDocuments>", "    <DocumentsStructure>", '        <TemplatesStructure IsAccessible="true">',
             "            <TemplateGroups>"]
    for g in templates["groups"]:
        lines.extend(group_xml(g, 16))
    lines += ["            </TemplateGroups>", "        </TemplatesStructure>", "    </DocumentsStructure>",
              "</SmartDocuments>"]
    return "\n".join(lines)


# --------------------------------------------------------------------------------------------------------------------
# Rendering
# --------------------------------------------------------------------------------------------------------------------

PLACEHOLDER = re.compile(r"\{\{\s*([\w.\-]+)\s*\}\}")


def value_to_text(value):
    if value is None:
        return ""
    if isinstance(value, list):
        # Each item becomes its own paragraph in the document
        return "\n\n".join(value_to_text(item) for item in value)
    if isinstance(value, dict):
        return "\n".join(f"{key}: {field_text(item)}" for key, item in value.items())
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def field_text(value):
    """A list of plain values inside an object is written on one line, separated by commas."""
    if isinstance(value, list) and all(not isinstance(item, (dict, list)) for item in value):
        return ", ".join(value_to_text(item) for item in value)
    return value_to_text(value)


def lookup(data, key):
    current = data
    for part in key.split("."):
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            return None, False
    return current, True


def render_text(text, data, missing):
    def replace(match):
        value, found = lookup(data, match.group(1))
        if not found:
            missing.add(match.group(1))
            return f"[{match.group(1)}]"
        return value_to_text(value)

    return PLACEHOLDER.sub(replace, text or "")


def render_document(template, group_name, data):
    missing = set()
    title = render_text(template.get("title") or template["name"], data, missing)
    body = render_text(template.get("body", ""), data, missing)
    paragraphs = [p.strip("\n") for p in re.split(r"\n\s*\n", body)]
    return {
        "title": title,
        "paragraphs": [p for p in paragraphs if p.strip()],
        "template": template["name"],
        "group": group_name,
        "data": data,
        "missing": sorted(missing),
        "date": datetime.date.today().strftime("%d-%m-%Y"),
    }


def data_rows(data):
    return [(str(k), value_to_text(v)) for k, v in data.items()]


def make_html(doc):
    rows = "".join(f"<tr><th>{html.escape(k)}</th><td>{html.escape(v)}</td></tr>" for k, v in data_rows(doc["data"]))
    paragraphs = "".join(f"<p>{html.escape(p).replace(chr(10), '<br>')}</p>" for p in doc["paragraphs"])
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>{html.escape(doc['title'])}</title>
<style>
body{{font-family:Helvetica,Arial,sans-serif;color:#1d2433;max-width:720px;margin:40px auto;padding:0 24px;line-height:1.55}}
.meta{{color:#6b7385;font-size:13px;border-bottom:1px solid #e3e6ec;padding-bottom:12px;margin-bottom:28px}}
h1{{font-size:22px;margin:0 0 20px}} table{{border-collapse:collapse;width:100%;font-size:13px;margin-top:8px}}
th,td{{text-align:left;padding:6px 10px;border-bottom:1px solid #e3e6ec;vertical-align:top}} th{{width:35%;color:#6b7385;font-weight:600}}
h2{{font-size:14px;margin-top:40px;color:#6b7385;text-transform:uppercase;letter-spacing:.04em}}
</style></head><body>
<div class="meta">SmartDocuments mock &middot; {html.escape(doc['group'])} / {html.escape(doc['template'])} &middot; {doc['date']}</div>
<h1>{html.escape(doc['title'])}</h1>
{paragraphs}
<h2>Template data received</h2>
<table>{rows or '<tr><td>No template data</td></tr>'}</table>
</body></html>"""


def make_xml(doc):
    def element(key, value):
        tag = re.sub(r"[^\w.\-]", "_", str(key)) or "field"
        if tag[0].isdigit() or tag[0] in ".-":
            tag = "_" + tag
        return f"        <{tag}>{xml_escape(value_to_text(value))}</{tag}>"

    fields = "\n".join(element(k, v) for k, v in doc["data"].items())
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<SmartDocumentsAnswer>
    <Selection>
        <TemplateGroup>{xml_escape(doc['group'])}</TemplateGroup>
        <Template>{xml_escape(doc['template'])}</Template>
    </Selection>
    <CustomerData>
{fields}
    </CustomerData>
</SmartDocumentsAnswer>
"""


def make_docx(doc):
    def run(text, bold=False, size=None, color=None):
        props = ""
        if bold or size or color:
            props = "<w:rPr>" + ("<w:b/>" if bold else "") + (f'<w:color w:val="{color}"/>' if color else "") + \
                    (f'<w:sz w:val="{size}"/>' if size else "") + "</w:rPr>"
        parts = text.split("\n")
        content = '<w:br/>'.join(f'<w:t xml:space="preserve">{xml_escape(p)}</w:t>' for p in parts)
        return f"<w:r>{props}{content}</w:r>"

    def para(text, bold=False, size=None, color=None, after=160):
        return f'<w:p><w:pPr><w:spacing w:after="{after}"/></w:pPr>{run(text, bold, size, color)}</w:p>'

    def cell(text, width, bold=False):
        return (f'<w:tc><w:tcPr><w:tcW w:w="{width}" w:type="dxa"/></w:tcPr>'
                f'<w:p><w:pPr><w:spacing w:after="0"/></w:pPr>{run(text, bold, 18)}</w:p></w:tc>')

    body = [para(f"SmartDocuments mock - {doc['group']} / {doc['template']} - {doc['date']}", size=18, color="6B7385",
                 after=360),
            para(doc["title"], bold=True, size=36, after=280)]
    body += [para(p) for p in doc["paragraphs"]]
    body.append(para("TEMPLATE DATA RECEIVED", bold=True, size=18, color="6B7385", after=120))
    border = '<w:tblBorders><w:insideH w:val="single" w:sz="4" w:color="E3E6EC"/>' \
             '<w:bottom w:val="single" w:sz="4" w:color="E3E6EC"/></w:tblBorders>'
    rows = "".join(f"<w:tr>{cell(k, 3200, True)}{cell(v, 6200)}</w:tr>" for k, v in data_rows(doc["data"]))
    body.append(f'<w:tbl><w:tblPr><w:tblW w:w="9400" w:type="dxa"/>{border}</w:tblPr>'
                f'<w:tblGrid><w:gridCol w:w="3200"/><w:gridCol w:w="6200"/></w:tblGrid>{rows}</w:tbl>' if rows
                else para("No template data"))
    document = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
                + "".join(body) +
                '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
                '<w:pgMar w:top="1440" w:right="1260" w:bottom="1440" w:left="1260"/></w:sectPr>'
                '</w:body></w:document>')
    content_types = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                     '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                     '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                     '<Default Extension="xml" ContentType="application/xml"/>'
                     '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
                     '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            '</Relationships>')
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", content_types)
        z.writestr("_rels/.rels", rels)
        z.writestr("word/document.xml", document)
    return buffer.getvalue()


# Average glyph widths for Helvetica (per 1000 units) to wrap lines reasonably.
NARROW = set("iIl.,;:'!|()[]{} ftjr")
WIDE = set("mwMW@%")


def text_width(text, size):
    width = 0
    for ch in text:
        width += 280 if ch in NARROW else 830 if ch in WIDE else 640 if ch.isupper() else 540
    return width * size / 1000


def wrap(text, size, max_width):
    lines = []
    for raw_line in text.split("\n"):
        words = raw_line.split(" ")
        current = ""
        for word in words:
            candidate = f"{current} {word}" if current else word
            if text_width(candidate, size) <= max_width or not current:
                current = candidate
            else:
                lines.append(current)
                current = word
        lines.append(current)
    return lines


def pdf_string(text):
    raw = text.encode("cp1252", errors="replace")
    return "(" + raw.decode("latin-1").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"


def make_pdf(doc):
    page_w, page_h, margin = 595, 842, 64
    max_w = page_w - 2 * margin
    pages, ops, y = [], [], page_h - margin

    def new_page():
        nonlocal ops, y
        if ops:
            pages.append(ops)
        ops, y = [], page_h - margin

    def text(line, x, size, font="F1", color=(0.11, 0.14, 0.2)):
        ops.append(f"{color[0]} {color[1]} {color[2]} rg BT /{font} {size} Tf {x:.1f} {y:.1f} Td {pdf_string(line)} Tj ET")

    def line_break(height):
        nonlocal y
        y -= height
        if y < margin:
            new_page()

    grey = (0.42, 0.45, 0.52)
    text(f"SmartDocuments mock  -  {doc['group']} / {doc['template']}  -  {doc['date']}", margin, 9, color=grey)
    line_break(10)
    ops.append(f"0.89 0.9 0.93 RG 0.8 w {margin} {y:.1f} m {page_w - margin} {y:.1f} l S")
    line_break(36)
    for line in wrap(doc["title"], 20, max_w):
        text(line, margin, 20, "F2")
        line_break(26)
    line_break(8)
    for paragraph in doc["paragraphs"]:
        for line in wrap(paragraph, 11, max_w):
            text(line, margin, 11)
            line_break(16)
        line_break(10)
    line_break(18)
    text("TEMPLATE DATA RECEIVED", margin, 9, "F2", grey)
    line_break(18)
    rows = data_rows(doc["data"]) or [("", "No template data")]
    for key, value in rows:
        value_lines = wrap(value, 10, max_w - 170)
        text(key, margin, 10, "F2", grey)
        for i, value_line in enumerate(value_lines):
            text(value_line, margin + 170, 10)
            if i < len(value_lines) - 1:
                line_break(14)
        line_break(8)
        ops.append(f"0.89 0.9 0.93 RG 0.5 w {margin} {y:.1f} m {page_w - margin} {y:.1f} l S")
        line_break(14)
    new_page()

    objects = []

    def add(obj):
        objects.append(obj)
        return len(objects)

    catalog_id = add(None)
    pages_id = add(None)
    font1 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    font2 = add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    page_ids = []
    for page_ops in pages:
        stream = "\n".join(page_ops).encode("latin-1")
        content_id = add(b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream")
        page_ids.append(add(f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {page_w} {page_h}] "
                            f"/Resources << /Font << /F1 {font1} 0 R /F2 {font2} 0 R >> >> /Contents {content_id} 0 R >>"))
    objects[catalog_id - 1] = f"<< /Type /Catalog /Pages {pages_id} 0 R >>"
    objects[pages_id - 1] = f"<< /Type /Pages /Kids [{' '.join(f'{p} 0 R' for p in page_ids)}] /Count {len(page_ids)} >>"
    info_id = add(f"<< /Producer (SmartDocuments mock) /Title {pdf_string(doc['title'])} >>")

    out = io.BytesIO()
    out.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for number, obj in enumerate(objects, start=1):
        offsets.append(out.tell())
        body = obj if isinstance(obj, bytes) else obj.encode("latin-1")
        out.write(f"{number} 0 obj\n".encode() + body + b"\nendobj\n")
    xref = out.tell()
    out.write(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for offset in offsets:
        out.write(f"{offset:010d} 00000 n \n".encode())
    out.write(f"trailer\n<< /Size {len(objects) + 1} /Root {catalog_id} 0 R /Info {info_id} 0 R >>\n"
              f"startxref\n{xref}\n%%EOF\n".encode())
    return out.getvalue()


def file_base_name(template_name):
    slug = re.sub(r"[^\w\-]+", "-", template_name.strip().lower()).strip("-")
    return slug or "document"


def generate_all_formats(doc):
    base = file_base_name(doc["template"])
    return {
        "DOCX": (f"{base}.docx", make_docx(doc)),
        "PDF": (f"{base}.pdf", make_pdf(doc)),
        "XML": (f"{base}_answer.xml", make_xml(doc).encode("utf-8")),
        "HTML": (f"{base}.html", make_html(doc).encode("utf-8")),
    }


def smartdocuments_response(files):
    # The plugin streams this JSON and expects "filename", then "document.data", then "outputFormat" per file.
    return {"file": [{"filename": name, "document": {"data": base64.b64encode(content).decode()}, "outputFormat": fmt}
                     for fmt, (name, content) in files.items()]}


def error_page(status, message):
    reason = {400: "Bad Request", 401: "Unauthorized", 500: "Internal Server Error"}.get(status, "Error")
    return (f"<!doctype html><html lang=\"en\"><head><title>HTTP Status {status} - {reason}</title></head><body>"
            f"<h1>HTTP Status {status} - {reason}</h1><hr/><p><b>Type</b> Status Report</p>"
            f"<p><b>Message</b> {html.escape(message)}</p><hr/><h3>SmartDocuments mock</h3></body></html>")


# --------------------------------------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    server_version = "SmartDocumentsMock/1.0"

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)

    # Helpers ---------------------------------------------------------------------------------------------------------

    def send(self, status, body=b"", content_type="application/json; charset=utf-8", headers=None):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_json(self, status, data):
        self.send(status, json.dumps(data, ensure_ascii=False))

    def read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def basic_auth_user(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return None, False
        try:
            user, _, password = base64.b64decode(header[6:]).decode("utf-8").partition(":")
        except Exception:
            return None, False
        return user, user == USERNAME and password == PASSWORD

    def simulated_failure(self):
        with lock:
            status = settings["failureStatus"]
            if status and settings["failureOnce"]:
                settings["failureStatus"] = None
            delay = settings["delayMs"]
        if delay:
            time.sleep(delay / 1000)
        return status

    def log_request_entry(self, entry):
        with lock:
            request_log.insert(0, entry)
            for old in request_log[MAX_LOG_ENTRIES:]:
                generated_files.pop(old["id"], None)
            del request_log[MAX_LOG_ENTRIES:]

    def new_entry(self, user, started):
        return {"id": uuid.uuid4().hex[:12], "time": datetime.datetime.now().isoformat(timespec="seconds"),
                "method": self.command, "path": self.path, "user": user, "durationMs": None, "status": None,
                "templateGroup": None, "template": None, "customerData": None, "message": None, "files": [],
                "missingPlaceholders": [], "payloadFormat": None, "rawBody": None, "keptLineBreaks": [],
                "lostLineBreaks": [], "started": started}

    def finish_entry(self, entry, status, message=None):
        entry["status"] = status
        entry["message"] = message
        entry["durationMs"] = int((time.time() - entry.pop("started")) * 1000)
        self.log_request_entry(entry)

    # Routing ---------------------------------------------------------------------------------------------------------

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/", "/index.html"):
            with open(UI_FILE, encoding="utf-8") as f:
                return self.send(200, f.read(), "text/html; charset=utf-8")
        if path == "/health":
            return self.send_json(200, {"status": "UP"})
        if path == "/sdapi/structure":
            return self.handle_structure()
        if path == "/api/requests":
            with lock:
                return self.send_json(200, request_log)
        if path == "/api/templates":
            return self.send_json(200, {"templates": load_templates(), "customized": os.path.isfile(TEMPLATES_FILE)})
        if path == "/api/settings":
            with lock:
                return self.send_json(200, {**settings, "username": USERNAME, "password": PASSWORD})
        match = re.fullmatch(r"/files/(\w+)/(\w+)", path)
        if match:
            return self.handle_file(match.group(1), match.group(2).upper())
        self.send(404, error_page(404, "Not found"), "text/html; charset=utf-8")

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/wsxmldeposit/deposit/unattended":
            return self.handle_generate()
        if path == "/api/preview":
            return self.handle_preview()
        if path == "/api/templates/reset":
            reset_templates()
            return self.send_json(200, {"templates": load_templates(), "customized": False})
        self.send(404, error_page(404, "Not found"), "text/html; charset=utf-8")

    def do_PUT(self):
        path = urlparse(self.path).path
        try:
            data = json.loads(self.read_body() or b"{}")
        except json.JSONDecodeError as e:
            return self.send_json(400, {"error": f"Invalid JSON: {e}"})
        if path == "/api/templates":
            try:
                save_templates(data)
            except ValueError as e:
                return self.send_json(400, {"error": str(e)})
            return self.send_json(200, {"templates": load_templates(), "customized": True})
        if path == "/api/settings":
            with lock:
                status = data.get("failureStatus")
                settings["failureStatus"] = int(status) if status else None
                settings["failureOnce"] = bool(data.get("failureOnce", True))
                settings["delayMs"] = max(0, min(int(data.get("delayMs") or 0), 120000))
                settings["simulateWhitespace"] = bool(data.get("simulateWhitespace", True))
                return self.send_json(200, {**settings, "username": USERNAME, "password": PASSWORD})
        self.send(404, error_page(404, "Not found"), "text/html; charset=utf-8")

    def do_DELETE(self):
        if urlparse(self.path).path == "/api/requests":
            with lock:
                request_log.clear()
                generated_files.clear()
            return self.send_json(200, [])
        self.send(404, error_page(404, "Not found"), "text/html; charset=utf-8")

    # SmartDocuments API ----------------------------------------------------------------------------------------------

    def check_access(self, entry):
        user, valid = self.basic_auth_user()
        entry["user"] = user
        failure = self.simulated_failure()
        if not valid:
            message = "Missing or wrong credentials" if user else "No basic auth credentials"
            self.send(401, error_page(401, message), "text/html; charset=utf-8",
                      {"WWW-Authenticate": 'Basic realm="SmartDocuments"'})
            self.finish_entry(entry, 401, message)
            return False
        if failure:
            message = f"Simulated error {failure} (Settings tab)"
            self.send(failure, error_page(failure, message), "text/html; charset=utf-8")
            self.finish_entry(entry, failure, message)
            return False
        return True

    def handle_structure(self):
        entry = self.new_entry(None, time.time())
        if not self.check_access(entry):
            return
        self.send(200, structure_xml(load_templates()), "application/xml; charset=utf-8")
        self.finish_entry(entry, 200, "Returned the template structure")

    def handle_generate(self):
        entry = self.new_entry(None, time.time())
        raw = self.read_body()
        is_xml = "xml" in (self.headers.get("Content-Type") or "").lower()
        entry["payloadFormat"] = "XML" if is_xml else "JSON"
        entry["rawBody"] = raw[:MAX_RAW_BODY].decode("utf-8", errors="replace")
        with lock:
            simulate = settings["simulateWhitespace"]
        error = None
        if is_xml:
            try:
                data, group_name, template_name, kept, lost = parse_xml_request(raw, simulate)
                entry.update(templateGroup=group_name, template=template_name, customerData=data,
                             keptLineBreaks=kept, lostLineBreaks=lost)
            except ValueError as e:
                error = str(e)
        else:
            try:
                request = json.loads(raw or b"{}")
                if not isinstance(request, dict):
                    raise ValueError
                selection = (request.get("SmartDocument") or {}).get("Selection") or {}
                lost = []
                data = request.get("customerData") or {}
                if simulate:
                    data = simulate_json_whitespace(data, "", lost)
                else:
                    simulate_json_whitespace(data, "", lost)
                entry.update(templateGroup=selection.get("TemplateGroup"), template=selection.get("Template"),
                             customerData=data, lostLineBreaks=lost)
            except ValueError:
                error = "The request body is not valid JSON"
        if not self.check_access(entry):
            return
        if error:
            code = "INVALID_XML" if is_xml else "INVALID_JSON"
            self.send(400, error_page(400, f"{code}: {error}"), "text/html; charset=utf-8")
            return self.finish_entry(entry, 400, error)

        group = find_group(load_templates()["groups"], entry["templateGroup"])
        template = next((t for t in (group or {}).get("templates", []) if t.get("name") == entry["template"]), None)
        if not template:
            reason = (f"Template group '{entry['templateGroup']}' does not exist" if not group
                      else f"Template '{entry['template']}' does not exist in group '{entry['templateGroup']}'")
            self.send(400, error_page(400, "INVALID_XML: No valid template specified"), "text/html; charset=utf-8")
            return self.finish_entry(entry, 400, reason)

        doc = render_document(template, group["name"], entry["customerData"])
        files = generate_all_formats(doc)
        self.send_json(200, smartdocuments_response(files))
        with lock:
            generated_files[entry["id"]] = files
        entry["files"] = [{"format": fmt, "filename": name, "size": len(content)} for fmt, (name, content) in files.items()]
        entry["missingPlaceholders"] = doc["missing"]
        self.finish_entry(entry, 200, f"Generated '{doc['title']}' in {len(files)} formats")

    # UI API ----------------------------------------------------------------------------------------------------------

    def handle_file(self, request_id, fmt):
        with lock:
            files = generated_files.get(request_id)
        if not files or fmt not in files:
            return self.send(404, error_page(404, "This document is no longer available"), "text/html; charset=utf-8")
        name, content = files[fmt]
        download = "download" in parse_qs(urlparse(self.path).query)
        disposition = f'{"attachment" if download else "inline"}; filename="{name}"'
        self.send(200, content, CONTENT_TYPES[fmt], {"Content-Disposition": disposition})

    def handle_preview(self):
        try:
            data = json.loads(self.read_body() or b"{}")
            template = data["template"]
            doc = render_document(template, data.get("group") or "Preview", data.get("customerData") or {})
        except (KeyError, TypeError, json.JSONDecodeError) as e:
            return self.send_json(400, {"error": f"Invalid preview request: {e}"})
        fmt = (data.get("format") or "PDF").upper()
        name, content = generate_all_formats(doc)[fmt]
        self.send(200, content, CONTENT_TYPES[fmt], {"Content-Disposition": f'inline; filename="{name}"'})


if __name__ == "__main__":
    print(f"SmartDocuments mock listening on port {PORT} (user '{USERNAME}')", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
