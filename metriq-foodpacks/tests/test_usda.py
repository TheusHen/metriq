import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))
from common import read_pack
from process_usda import convert


def write_csv(path, fieldnames, rows):
    with open(path, "w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def normalized_fixture(tmp_path):
    write_csv(
        tmp_path / "food.csv",
        ["fdc_id", "data_type", "description", "food_category_id"],
        [
            {"fdc_id": "1", "data_type": "SR Legacy", "description": "Test Apple",
             "food_category_id": "9"},
            {"fdc_id": "2", "data_type": "SR Legacy", "description": "No macros",
             "food_category_id": "9"},
        ],
    )
    amounts = {
        "1008": 52, "1003": 0.3, "1004": 0.2, "1005": 14,
        "1079": 2.4, "2000": 10, "1093": 1, "1092": 107,
        "1087": 6, "1089": 0.1, "1090": 5, "1095": 0.04,
        "1253": 0, "1106": 3, "1162": 4.6, "1114": 0, "1178": 0,
    }
    write_csv(
        tmp_path / "food_nutrient.csv",
        ["id", "fdc_id", "nutrient_id", "amount"],
        [{"id": str(index), "fdc_id": "1", "nutrient_id": key, "amount": value}
         for index, (key, value) in enumerate(amounts.items(), start=1)]
        + [{"id": "99", "fdc_id": "2", "nutrient_id": "1093", "amount": 100}],
    )
    write_csv(
        tmp_path / "nutrient.csv",
        ["id", "name", "unit_name"],
        [{"id": "1008", "name": "Energy", "unit_name": "KCAL"},
         {"id": "1093", "name": "Sodium", "unit_name": "MG"}],
    )


def test_normalized_fdc_tables(tmp_path):
    normalized_fixture(tmp_path)
    out = tmp_path / "usda.tsv"
    convert(tmp_path, out)
    rows = read_pack(out)
    assert [row["name"] for row in rows] == ["Test Apple"]  # "No macros" skipped
    row = rows[0]
    assert row["kcal"] == 52
    assert row["protein"] == 0.3
    assert row["fat"] == 0.2
    assert row["carbs"] == 14
    assert row["fiber"] == 2.4
    assert row["sugar"] == 10
    assert row["sodium"] == 1
    assert row["potassium"] == 107
    assert row["calcium"] == 6
    assert row["iron"] == 0.1
    assert row["magnesium"] == 5
    assert row["zinc"] == 0.04
    assert row["cholesterol"] == 0
    assert row["vitaminA"] == 3
    assert row["vitaminC"] == 4.6
    assert row["vitaminD"] == 0
    assert row["vitaminB12"] == 0


def test_wide_csv_still_supported(tmp_path):
    source = tmp_path / "wide.csv"
    write_csv(
        source,
        ["description", "1008", "1003", "1004", "1005", "1093"],
        [{"description": "Wide Food", "1008": "100", "1003": "5",
          "1004": "2", "1005": "20", "1093": "50"}],
    )
    out = tmp_path / "wide.tsv"
    convert(source, out)
    rows = read_pack(out)
    assert len(rows) == 1
    assert rows[0]["kcal"] == 100
    assert rows[0]["protein"] == 5
    assert rows[0]["sodium"] == 50
