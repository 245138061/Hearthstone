#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen


HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; BGTacticianBot/1.0)"
}


def download(url: str) -> bytes:
    request = Request(url, headers=HEADERS)
    with urlopen(request, timeout=20) as response:
        return response.read()


def main() -> None:
    parser = argparse.ArgumentParser(description="Export battleground trinket catalog and optionally cache images.")
    parser.add_argument(
        "--metadata",
        default="app/src/main/assets/bgs_card_metadata.json",
        help="Path to bgs_card_metadata.json",
    )
    parser.add_argument(
        "--output",
        default="/tmp/bili_video_learn/trinket_catalog.tsv",
        help="Output TSV path",
    )
    parser.add_argument(
        "--image-dir",
        default="/tmp/bili_video_learn/trinket_catalog_images",
        help="Directory for downloaded trinket images",
    )
    parser.add_argument(
        "--download-images",
        action="store_true",
        help="Download jpg images from zerotoheroes while exporting",
    )
    args = parser.parse_args()

    payload = json.loads(Path(args.metadata).read_text(encoding="utf-8"))
    cards = payload["cards"]
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    image_dir = Path(args.image_dir)
    image_dir.mkdir(parents=True, exist_ok=True)

    rows: list[str] = []
    for card_id, item in cards.items():
        if item.get("type") != "BATTLEGROUND_TRINKET":
            continue
        spell_school = item.get("spell_school") or ""
        name = item.get("localized_name") or item.get("name") or card_id
        rows.append(f"{card_id}\t{spell_school}\t{name}")

        if not args.download_images:
            continue
        target = image_dir / f"{card_id}.jpg"
        if target.exists() and target.stat().st_size > 0:
            continue
        url = f"https://static.zerotoheroes.com/hearthstone/cardart/256x/{card_id}.jpg"
        try:
            target.write_bytes(download(url))
        except Exception:
            # 没拉到图就留给后面的匹配流程跳过，不能让整批导出直接失败。
            pass

    output_path.write_text("\n".join(sorted(rows)) + "\n", encoding="utf-8")
    print(f"exported {len(rows)} trinkets to {output_path}")


if __name__ == "__main__":
    main()
