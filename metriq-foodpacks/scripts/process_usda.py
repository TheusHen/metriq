#!/usr/bin/env python3
"""Convert USDA FoodData Central exports into a Metriq pack.

The FDC full download ships normalized tables (food.csv + food_nutrient.csv),
which are joined by fdc_id. A legacy one-file wide CSV (nutrient ids as columns)
is still accepted. Nutrient ids are mapped via config/usda_nutrients.yaml
(simple key=value lines) so the build can be pinned.
"""
import argparse
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import FIELDS, dedupe, number, write_pack

DEFAULT_MAP = {
    "1008": "kcal", "1003": "protein", "1004": "fat", "1005": "carbs",
    "1079": "fiber", "2000": "sugar", "1093": "sodium",
    "1253": "cholesterol", "1092": "potassium", "1087": "calcium",
    "1089": "iron", "1090": "magnesium", "1091": "phosphorus",
    "1095": "zinc", "1106": "vitaminA", "1162": "vitaminC",
    "1114": "vitaminD", "1178": "vitaminB12",
}


def load_mapping(path):
    result = dict(DEFAULT_MAP)
    if path:
        for line in Path(path).read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                result[key.strip()] = value.strip()
    return result


def _iter_csv(path):
    with open(path, "r", encoding="utf-8", newline="") as source:
        for item in csv.DictReader(source):
            yield item


def _convert_wide(path, mapping, limit=None):
    rows = []
    for item in _iter_csv(path):
        name = (item.get("description") or item.get("name") or "").strip()
        if not name:
            continue
        row = {field: None for field in FIELDS}
        row.update({
            "name": name,
            "brand": (item.get("brand_name") or item.get("brandOwner") or "").strip() or None,
            "category": (item.get("food_category") or item.get("fdc_category") or "").strip() or None,
            "barcode": (item.get("gtin") or item.get("upc") or "").strip() or None,
        })
        for nutrient_id, field in mapping.items():
            value = number(item.get(nutrient_id))
            if value is not None:
                row[field] = value
        if None in (row["kcal"], row["protein"], row["fat"], row["carbs"]):
            continue
        rows.append(row)
        if limit and len(rows) >= limit:
            break
    return rows


def _convert_normalized(food_path, nutrient_path, mapping, limit=None):
    amounts = {}
    for item in _iter_csv(nutrient_path):
        fdc_id = (item.get("fdc_id") or "").strip()
        field = mapping.get((item.get("nutrient_id") or "").strip())
        if not fdc_id or not field:
            continue
        value = number(item.get("amount"))
        if value is not None:
            amounts.setdefault(fdc_id, {})[field] = value

    rows = []
    for item in _iter_csv(food_path):
        name = (item.get("description") or "").strip()
        if not name:
            continue
        fdc_id = (item.get("fdc_id") or "").strip()
        row = {field: None for field in FIELDS}
        row.update({
            "name": name,
            "brand": (item.get("brand_name") or "").strip() or None,
            "category": (item.get("food_category_id") or "").strip() or None,
            "barcode": (item.get("gtin") or "").strip() or None,
        })
        for field, value in amounts.get(fdc_id, {}).items():
            row[field] = value
        if None in (row["kcal"], row["protein"], row["fat"], row["carbs"]):
            continue
        rows.append(row)
        if limit and len(rows) >= limit:
            break
    return rows


def resolve_sources(path):
    """Find the normalized tables for a directory or a food.csv path."""
    path = Path(path)
    if path.is_dir():
        return path / "food.csv", path / "food_nutrient.csv"
    if path.name.lower() == "food.csv" and path.with_name("food_nutrient.csv").exists():
        return path, path.with_name("food_nutrient.csv")
    return path, None


def convert(path, out, mapping_path=None, limit=None):
    mapping = load_mapping(mapping_path)
    csv.field_size_limit(sys.maxsize)
    food_path, nutrient_path = resolve_sources(path)
    if nutrient_path is not None and nutrient_path.exists():
        rows = _convert_normalized(food_path, nutrient_path, mapping, limit)
    else:
        rows = _convert_wide(food_path, mapping, limit)

    rows = dedupe(rows)
    write_pack(rows, out)
    print(f"wrote {len(rows)} foods to {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--mapping")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    convert(args.input, args.out, args.mapping, args.limit)
