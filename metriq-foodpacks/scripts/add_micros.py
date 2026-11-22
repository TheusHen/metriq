#!/usr/bin/env python3
"""Normalize a pack to the canonical column set (no enrichment is performed).

No micronutrient data source is shipped with this repository, so this script
cannot fill in unknown values: read_pack() already returns every FIELDS column,
with None for unknown values. Existing measured values are preserved and unknown
ones stay empty. Kept as an explicit normalization step in the build pipeline.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import FIELDS, read_pack, write_pack


def enrich(path, out):
    rows = read_pack(path)
    for row in rows:
        for field in FIELDS:
            row.setdefault(field, None)
    write_pack(rows, out)
    print(f"normalized {len(rows)} foods into {out} (no enrichment source bundled)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    enrich(Path(args.input), Path(args.out))
