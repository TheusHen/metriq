#!/usr/bin/env python3
"""Download the Open Food Facts product CSV dump."""
import argparse
import sys
import urllib.request

DEFAULT_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz"


def download(url, out):
    with urllib.request.urlopen(url, timeout=120) as response, open(out, "wb") as target:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            target.write(chunk)
    print(f"downloaded {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    try:
        download(args.url, args.out)
    except Exception as exc:  # network failures are explicit
        print(f"download failed: {exc}", file=sys.stderr)
        sys.exit(1)
