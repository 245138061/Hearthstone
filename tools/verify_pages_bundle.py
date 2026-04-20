#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from build_pages_bundle import verify_bundle


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify the generated GitHub Pages bundle for BGTactician.")
    parser.add_argument("--site-dir", required=True, help="Bundle directory to verify.")
    args = parser.parse_args()
    verify_bundle(Path(args.site_dir))


if __name__ == "__main__":
    main()
