#!/usr/bin/env python3
"""Write manifest.json with count, size and sha256 for every pack."""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import manifest


def main(packs_dir, out):
    data = manifest(packs_dir)
    Path(out).write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"wrote {out} with {len(data['packs'])} packs")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--packs", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    main(args.packs, args.out)
