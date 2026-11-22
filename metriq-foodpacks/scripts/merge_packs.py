#!/usr/bin/env python3
"""Merge packs, preferring the first source for duplicate barcodes/names."""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import dedupe, read_pack, write_pack


def merge(paths, out):
    rows = []
    for path in paths:
        rows.extend(read_pack(path))
    rows = dedupe(rows)
    write_pack(rows, out)
    print(f"merged {len(rows)} foods into {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--inputs", nargs="+", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    merge([Path(p) for p in args.inputs], Path(args.out))
