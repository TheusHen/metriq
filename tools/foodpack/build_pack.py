#!/usr/bin/env python3
"""Convert an Open Food Facts CSV export into Metriq's bundled food TSV shape.

Usage:
    python tools/foodpack/build_pack.py --off products.csv --out foods.tsv

Input columns consumed: code, product_name, brands, categories_tags, nutriments,
serving_size, serving_quantity. Nutriments are a JSON object with *_100g keys.

The output has 12 tab-separated fields:
    name, brand, category, kcal, protein, fat, carbs, fiber, sugar, sodium,
    serving_label, serving_grams

Nutrition values are per 100 g. Open Food Facts data is licensed ODbL; keep attribution.
"""

import argparse
import csv
import json
import sys


def number(value):
    if value is None or value == "":
        return None
    try:
        return float(str(value).replace(",", "."))
    except ValueError:
        return None


def first_number(nutriments, *keys):
    for key in keys:
        value = number(nutriments.get(key))
        if value is not None:
            return value
    return None


def convert(path, out, limit=None, min_fields=4):
    seen = set()
    written = 0
    csv.field_size_limit(sys.maxsize)
    with open(path, "r", encoding="utf-8", newline="") as source, open(
        out, "w", encoding="utf-8", newline="\n"
    ) as target:
        reader = csv.DictReader(source)
        for row in reader:
            barcode = (row.get("code") or "").strip()
            if barcode and barcode in seen:
                continue
            name = (row.get("product_name") or "").strip()
            if not name:
                continue
            try:
                nutriments = json.loads(row.get("nutriments") or "{}")
            except (TypeError, ValueError):
                nutriments = {}

            kcal = first_number(nutriments, "energy-kcal_100g")
            if kcal is None:
                kj = first_number(nutriments, "energy-kj_100g")
                kcal = kj / 4.184 if kj is not None else None
            protein = first_number(nutriments, "proteins_100g")
            fat = first_number(nutriments, "fat_100g")
            carbs = first_number(nutriments, "carbohydrates_100g")

            fields = [x for x in (kcal, protein, fat, carbs) if x is not None]
            if len(fields) < min_fields:
                continue

            category = (row.get("categories_tags") or "").split(",")[0].strip()
            if category.startswith("en:"):
                category = category[3:]
            serving_label = (row.get("serving_size") or "").strip()
            serving_grams = number(row.get("serving_quantity"))

            values = [
                name,
                (row.get("brands") or "").strip(),
                category,
                kcal, protein, fat, carbs,
                first_number(nutriments, "fiber_100g"),
                first_number(nutriments, "sugars_100g"),
                first_number(nutriments, "sodium_100g"),
                serving_label,
                serving_grams,
            ]
            target.write("\t".join("" if v is None else str(v) for v in values) + "\n")
            if barcode:
                seen.add(barcode)
            written += 1
            if limit and written >= limit:
                break
    print(f"wrote {written} foods to {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--off", required=True, help="Open Food Facts CSV export")
    parser.add_argument("--out", required=True, help="Destination foods.tsv")
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()
    convert(args.off, args.out, args.limit)
