#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from import_external_strategies import load_json

DEFAULT_CARDS_EN_URL = "https://api.hearthstonejson.com/v1/latest/enUS/cards.json"
DEFAULT_CARDS_ZH_URL = "https://api.hearthstonejson.com/v1/latest/zhCN/cards.json"

RACE_TAGS = {
    "MURLOC": 2536,
    "DEMON": 2537,
    "MECHANICAL": 2539,
    "ELEMENTAL": 2540,
    "BEAST": 2542,
    "PIRATE": 2522,
    "DRAGON": 2523,
    "QUILBOAR": 2546,
    "NAGA": 2553,
    "UNDEAD": 2534,
}

INCLUDED_CARD_TYPES = {
    "MINION",
    "HERO",
    "HERO_POWER",
    "BATTLEGROUND_SPELL",
    "BATTLEGROUND_TRINKET",
    "BATTLEGROUND_QUEST_REWARD",
    "BATTLEGROUND_ANOMALY",
    "BATTLEGROUND_HERO_BUDDY",
}


def normalize_races(card: dict[str, Any]) -> list[str]:
    races = card.get("races")
    if isinstance(races, list):
        values = [str(value).strip() for value in races if str(value).strip()]
    else:
        race = str(card.get("race") or "").strip()
        values = [race] if race else []

    normalized: list[str] = []
    for value in values:
        if value not in normalized:
            normalized.append(value)
    return normalized


def load_cards(source: str) -> list[dict[str, Any]]:
    payload = load_json(source)
    if not isinstance(payload, list):
        raise ValueError(f"cards payload is invalid: {source}")
    return [card for card in payload if isinstance(card, dict)]


def build_dbf_lookup(cards: list[dict[str, Any]]) -> dict[int, str]:
    lookup: dict[int, str] = {}
    for card in cards:
        dbf_id = card.get("dbfId")
        card_id = str(card.get("id") or "").strip()
        if isinstance(dbf_id, int) and card_id:
            lookup[dbf_id] = card_id
    return lookup


def build_battleground_card_metadata(
    cards_en_source: str,
    cards_zh_source: str,
    version_label: str | None = None,
) -> dict[str, Any]:
    cards_en = load_cards(cards_en_source)
    cards_zh = load_cards(cards_zh_source)
    localized_name_map = {
        str(card.get("id") or "").strip(): str(card.get("name") or "").strip()
        for card in cards_zh
        if card.get("id") and card.get("name")
    }
    dbf_lookup = build_dbf_lookup(cards_en)

    entries: dict[str, dict[str, Any]] = {}
    included_types: dict[str, int] = {}

    for card in cards_en:
        card_id = str(card.get("id") or "").strip()
        if not card_id:
            continue
        if str(card.get("set") or "").strip() != "BATTLEGROUNDS":
            continue

        card_type = str(card.get("type") or "").strip()
        if card_type not in INCLUDED_CARD_TYPES:
            continue
        included_types[card_type] = included_types.get(card_type, 0) + 1

        premium_dbf_id = card.get("battlegroundsPremiumDbfId")
        normal_dbf_id = card.get("battlegroundsNormalDbfId")
        related_dbf_id = card.get("battlegroundsRelatedCard")

        entry = {
            "dbf_id": card.get("dbfId"),
            "name": str(card.get("name") or "").strip(),
            "localized_name": localized_name_map.get(card_id),
            "type": card_type,
            "tech_level": card.get("techLevel"),
            "races": normalize_races(card),
            "spell_school": str(card.get("spellSchool") or "").strip() or None,
            "is_pool_minion": bool(card.get("isBattlegroundsPoolMinion", False)),
            "is_pool_spell": bool(card.get("isBattlegroundsPoolSpell", False)),
            "premium_card_id": dbf_lookup.get(premium_dbf_id) if isinstance(premium_dbf_id, int) else None,
            "normal_card_id": dbf_lookup.get(normal_dbf_id) if isinstance(normal_dbf_id, int) else None,
            "related_card_id": dbf_lookup.get(related_dbf_id) if isinstance(related_dbf_id, int) else None,
        }
        entries[card_id] = entry

    timestamp = datetime.now(timezone.utc)
    return {
        "version": version_label or f"hsjson-latest-{timestamp.strftime('%Y-%m-%d')}",
        "generated_at": timestamp.isoformat().replace("+00:00", "Z"),
        "source": {
            "cards_en": cards_en_source,
            "cards_zh": cards_zh_source,
        },
        "race_tags": RACE_TAGS,
        "summary": {
            "card_count": len(entries),
            "type_counts": dict(sorted(included_types.items())),
        },
        "cards": dict(sorted(entries.items())),
    }


