"""Fixture-only tests for the expanded pack converter. No network is used."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))
from common import FIELDS, read_pack
from convert_expanded import (
    build_pack,
    estimated_size,
    off_item_to_row,
    read_seed_pack,
    usda_item_to_row,
    validate,
    write_if_valid,
)


def fdc_item(name="Test Apple", gtin=None, description_override=None):
    """A representative FDC v1 foods/search item with inline nutrients."""
    def nutrient(nid, value, unit="G"):
        return {"nutrientId": nid, "value": value, "unitName": unit}

    return {
        "fdcId": 1,
        "description": description_override if description_override is not None else name,
        "brandName": "Acme",
        "foodCategory": "Fruits and Vegetables",
        "gtin": gtin,
        "servingSize": 100.0,
        "servingSizeUnit": "g",
        "foodNutrients": [
            nutrient("1008", 52, "KCAL"),
            nutrient("1003", 0.3),
            nutrient("1004", 0.2),
            nutrient("1005", 14),
            nutrient("1079", 2.4),
            nutrient("2000", 10),
            nutrient("1093", 1, "MG"),
            nutrient("1092", 107, "MG"),
            nutrient("1087", 6, "MG"),
            nutrient("1089", 0.1, "MG"),
            nutrient("1090", 5, "MG"),
            nutrient("1095", 0.04, "MG"),
            nutrient("1253", 0, "MG"),
            nutrient("1106", 3, "UG_RAE"),
            nutrient("1162", 4.6, "MG"),
            nutrient("1114", 0, "UG"),
            nutrient("1178", 0, "UG"),
        ],
    }


def off_product(name="Test Cereal", code="1234567890"):
    return {
        "code": code,
        "product_name": name,
        "brands": "Acme",
        "categories_tags": "en:breakfast-cereals,en:world",
        "serving_size": "1 cup (40 g)",
        "serving_quantity": 40.0,
        "nutriments": {
            "energy-kcal_100g": 380,
            "proteins_100g": 12,
            "fat_100g": 7,
            "carbohydrates_100g": 65,
            "fiber_100g": 7,
            "sugars_100g": 20,
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


def test_read_seed_pack_handles_legacy_and_extended_rows(tmp_path):
    legacy = "Banana\tCommon\tfruit\t89\t1.1\t0.3\t22.8\t2.6\t12.2\t1\t1 medium\t118"
    extended = [""] * 26
    extended[0], extended[3], extended[4] = "Coca-Cola Original Taste", "5449000054227", "42"
    extended[24], extended[25] = "1 serving", "250"
    path = tmp_path / "seed.tsv"
    path.write_text(legacy + "\n" + "\t".join(extended) + "\n", encoding="utf-8")

    rows = read_seed_pack(path)
    assert len(rows) == 2
    assert rows[0]["name"] == "Banana"
    assert rows[0]["barcode"] is None  # legacy kcal must NOT become a barcode
    assert rows[0]["kcal"] == 89
    assert rows[0]["servingGrams"] == 118
    assert rows[1]["barcode"] == "5449000054227"
    assert rows[1]["kcal"] == 42


def test_usda_conversion_maps_macros_micros_and_serving():
    row = usda_item_to_row(fdc_item())
    assert row is not None
    assert row["name"] == "Test Apple"
    assert row["brand"] == "Acme"
    assert row["category"] == "Fruits and Vegetables"
    assert row["kcal"] == 52
    assert row["protein"] == 0.3
    assert row["fat"] == 0.2
    assert row["carbs"] == 14
    assert row["fiber"] == 2.4
    assert (row["potassium"], row["calcium"], row["iron"]) == (107, 6, 0.1)
    assert row["vitaminC"] == 4.6
    assert row["servingGrams"] == 100
    assert row["servingLabel"] == "1 g"
    assert set(row) == set(FIELDS)


def test_usda_conversion_keeps_barcode_and_normalizes_gram_minerals():
    item = fdc_item(gtin="0123456789012")
    item["foodNutrients"].append({"nutrientId": "1093", "value": 0.4, "unitName": "g"})
    row = usda_item_to_row(item)
    assert row["barcode"] == "0123456789012"
    assert row["sodium"] == 400  # last value wins, but declared grams -> mg


def test_usda_conversion_skips_incomplete_records():
    assert usda_item_to_row({"description": "No macros"}) is None
    assert usda_item_to_row({}) is None


def test_off_conversion_converts_grams_to_mg_and_keeps_serving():
    row = off_item_to_row(off_product())
    assert row is not None
    assert row["name"] == "Test Cereal"
    assert row["brand"] == "Acme"
    assert row["category"] == "breakfast-cereals"
    assert row["barcode"] == "1234567890"
    assert (row["kcal"], row["protein"], row["fat"], row["carbs"]) == (380, 12, 7, 65)
    assert row["sodium"] == 500
    assert row["potassium"] == 300
    assert row["calcium"] == 120
    assert row["iron"] == 2
    assert row["vitaminC"] == 6
    assert row["vitaminD"] is None
    assert row["servingLabel"] == "1 cup (40 g)"
    assert row["servingGrams"] == 40
    assert set(row) == set(FIELDS)


def test_off_conversion_can_fall_back_to_kilojoules():
    product = off_product()
    product["nutriments"].pop("energy-kcal_100g")
    product["nutriments"]["energy-kj_100g"] = 418.4
    assert off_item_to_row(product)["kcal"] == pytest.approx(100.0)


def test_off_conversion_skips_products_without_nutrition():
    assert off_item_to_row({"product_name": "Empty"}) is None
    assert off_item_to_row({}) is None


def test_build_pack_dedupes_by_barcode_and_name_and_caps_sources():
    seed = [usda_item_to_row(fdc_item(name="Banana"))]
    branded_seed = off_item_to_row(off_product("Coca-Cola", code="5449000054227"))
    duplicates = [
        usda_item_to_row(fdc_item(name="Banana")),  # same name -> deduped
        off_item_to_row(off_product("Coca-Cola", code="5449000054227")),  # same barcode
    ]
    many_staples = [usda_item_to_row(fdc_item(name="Staple %d" % i)) for i in range(12)]
    many_branded = [off_item_to_row(off_product("Brand %d" % i, code=str(9000000000000 + i)))
                    for i in range(12)]

    pack = build_pack(seed + [branded_seed], many_staples + duplicates, many_branded,
                      staples_cap=5, branded_cap=3)

    assert len([r for r in pack if not r["barcode"]]) == 5
    assert len([r for r in pack if r["barcode"]]) == 3
    assert any(r["barcode"] == "5449000054227" for r in pack)  # seed survives
    names = [r["name"] for r in pack]
    assert names.count("Banana") == 1
    assert names.count("Coca-Cola") == 1


def test_validate_enforces_minimum_and_size_budget():
    rows = [usda_item_to_row(fdc_item(name="Food %d" % i)) for i in range(119)]
    rows.append(off_item_to_row(off_product("Barcoded", code="5449000054227")))
    assert validate(rows, min_rows=100) is None
    assert "only" in validate(rows[:5], min_rows=100)
    assert "limit" in validate(rows, max_bytes=10)
    assert estimated_size(rows) > 0


def test_write_if_valid_is_atomic_and_leaves_existing_pack_on_failure(tmp_path):
    rows = [usda_item_to_row(fdc_item(name="Food %d" % i)) for i in range(119)]
    rows.append(off_item_to_row(off_product("Barcoded", code="5449000054227")))
    target = tmp_path / "foods.tsv"
    target.write_text("original", encoding="utf-8")

    with pytest.raises(ValueError):
        write_if_valid(rows[:3], target, min_rows=100)
    assert target.read_text(encoding="utf-8") == "original"
    assert not (tmp_path / "foods.tsv.tmp").exists()

    assert write_if_valid(rows, target, min_rows=100) == target
    assert len(read_pack(target)) == 120
    assert not (tmp_path / "foods.tsv.tmp").exists()
