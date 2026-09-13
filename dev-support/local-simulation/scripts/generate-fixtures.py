#!/usr/bin/env python3
"""Generate the binary Parquet/XLSX fixtures from the canonical CSV sample."""

from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
FILES = ROOT / "files"


def rows() -> list[dict[str, object]]:
    with (FILES / "orders.csv").open(newline="", encoding="utf-8") as stream:
        result = []
        for row in csv.DictReader(stream):
            result.append({
                "id": int(row["id"]),
                "customer_id": int(row["customer_id"]),
                "order_date": row["order_date"],
                "status": row["status"],
                "amount": float(row["amount"]),
                "nullable_note": row["nullable_note"] or None,
            })
        return result


def write_parquet(data: list[dict[str, object]]) -> None:
    import pyarrow as pa
    import pyarrow.parquet as pq

    table = pa.Table.from_pylist(data)
    pq.write_table(table, FILES / "orders.parquet", row_group_size=3)


def write_xlsx(data: list[dict[str, object]]) -> None:
    headers = list(data[0])
    values = [headers] + [[row[key] for key in headers] for row in data]
    rows_xml = []
    for row_number, value_row in enumerate(values, start=1):
        cells = []
        for column_number, value in enumerate(value_row):
            column = chr(ord("A") + column_number)
            ref = f"{column}{row_number}"
            if value is None:
                cells.append(f'<c r="{ref}"/>')
            elif isinstance(value, (int, float)):
                cells.append(f'<c r="{ref}"><v>{value}</v></c>')
            else:
                cells.append(f'<c r="{ref}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>')
        rows_xml.append(f'<row r="{row_number}">{"".join(cells)}</row>')

    files = {
        "[Content_Types].xml": """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
        "_rels/.rels": """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/_rels/workbook.xml.rels": """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
        "xl/workbook.xml": """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="orders" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        "xl/worksheets/sheet1.xml": f'''<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>{"".join(rows_xml)}</sheetData></worksheet>''',
    }
    with zipfile.ZipFile(FILES / "orders.xlsx", "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in files.items():
            archive.writestr(name, content)


def write_manifest(data: list[dict[str, object]]) -> None:
    schema = [
        {"name": "id", "type": "int64"},
        {"name": "customer_id", "type": "int64"},
        {"name": "order_date", "type": "string"},
        {"name": "status", "type": "string"},
        {"name": "amount", "type": "double"},
        {"name": "nullable_note", "type": "string", "nullable": True},
    ]
    manifest = {"schemaVersion": 1, "rowCount": len(data), "files": []}
    for name, fmt in (("orders.csv", "CSV"), ("orders.json", "JSON"), ("orders.parquet", "PARQUET"), ("orders.xlsx", "XLSX")):
        path = FILES / name
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        manifest["files"].append({
            "objectKey": f"files/{name}",
            "fileName": name,
            "format": fmt,
            "bytes": path.stat().st_size,
            "sha256": digest,
            "schema": schema,
            "filter": {"field": "status", "operator": "eq", "value": "PAID", "expectedRows": 3},
            "joinKey": "id",
        })
    (FILES / "fixtures-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    data = rows()
    write_parquet(data)
    write_xlsx(data)
    write_manifest(data)
    print(f"Generated {len(data)} rows in orders.parquet and orders.xlsx")
