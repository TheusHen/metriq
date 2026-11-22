"""Shared pack helpers."""
import csv
import hashlib
import json
import re
from pathlib import Path

FIELDS = [
    "name", "brand", "category", "barcode",
    "kcal", "protein", "fat", "carbs", "fiber", "sugar", "sodium",
    "saturatedFat", "transFat", "cholesterol", "potassium", "calcium",
    "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD",
    "vitaminB12", "omega3", "servingLabel", "servingGrams",
]

MICRO_FIELDS = FIELDS[11:24]


THOUSANDS = re.compile(r"^[+-]?\d{1,3}(?:,\d{3})+$")


def number(value):
    if value is None or value == "":
        return None
    text = str(value).strip()
    if not text:
        return None
    # "1,234" is a thousands separator; "1,5" is a decimal comma.
    text = text.replace(",", "") if THOUSANDS.match(text) else text.replace(",", ".")
    try:
        return float(text)
    except (TypeError, ValueError):
        return None


def write_pack(rows, path):
    with open(path, "w", encoding="utf-8", newline="\n") as target:
        for row in rows:
            values = [row.get(field, "") for field in FIELDS]
            target.write("\t".join("" if v is None else str(v) for v in values) + "\n")
    return Path(path)


def read_pack(path):
    rows = []
    with open(path, "r", encoding="utf-8", newline="") as source:
        for line in source:
            line = line.rstrip("\n")
            if not line or line.lower().startswith("name\t"):
                continue
            cols = line.split("\t")
            if len(cols) < 4 or not cols[0].strip():
                continue
            row = {}
            for index, field in enumerate(FIELDS):
                raw = cols[index] if index < len(cols) else ""
                row[field] = number(raw) if field not in ("name", "brand", "category", "barcode", "servingLabel") else (raw or None)
            rows.append(row)
    return rows


def dedupe(rows):
    seen = set()
    out = []
    for row in rows:
        key = row.get("barcode") or row.get("name", "").strip().lower()
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


def manifest(packs_dir):
    entries = []
    for path in sorted(Path(packs_dir).glob("*.tsv")):
        data = path.read_bytes()
        entries.append({
            "id": path.stem,
            "file": path.name,
            "size": len(data),
            "count": sum(
                1 for line in data.decode("utf-8").splitlines()
                if line.strip() and not line.lower().startswith("name\t")
            ),
            "sha256": hashlib.sha256(data).hexdigest(),
        })
    return {"version": 1, "packs": entries}
