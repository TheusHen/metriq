import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))
from common import FIELDS, dedupe, manifest, number, read_pack, write_pack


def sample():
    row = {field: None for field in FIELDS}
    row.update({"name": "Oats", "brand": "Acme", "barcode": "123", "kcal": 380.0,
                "protein": 12.0, "fat": 7.0, "carbs": 65.0})
    return row


def test_write_read_roundtrip(tmp_path):
    path = tmp_path / "pack.tsv"
    write_pack([sample()], path)
    rows = read_pack(path)
    assert len(rows) == 1
    assert rows[0]["name"] == "Oats"
    assert rows[0]["kcal"] == 380.0


def test_dedupe_by_barcode():
    a, b = sample(), sample()
    b["protein"] = 10.0
    assert len(dedupe([a, b])) == 1


def test_manifest(tmp_path):
    write_pack([sample()], tmp_path / "base.tsv")
    data = manifest(tmp_path)
    assert data["packs"][0]["count"] == 1
    assert len(data["packs"][0]["sha256"]) == 64


def test_blank_rows_ignored(tmp_path):
    path = tmp_path / "p.tsv"
    path.write_text("name\tbrand\n\n", encoding="utf-8")
    assert read_pack(path) == []


def test_number_thousands_separator():
    assert number("1,234") == 1234.0
    assert number("1,234,567") == 1234567.0
    # decimal comma must keep working
    assert number("1,5") == 1.5
    assert number(None) is None


def test_manifest_excludes_header(tmp_path):
    path = tmp_path / "with_header.tsv"
    header = "\t".join(FIELDS)
    data_row = "\t".join(
        "Oats" if field == "name" else ("380" if field == "kcal" else "")
        for field in FIELDS
    )
    path.write_text("\n".join([header, data_row, data_row]) + "\n", encoding="utf-8")
    entry = next(p for p in manifest(tmp_path)["packs"] if p["file"] == path.name)
    assert entry["count"] == 2