def build_strategy_validation_report(
    metadata: dict[str, Any],
    strategies_source: str,
) -> dict[str, Any]:
    payload = load_json(strategies_source)
    if not isinstance(payload, dict):
        raise ValueError(f"strategies payload is invalid: {strategies_source}")

    cards = metadata.get("cards")
    if not isinstance(cards, dict):
        raise ValueError("metadata payload is invalid")

    mismatched_stars: list[dict[str, Any]] = []
    non_minion_entries: list[dict[str, Any]] = []
    missing_metadata: list[dict[str, Any]] = []

    for comp in payload.get("comps", []):
        if not isinstance(comp, dict):
            continue
        comp_id = str(comp.get("id") or "")
        comp_name = str(comp.get("name") or "")
        for key_minion in comp.get("key_minions", []):
            if not isinstance(key_minion, dict):
                continue
            card_id = str(key_minion.get("card_id") or "").strip()
            if not card_id:
                continue
            metadata_entry = cards.get(card_id)
            if not isinstance(metadata_entry, dict):
                missing_metadata.append(
                    {
                        "comp_id": comp_id,
                        "comp_name": comp_name,
                        "card_id": card_id,
                        "name": key_minion.get("name"),
                        "strategy_star": key_minion.get("star"),
                    }
                )
                continue

            card_type = str(metadata_entry.get("type") or "")
            if card_type != "MINION":
                non_minion_entries.append(
                    {
                        "comp_id": comp_id,
                        "comp_name": comp_name,
                        "card_id": card_id,
                        "name": key_minion.get("name"),
                        "strategy_star": key_minion.get("star"),
                        "metadata_type": card_type,
                        "metadata_tech_level": metadata_entry.get("tech_level"),
                    }
                )
                continue

            strategy_star = key_minion.get("star")
            tech_level = metadata_entry.get("tech_level")
            if isinstance(strategy_star, int) and isinstance(tech_level, int) and strategy_star != tech_level:
                mismatched_stars.append(
                    {
                        "comp_id": comp_id,
                        "comp_name": comp_name,
                        "card_id": card_id,
                        "name": key_minion.get("name"),
                        "strategy_star": strategy_star,
                        "metadata_tech_level": tech_level,
                    }
                )

    return {
        "strategies_source": strategies_source,
        "summary": {
            "mismatched_star_count": len(mismatched_stars),
            "non_minion_entry_count": len(non_minion_entries),
            "missing_metadata_count": len(missing_metadata),
        },
        "mismatched_stars": mismatched_stars,
        "non_minion_entries": non_minion_entries,
        "missing_metadata": missing_metadata,
    }


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build Battlegrounds card metadata from HearthstoneJSON.")
    parser.add_argument("--cards-en", default=DEFAULT_CARDS_EN_URL, help="enUS cards JSON URL or local path.")
    parser.add_argument("--cards-zh", default=DEFAULT_CARDS_ZH_URL, help="zhCN cards JSON URL or local path.")
    parser.add_argument("--output", required=True, help="Output metadata JSON path.")
    parser.add_argument("--version", default=None, help="Optional explicit version label.")
    parser.add_argument("--validate-strategies", default=None, help="Optional strategy JSON path for validation report.")
    parser.add_argument("--report-output", default=None, help="Optional validation report output JSON path.")
    args = parser.parse_args()

    metadata = build_battleground_card_metadata(
        cards_en_source=args.cards_en,
        cards_zh_source=args.cards_zh,
        version_label=args.version,
    )
    write_json(Path(args.output), metadata)

    if args.validate_strategies:
        report = build_strategy_validation_report(metadata, args.validate_strategies)
        if args.report_output:
            write_json(Path(args.report_output), report)
        else:
            print(json.dumps(report["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
