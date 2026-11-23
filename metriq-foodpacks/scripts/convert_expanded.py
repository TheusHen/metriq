#!/usr/bin/env python3
"""Pure conversion helpers for the expanded, multi-source food pack.

No network access lives here: every function takes already-downloaded data and
returns plain ``common.FIELDS`` rows. This keeps the converter unit-testable with
fixtures only (see tests/test_convert_expanded.py).
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import FIELDS, dedupe, number, write_pack
from process_off import mg


# FDC nutrient id -> pack column. Same ids as config/usda_nutrients.yaml; kept
# here so a fixture-driven test never has to read the config from disk.
USDA_NUTRIENTS = {
    "1008": "kcal", "1003": "protein", "1004": "fat", "1005": "carbs",
    "1079": "fiber", "2000": "sugar", "1093": "sodium",
    "1253": "cholesterol", "1092": "potassium", "1087": "calcium",
    "1089": "iron", "1090": "magnesium", "1095": "zinc",
    "1106": "vitaminA", "1162": "vitaminC", "1114": "vitaminD",
    "1178": "vitaminB12",
}

MG_FROM_G_FIELDS = {
    "sodium", "cholesterol", "potassium", "calcium", "iron", "magnesium", "zinc",
}

SERVING_UNITS = {"g", "gram", "grams", "ml", "mlt", "milliliter", "milliliters"}


def blank_row():
    return {field: None for field in FIELDS}


def read_seed_pack(path):
    """Read the bundled seed, understanding both 12-column legacy and 26-column rows.

    ``common.read_pack`` maps columns positionally to the 26-field schema, which
    would misread a legacy row's kcal value as a barcode. FoodRepository handles
    that offset at runtime; the generator must do the same when reusing the seed.
    """
    rows = []
    with open(path, "r", encoding="utf-8", newline="") as source:
        for line in source:
            cols = line.rstrip("\r\n").split("\t")
            if len(cols) < 11 or not cols[0].strip():
                continue
            if cols[0].strip().lower() == "name":
                continue
            row = blank_row()
            if len(cols) >= 26:
                for index, field in enumerate(FIELDS):
                    value = cols[index]
                    row[field] = value or None if field in (
                        "name", "brand", "category", "barcode", "servingLabel"
                    ) else number(value)
            else:
                row.update({
                    "name": cols[0].strip(),
                    "brand": cols[1].strip() or None,
                    "category": cols[2].strip() or None,
                    "kcal": number(cols[3]),
                    "protein": number(cols[4]),
                    "fat": number(cols[5]),
                    "carbs": number(cols[6]),
                    "fiber": number(cols[7]),
                    "sugar": number(cols[8]),
                    "sodium": number(cols[9]),
                    "servingLabel": cols[10].strip() or None,
                    "servingGrams": number(cols[11]) if len(cols) > 11 else None,
                })
            if row["kcal"] is None:
                continue
            rows.append(row)
    return rows


def _text(item, *keys):
    """First non-blank string among ``keys`` (FDC and OFF rename fields often)."""
    for key in keys:
        value = item.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def usda_item_to_row(item, mapping=None):
    """Convert one USDA FoodData Central ``foods/search`` item.

    Returns ``None`` for records that cannot make a useful food entry (no name or
    no complete macro profile), which is the same rule the legacy converter uses.
    """
    name = _text(item, "description", "name")
    if not name:
        return None

    mapping = mapping or USDA_NUTRIENTS
    row = blank_row()
    row.update({
        "name": name,
        "brand": _text(item, "brandName", "brand_name", "brandOwner"),
        "category": _text(item, "foodCategory", "food_category"),
        "barcode": _text(item, "gtin", "upc"),
    })

    for nutrient in item.get("foodNutrients") or []:
        if not isinstance(nutrient, dict):
            continue
        field = mapping.get(str(nutrient.get("nutrientId", "")).strip())
        if not field or field not in row:
            continue
        value = number(nutrient.get("value"))
        if value is not None:
            row[field] = value

    # FDC reports sodium/potassium/... in mg for Foundation/SR Legacy already.
    # Some branded records use grams for a handful of minerals; normalize only
    # when the declared unit explicitly says so.
    for nutrient in item.get("foodNutrients") or []:
        if not isinstance(nutrient, dict):
            continue
        field = mapping.get(str(nutrient.get("nutrientId", "")).strip())
        if field in MG_FROM_G_FIELDS and (nutrient.get("unitName") or "").strip().lower() == "g":
            if row.get(field) is not None:
                row[field] = mg(row[field])

    serving = number(item.get("servingSize"))
    unit = (item.get("servingSizeUnit") or "").strip().lower()
    if serving is not None and serving > 0 and unit in SERVING_UNITS:
        row["servingGrams"] = serving
        row["servingLabel"] = "1 " + unit

    if any(row.get(key) is None for key in ("kcal", "protein", "fat", "carbs")):
        return None
    return row


def off_item_to_row(item):
    """Convert one Open Food Facts ``search.pl`` product into an extended row."""
    name = _text(item, "product_name", "product_name_en", "generic_name")
    if not name:
        return None

    nutrients = item.get("nutriments") or {}
    if isinstance(nutrients, str):
        try:
            nutrients = json.loads(nutrients)
        except (TypeError, ValueError):
            nutrients = {}
    if not isinstance(nutrients, dict):
        nutrients = {}

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
        return None

    category = _text(item, "categories_tags") or ""
    category = category.split(",")[0].strip()
    if category.startswith("en:"):
        category = category[3:]

    row = blank_row()
    row.update({
        "name": name,
        "brand": _text(item, "brands", "brand"),
        "category": category or None,
        "barcode": _text(item, "code", "barcode"),
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
        "servingLabel": _text(item, "serving_size"),
        "servingGrams": number(item.get("serving_quantity")),
    })
    return row


def build_pack(seed_rows, usda_rows, off_rows, staples_cap=3000, branded_cap=200):
    """Merge all sources, dedupe by barcode/name and apply the bundle caps.

    Barcoded products are capped separately so a flood of branded items can never
    push the everyday staples out of the bundled pack. Seeded rows come first so
    a required offline product (and the canonical staple names) always survive.
    """
    all_rows = dedupe(list(seed_rows) + list(usda_rows) + list(off_rows))
    staples = [row for row in all_rows if not row.get("barcode")][:staples_cap]
    branded = [row for row in all_rows if row.get("barcode")][:branded_cap]
    return staples + branded


def estimated_size(rows):
    """Bytes the pack would occupy (used to guard the <2 MB asset budget)."""
    return sum(
        len(("\t".join("" if row.get(f) is None else str(row[f]) for f in FIELDS) + "\n").encode("utf-8"))
        for row in rows
    )


def validate(rows, max_bytes=2_000_000, min_rows=100):
    """Return an error string, or ``None`` when the pack is safe to ship."""
    if len(rows) < min_rows:
        return "only %d foods (need >= %d)" % (len(rows), min_rows)
    size = estimated_size(rows)
    if size > max_bytes:
        return "pack would be %d bytes (limit %d)" % (size, max_bytes)
    if not any(row.get("barcode") for row in rows):
        return "no barcoded products in pack"
    return None


def write_if_valid(rows, path, max_bytes=2_000_000, min_rows=100):
    """Atomically replace ``path`` only after the generated pack validates.

    A failed or partial generation therefore never destroys the bundled asset.
    """
    path = Path(path)
    error = validate(rows, max_bytes=max_bytes, min_rows=min_rows)
    if error:
        raise ValueError("refusing to overwrite %s: %s" % (path, error))

    temporary = path.with_name(path.name + ".tmp")
    write_pack(rows, temporary)
    if temporary.stat().st_size > max_bytes:
        temporary.unlink(missing_ok=True)
        raise ValueError("serialized pack exceeds %d bytes" % max_bytes)
    os.replace(temporary, path)
    return path
