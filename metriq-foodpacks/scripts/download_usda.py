#!/usr/bin/env python3
"""Download USDA FoodData Central bulk files (API key required)."""
import argparse
import sys
import urllib.parse
import urllib.request

BASE = "https://fdc.nal.usda.gov/fdc-api/v2/downloads"


def download(api_key, name, out):
    url = f"{BASE}/{urllib.parse.quote(name)}?api_key={api_key}"
    with urllib.request.urlopen(url, timeout=120) as response, open(out, "wb") as target:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            target.write(chunk)
    print(f"downloaded {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--name", default="FoodData Central Full Download Files.zip")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    try:
        download(args.api_key, args.name, args.out)
    except Exception as exc:
        print(f"download failed: {exc}", file=sys.stderr)
        sys.exit(1)
