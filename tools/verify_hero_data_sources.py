#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_name(value: str | None) -> str:
    return (value or "").strip()


def merge_hero_name_index(
    hero_name_index: dict[str, Any],
    card_metadata: dict[str, Any],
) -> dict[str, Any]:
    merged_by_id: dict[str, dict[str, Any]] = {}

    for hero in hero_name_index.get("heroes", []):
        hero_card_id = normalize_name(hero.get("heroCardId"))
        if not hero_card_id:
            continue
        aliases = [
            alias
            for alias in (
                normalize_name(alias)
                for alias in hero.get("aliases", [])
            )
            if alias
        ]
        name = normalize_name(hero.get("name"))
        localized_name = normalize_name(hero.get("localizedName"))
        if name and name not in aliases:
            aliases.append(name)
        if localized_name and localized_name not in aliases:
            aliases.append(localized_name)
        merged_by_id[hero_card_id] = {
            "heroCardId": hero_card_id,
            "name": name,
            "localizedName": localized_name,
            "aliases": aliases,
        }

    for hero_card_id, card in card_metadata.get("cards", {}).items():
        if normalize_name(card.get("type")) != "HERO":
            continue
        english_name = normalize_name(card.get("name"))
        localized_name = normalize_name(card.get("localized_name"))
        if not english_name and not localized_name:
            continue

        existing = merged_by_id.get(hero_card_id, {})
        aliases: list[str] = []
        for value in existing.get("aliases", []):
            normalized = normalize_name(value)
            if normalized and normalized not in aliases:
                aliases.append(normalized)
        for value in (english_name, localized_name, existing.get("name"), existing.get("localizedName")):
            normalized = normalize_name(value)
            if normalized and normalized not in aliases:
                aliases.append(normalized)

        merged_by_id[hero_card_id] = {
            "heroCardId": hero_card_id,
            "name": normalize_name(existing.get("name")) or english_name or localized_name,
            "localizedName": normalize_name(existing.get("localizedName")) or localized_name,
            "aliases": aliases,
        }

    return {
        "version": hero_name_index.get("version", ""),
        "heroes": sorted(merged_by_id.values(), key=lambda item: item["heroCardId"]),
    }


def verify_hero_data_sources(
    *,
    hero_stats_path: Path,
    card_metadata_path: Path,
    hero_name_index_path: Path,
) -> None:
    hero_stats = load_json(hero_stats_path)
    card_metadata = load_json(card_metadata_path)
    hero_name_index = load_json(hero_name_index_path)
    merged_index = merge_hero_name_index(hero_name_index, card_metadata)

    hero_stat_ids = {
        normalize_name(entry.get("heroCardId"))
        for entry in hero_stats.get("heroStats", [])
        if normalize_name(entry.get("heroCardId"))
    }
    merged_ids = {
        normalize_name(entry.get("heroCardId"))
        for entry in merged_index.get("heroes", [])
        if normalize_name(entry.get("heroCardId"))
    }

    missing_ids = sorted(hero_stat_ids - merged_ids)
    if missing_ids:
        raise ValueError(
            "hero name index does not cover all hero-stats ids after metadata merge: "
            + ", ".join(missing_ids)
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Verify hero-stats coverage against hero name index plus card metadata."
    )
    parser.add_argument("--hero-stats", required=True, help="Path to hero-stats.json")
    parser.add_argument("--card-metadata", required=True, help="Path to bgs-card-metadata.json")
    parser.add_argument("--hero-name-index", required=True, help="Path to bgs_hero_name_index.json")
    args = parser.parse_args()
    verify_hero_data_sources(
        hero_stats_path=Path(args.hero_stats),
        card_metadata_path=Path(args.card_metadata),
        hero_name_index_path=Path(args.hero_name_index),
    )


if __name__ == "__main__":
    main()
