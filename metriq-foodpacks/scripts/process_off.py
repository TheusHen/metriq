#!/usr/bin/env python3
"""Convert an Open Food Facts CSV (plain or gzip) into a Metriq pack.

OFF reports sodium/potassium/calcium/iron/magnesium/zinc/cholesterol in grams;
the pack schema stores them in mg, so those fields are converted with mg().
Items are filtered by config/off_filters.yaml unless --filters overrides it.
"""
import argparse
import csv
import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import FIELDS, dedupe, number, write_pack

DEFAULT_FILTERS = Path(__file__).parent.parent / "config" / "off_filters.yaml"


def open_csv(path):
    if str(path).endswith(".gz"):
        return gzip.open(path, "rt", encoding="utf-8", newline="")
    return open(path, "r", encoding="utf-8", newline="")


def mg(value):
    """Grams (OFF) -> mg (pack); unknown values stay unknown."""
    return None if value is None else value * 1000


def _parse_filters(text):
    """Minimal parser for the simple list/scalar filter config (no dependency)."""
    filters = {}
    current = None
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("- "):
            if current:
                filters.setdefault(current, []).append(line[2:].strip())
            continue
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if value:
            try:
                filters[key] = int(value)
            except ValueError:
                filters[key] = value
            current = None
        else:
            filters[key] = []
            current = key
    return filters


def load_filters(path=None):
    path = Path(path) if path else DEFAULT_FILTERS
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8")
    try:
        import yaml
    except ImportError:
        return _parse_filters(text)
    return yaml.safe_load(text) or {}


def passes_filters(item, filters):
    if not filters:
        return True
    allowed = filters.get("countries")
    if allowed:
        tags = [tag.strip() for tag in (item.get("countries_tags") or "").split(",") if tag.strip()]
        if not any(tag in allowed for tag in tags):
            return False
    minimum = filters.get("min_quality_score")
    if minimum is not None:
        quality = number(item.get("nutrition_quality"))
        if quality is None or quality < minimum:
            return False
    skipped = filters.get("skip_categories")
    if skipped:
        tags = [tag.strip() for tag in (item.get("categories_tags") or "").split(",") if tag.strip()]
        if any(tag in skipped for tag in tags):
            return False
    return True


def convert(path, out, limit=None, filters_path=None):
    csv.field_size_limit(sys.maxsize)
    filters = load_filters(filters_path)
    rows = []
    with open_csv(path) as source:
        for item in csv.DictReader(source):
            name = (item.get("product_name") or "").strip()
            if not name or not passes_filters(item, filters):
                continue
            nutrients = {}
            try:
                nutrients = json.loads(item.get("nutriments") or "{}")
            except (TypeError, ValueError):
                pass

            def nut(key):
                value = number(nutrients.get(key))
                if value is None and key == "energy-kcal_100g":
                    kj = number(nutrients.get("energy-kj_100g"))
                    value = kj / 4.184 if kj is not None else None
                return value

            kcal = nut("energy-kcal_100g")
            protein = nut("proteins_100g")
            fat = nut("fat_100g")
            carbs = nut("carbohydrates_100g")
            if None in (kcal, protein, fat, carbs):
                continue

            category = (item.get("categories_tags") or "").split(",")[0].strip()
            if category.startswith("en:"):
                category = category[3:]

            row = {field: None for field in FIELDS}
            row.update({
                "name": name,
                "brand": (item.get("brands") or "").strip() or None,
                "category": category or None,
                "barcode": (item.get("code") or "").strip() or None,
                "kcal": kcal,
                "protein": protein,
                "fat": fat,
                "carbs": carbs,
                "fiber": nut("fiber_100g"),
                "sugar": nut("sugars_100g"),
                "sodium": mg(nut("sodium_100g")),
                "saturatedFat": nut("saturated-fat_100g"),
                "transFat": nut("trans-fat_100g"),
                "cholesterol": mg(nut("cholesterol_100g")),
                "potassium": mg(nut("potassium_100g")),
                "calcium": mg(nut("calcium_100g")),
                "iron": mg(nut("iron_100g")),
                "magnesium": mg(nut("magnesium_100g")),
                "zinc": mg(nut("zinc_100g")),
                "vitaminA": nut("vitamin-a_100g"),
                "vitaminC": nut("vitamin-c_100g"),
                "vitaminD": nut("vitamin-d_100g"),
                "vitaminB12": nut("vitamin-b12_100g"),
                "servingLabel": (item.get("serving_size") or "").strip() or None,
                "servingGrams": number(item.get("serving_quantity")),
            })
            rows.append(row)
            if limit and len(rows) >= limit:
                break

    rows = dedupe(rows)
    write_pack(rows, out)
    print(f"wrote {len(rows)} foods to {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--filters", help="filter config (default: config/off_filters.yaml)")
    args = parser.parse_args()
    convert(args.input, args.out, args.limit, args.filters)
