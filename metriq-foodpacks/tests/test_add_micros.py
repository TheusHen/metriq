import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))
from add_micros import enrich
from common import FIELDS, read_pack, write_pack


def test_enrich_preserves_values_without_inventing_data(tmp_path):
    """No enrichment source is bundled: this script only normalizes the schema."""
    source = tmp_path / "in.tsv"
    out = tmp_path / "out.tsv"
    row = {field: None for field in FIELDS}
    row.update({"name": "Oats", "kcal": 380, "sodium": 400, "vitaminC": 2})
    write_pack([row], source)

    enrich(source, out)

    result = read_pack(out)[0]
    assert all(field in result for field in FIELDS)
    # measured values are preserved
    assert result["sodium"] == 400
    assert result["vitaminC"] == 2
    # unknown values stay unknown -- no data source is shipped with this script
    assert result["iron"] is None
    assert result["vitaminD"] is None
