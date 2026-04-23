#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import requests


HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; BGTacticianBot/1.0)"
}


def fetch_html(bvid: str) -> str:
    url = f"https://www.bilibili.com/video/{bvid}/"
    response = requests.get(url, headers=HEADERS, timeout=20)
    response.raise_for_status()
    return response.text


def extract_initial_state(html: str) -> dict:
    match = re.search(r"__INITIAL_STATE__=(\{.*?\});\(function", html)
    if not match:
        raise RuntimeError("failed to find __INITIAL_STATE__")
    return json.loads(match.group(1))


def fetch_videoshot(bvid: str, cid: int) -> dict:
    response = requests.get(
        "https://api.bilibili.com/x/player/videoshot",
        params={"bvid": bvid, "cid": cid, "index": 1},
        headers=HEADERS,
        timeout=20,
    )
    response.raise_for_status()
    payload = response.json()
    if payload.get("code") != 0:
        raise RuntimeError(f"videoshot api failed: {payload}")
    return payload["data"]


def download_sheet(image_url: str) -> bytes:
    full_url = image_url if image_url.startswith("http") else f"https:{image_url}"
    response = requests.get(full_url, headers=HEADERS, timeout=20)
    response.raise_for_status()
    return response.content


def resolve_tile_index(indexes: list[int], seconds: int) -> int:
    if not indexes:
        raise RuntimeError("videoshot index is empty")
    candidate = 0
    for idx, at in enumerate(indexes):
        if at <= seconds:
            candidate = idx
        else:
            break
    return candidate


def crop_tile_box(tile_index: int, x_len: int, y_len: int, x_size: int, y_size: int) -> tuple[int, int, int, int]:
    col = tile_index % x_len
    row = tile_index // x_len
    if row >= y_len:
        raise RuntimeError(f"tile index out of range: {tile_index}")
    left = col * x_size
    top = row * y_size
    return left, top, left + x_size, top + y_size


def main() -> None:
    parser = argparse.ArgumentParser(description="Download a Bilibili storyboard tile for a given timestamp.")
    parser.add_argument("--bvid", required=True)
    parser.add_argument("--seconds", type=int, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    html = fetch_html(args.bvid)
    state = extract_initial_state(html)
    pages = state.get("videoData", {}).get("pages") or []
    if not pages:
        raise RuntimeError("no pages found in initial state")
    cid = pages[0]["cid"]

    shot = fetch_videoshot(args.bvid, cid)
    tile_index = resolve_tile_index(shot.get("index", []), args.seconds)
    sheet_bytes = download_sheet(shot["image"][0])
    crop_box = crop_tile_box(
        tile_index=tile_index,
        x_len=shot["img_x_len"],
        y_len=shot["img_y_len"],
        x_size=shot["img_x_size"],
        y_size=shot["img_y_size"],
    )

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    sheet_path = output_path.with_suffix(".sheet.jpg")
    sheet_path.write_bytes(sheet_bytes)
    output_path.write_text(
        json.dumps(
            {
                "bvid": args.bvid,
                "seconds": args.seconds,
                "cid": cid,
                "tile_index": tile_index,
                "crop_box": crop_box,
                "sheet_path": str(sheet_path),
                "image_url": shot["image"][0],
            },
            ensure_ascii=False,
            indent=2,
        ) + "\n",
        encoding="utf-8",
    )
    print(f"saved {output_path} and {sheet_path}")


if __name__ == "__main__":
    main()
