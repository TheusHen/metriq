#!/usr/bin/env python3
"""Build the expanded bundled food pack from open data (USDA FDC + Open Food Facts).

The pack is written atomically by convert_expanded.write_if_valid, so any network
or conversion failure leaves the previously bundled ``foods.tsv`` untouched.

No API key is required: ``FDC_API_KEY`` / ``USDA_API_KEY`` (or ``--api-key``)
override the public ``DEMO_KEY`` default.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))

from convert_expanded import (
    build_pack, off_item_to_row, read_seed_pack, usda_item_to_row, write_if_valid,
)
from process_usda import load_mapping

FDC_SEARCH = "https://api.nal.usda.gov/fdc/v1/foods/search"
OFF_SEARCH = "https://world.openfoodfacts.org/cgi/search.pl"
DEFAULT_FIELDS = "code,product_name,brands,nutriments,serving_size,serving_quantity"
DEFAULT_OUT = ROOT / "core-data" / "src" / "main" / "assets" / "foods.tsv"
DEFAULT_SEED = HERE.parent / "data" / "seed_foods.tsv"
DEFAULT_MAPPING = HERE.parent / "config" / "usda_nutrients.yaml"

# A short, identifying UA is required by OFF; it contains no personal data.
USER_AGENT = "Metriq/0.1 (open-data food pack build)"


def get_json(url, attempts=24):
    """GET a JSON payload, waiting out DEMO_KEY's rolling rate limit.

    FDC's public key permits only a handful of requests per window; a 429 must be
    retried after the window resets rather than aborting the whole build.
    """
    import time

    last_error = None
    for attempt in range(attempts):
        request = urllib.request.Request(url, headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/json",
        })
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code == 429 and attempt < attempts - 1:
                retry_after = error.headers.get("Retry-After")
                time.sleep(float(retry_after) if retry_after else 70.0)
                continue
            if error.code not in (500, 502, 503, 504) or attempt == attempts - 1:
                raise
        except (urllib.error.URLError, TimeoutError, ValueError) as error:
            last_error = error
            if attempt == attempts - 1:
                raise
        time.sleep(min(20, 2 * (2 ** min(attempt, 3))))
    raise last_error  # pragma: no cover - loop always returns or raises


def fetch_usda(api_key, data_types, mapping, max_pages, page_size=200):
    """Download FDC search pages. ``data_types`` is a list of FDC type names."""
    rows = []
    for data_type in data_types:
        for page in range(1, max_pages + 1):
            params = {
                "api_key": api_key,
                "query": "",
                "dataType": data_type,
                "pageSize": page_size,
                "pageNumber": page,
            }
            url = FDC_SEARCH + "?" + urllib.parse.urlencode(params)
            payload = get_json(url)
            foods = payload.get("foods") or []
            if not foods:
                break
            for item in foods:
                row = usda_item_to_row(item, mapping)
                if row is not None:
                    rows.append(row)
            total_pages = int(payload.get("totalPages") or page)
            if page >= total_pages:
                break
            import time
            time.sleep(0.4)
    return rows


def fetch_off(terms, per_term, fields=DEFAULT_FIELDS):
    """Download a small, representative sample of OFF products by search term."""
    rows = []
    for term in terms:
        params = {
            "search_terms": term,
            "json": "1",
            "page_size": per_term,
            "fields": fields,
        }
        url = OFF_SEARCH + "?" + urllib.parse.urlencode(params)
        payload = get_json(url)
        products = payload.get("products") or []
        for item in products:
            row = off_item_to_row(item)
            if row is not None:
                rows.append(row)
        import time
        time.sleep(0.5)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--seed", type=Path, default=DEFAULT_SEED)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--api-key", default=os.environ.get("FDC_API_KEY")
                        or os.environ.get("USDA_API_KEY") or "DEMO_KEY")
    parser.add_argument("--staples", type=int, default=3000)
    parser.add_argument("--branded", type=int, default=200)
    # Foundation has ~400 items; ~13 SR Legacy pages bring staples to ~3000.
    parser.add_argument("--pages", type=int, default=13)
    parser.add_argument("--off-per-term", type=int, default=25)
    parser.add_argument("--off-terms", nargs="+", default=[
        "chocolate", "coffee", "milk", "yogurt", "bread", "pasta",
        "cereal", "cheese", "juice", "peanut butter", "pizza", "rice",
    ])
    parser.add_argument("--max-bytes", type=int, default=2_000_000)
    parser.add_argument(
        "--offline", action="store_true",
        help="use only the bundled seed (no network); safe cap when FDC quota is exhausted",
    )
    args = parser.parse_args()

    seed_rows = read_seed_pack(args.seed) if args.seed.exists() else []
    print("seed: %d bundled foods" % len(seed_rows))

    if args.offline:
        staples, branded_usda, branded_off = [], [], []
    else:
        mapping = load_mapping(args.mapping if args.mapping.exists() else None)
        staples = fetch_usda(
            args.api_key, ["Foundation", "SR Legacy"], mapping, args.pages,
        )
        print("USDA staples: %d" % len(staples))

        branded_usda = fetch_usda(args.api_key, ["Branded"], mapping, 1)
        print("USDA branded: %d" % len(branded_usda))

        branded_off = fetch_off(args.off_terms, args.off_per_term)
        print("OFF products: %d" % len(branded_off))

    pack = build_pack(
        seed_rows, staples + branded_usda, branded_off,
        staples_cap=args.staples, branded_cap=args.branded,
    )
    write_if_valid(pack, args.out, max_bytes=args.max_bytes,
                   min_rows=50 if args.offline else 100)
    barcoded = sum(1 for row in pack if row.get("barcode"))
    print("wrote %d foods (%d staples, %d barcoded) to %s"
          % (len(pack), len(pack) - barcoded, barcoded, args.out))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # never leave a half-written asset behind
        print("expanded pack generation failed: %s" % error, file=sys.stderr)
        sys.exit(1)
