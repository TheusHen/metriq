import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))
from common import read_pack
from process_off import convert


COLUMNS = [
    "product_name", "brands", "code", "categories_tags", "countries_tags",
    "nutrition_quality", "nutriments", "serving_size", "serving_quantity",
]


def off_item(name, code="1", **overrides):
    item = {
        "product_name": name,
        "brands": "Acme",
        "code": code,
        "categories_tags": "en:breakfast-cereals",
        "countries_tags": "en:world",
        "nutrition_quality": "10",
        "nutriments": {
            "energy-kcal_100g": 380,
            "proteins_100g": 12,
            "fat_100g": 7,
            "carbohydrates_100g": 65,
            "fiber_100g": 7,
            "sugars_100g": 20,
            "saturated-fat_100g": 3,
            "trans-fat_100g": 0.1,
            "sodium_100g": 0.5,
            "potassium_100g": 0.3,
            "calcium_100g": 0.12,
            "iron_100g": 0.002,
            "magnesium_100g": 0.05,
            "zinc_100g": 0.003,
            "cholesterol_100g": 0.01,
            "vitamin-a_100g": 10,
            "vitamin-c_100g": 6,
            "vitamin-d_100g": None,
            "vitamin-b12_100g": 0.000002,
        },
    }
    nutrients = item["nutriments"]
    for key, value in list(overrides.items()):
        if key.endswith("_100g"):
            nutrients[key] = overrides.pop(key)
    item.update(overrides)
    return item


def write_off_csv(path, items):
    with open(path, "w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=COLUMNS)
        writer.writeheader()
        for item in items:
            row = {column: "" for column in COLUMNS}
            row.update(item)
            if isinstance(row["nutriments"], dict):
                row["nutriments"] = json.dumps(row["nutriments"])
            writer.writerow(row)


def process(tmp_path, items):
    source = tmp_path / "off.csv"
    out = tmp_path / "off.tsv"
    write_off_csv(source, items)
    convert(source, out)
    return read_pack(out)


def test_mineral_units_are_mg(tmp_path):
    rows = process(tmp_path, [off_item("Cereal", magnesium_100g=None)])
    assert len(rows) == 1
    row = rows[0]
    assert row["sodium"] == 500
    assert row["potassium"] == 300
    assert row["calcium"] == 120
    assert row["iron"] == 2
    assert row["zinc"] == 3
    assert row["cholesterol"] == 10
    # grams stay grams, mcg stays mcg
    assert row["vitaminC"] == 6
    assert row["vitaminA"] == 10
    assert row["vitaminB12"] == 0.000002
    assert row["saturatedFat"] == 3
    assert row["transFat"] == 0.1
    assert row["fiber"] == 7
    assert row["sugar"] == 20
    # macros unchanged
    assert row["kcal"] == 380
    assert row["protein"] == 12
    assert row["fat"] == 7
    assert row["carbs"] == 65
    # unknown stays unknown, including through the mg helper
    assert row["magnesium"] is None
    assert row["vitaminD"] is None


def test_filters_exclude_unwanted_items(tmp_path):
    items = [
        off_item("Allowed"),
        off_item("Wrong country", code="2", countries_tags="en:othercountry"),
        off_item("Non food", code="3", categories_tags="en:non-food-products"),
        off_item("Low quality", code="4", nutrition_quality="1"),
    ]
    rows = process(tmp_path, items)
    assert [row["name"] for row in rows] == ["Allowed"]
