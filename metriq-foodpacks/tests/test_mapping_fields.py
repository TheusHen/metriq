"""Guard: every nutrient-id mapping must target a real pack column.

A mapped field that is not in ``common.FIELDS`` is silently dropped by
``write_pack``. This used to happen with USDA nutrient 1091 (phosphorus).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

from common import FIELDS
from convert_expanded import USDA_NUTRIENTS
from process_usda import DEFAULT_MAP


def load_usda_config():
    """Parse the simple key=value lines in config/usda_nutrients.yaml."""
    path = Path(__file__).parent.parent / "config" / "usda_nutrients.yaml"
    mapping = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            mapping[key.strip()] = value.strip()
    return mapping


def _unknown_fields(mapping):
    return sorted({field for field in mapping.values() if field not in FIELDS})


def test_process_usda_default_map_only_uses_pack_fields():
    assert not _unknown_fields(DEFAULT_MAP), (
        "DEFAULT_MAP targets unknown pack fields: %s" % _unknown_fields(DEFAULT_MAP)
    )


def test_convert_expanded_nutrients_only_use_pack_fields():
    assert not _unknown_fields(USDA_NUTRIENTS), (
        "USDA_NUTRIENTS targets unknown pack fields: %s" % _unknown_fields(USDA_NUTRIENTS)
    )


def test_usda_config_only_uses_pack_fields():
    config = load_usda_config()
    assert not _unknown_fields(config), (
        "config/usda_nutrients.yaml targets unknown pack fields: %s" % _unknown_fields(config)
    )


def test_phosphorus_is_not_mapped():
    """Phosphorus has no pack column, so any mapping for it would be dead data."""
    assert "phosphorus" not in DEFAULT_MAP.values()
    assert "phosphorus" not in USDA_NUTRIENTS.values()
    assert "phosphorus" not in load_usda_config().values()
