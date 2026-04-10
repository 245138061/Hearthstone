#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen


COMP_TRIBE_OVERRIDES = {
    "deep_blue_nagas": ["Naga"],
    "shield_mechs": ["Mech"],
    "buff_shop_demons": ["Demon"],
    "scam": [],
    "silithid_beasts": ["Beast"],
    "attack_undead": ["Undead"],
    "refresh_elementals": ["Elemental"],
    "apm_pirate": ["Pirate"],
    "boost_shop_quilboar": ["Quilboar"],
    "handbuff_murloc": ["Murloc"],
    "end_of_turn_murlocs": ["Murloc"],
    "tier_2_ballers": [],
    "cycle_quilboar": ["Quilboar"],
    "end_of_turn_naga": ["Naga"],
    "carapace_undead": ["Undead"],
    "beasts_beetles": ["Beast"],
    "spell_cycle": [],
    "stuntdrake_dragons": ["Dragon"],
    "bomber_mechs": ["Mech"],
    "apexis_mechs": ["Mech"],
    "self_damage_beasts": ["Beast"],
    "overflow_undead": ["Undead"],
    "deathrattle_quilboar": ["Quilboar"],
    "lord_of_ruins_demons": ["Demon"],
    "whelp_dragon": ["Dragon"],
    "deathrattle_naga": ["Naga"],
}

TRIBE_TOKEN_MAP = {
    "beast": "Beast",
    "beasts": "Beast",
    "demon": "Demon",
    "demons": "Demon",
    "dragon": "Dragon",
    "dragons": "Dragon",
    "elemental": "Elemental",
    "elementals": "Elemental",
    "mech": "Mech",
    "mechs": "Mech",
    "murloc": "Murloc",
    "murlocs": "Murloc",
    "naga": "Naga",
    "nagas": "Naga",
    "pirate": "Pirate",
    "pirates": "Pirate",
    "quilboar": "Quilboar",
    "undead": "Undead",
}

STATUS_PHASE_MAP = {
    "CORE": ("主核", 5),
    "ADDON": ("补强", 4),
    "RECOMMENDED": ("过渡", 3),
    "CYCLE": ("经济", 2),
}

POWER_TO_TIER = {
    "S": "T0",
    "A": "T1",
    "B": "T2",
    "C": "T2",
    "": "T2",
}

DIFFICULTY_TO_LABEL = {
    "Easy": "低",
    "Medium": "中",
    "Hard": "高",
    "": "中",
}

DIFFICULTY_TO_TURNS = {
    "Easy": ["3 铸币升 2", "7 铸币升 3", "9 铸币升 4"],
    "Medium": ["3 铸币升 2", "7 铸币升 3", "9 铸币升 4", "11 铸币升 5"],
    "Hard": ["2 铸币升 2", "5 铸币升 3", "8 铸币升 4", "10 铸币升 5", "13 铸币冲 6"],
    "": ["3 铸币升 2", "7 铸币升 3", "9 铸币升 4", "11 铸币升 5"],
}

TRIBE_ASSET_MAP = {
    "Beast": "minions/tribes/beast.svg",
    "Demon": "minions/tribes/demon.svg",
    "Dragon": "minions/tribes/dragon.svg",
    "Elemental": "minions/tribes/elemental.svg",
    "Mech": "minions/tribes/mech.svg",
    "Murloc": "minions/tribes/murloc.svg",
    "Naga": "minions/tribes/naga.svg",
    "Pirate": "minions/tribes/pirate.svg",
    "Quilboar": "minions/tribes/quilboar.svg",
    "Undead": "minions/tribes/undead.svg",
}

RENDER_BASE_URL = "https://art.hearthstonejson.com/v1/render/latest"


@dataclass
class SourceUrls:
    strategies: str
    locale: str


def load_json(source: str) -> Any:
    if source.startswith("http://") or source.startswith("https://"):
        request = Request(
            source,
            headers={
                "User-Agent": "Mozilla/5.0 (compatible; BGTacticianBot/1.0; +https://github.com/245138061/Hearthstone)",
                "Accept": "application/json,text/plain,*/*",
                "Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8",
            },
        )
        with urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    return json.loads(Path(source).read_text())


def infer_required_tribes(comp_id: str, cards: list[dict[str, Any]]) -> list[str]:
    if comp_id in COMP_TRIBE_OVERRIDES:
        return COMP_TRIBE_OVERRIDES[comp_id]

    tokens = re.split(r"[_\\-\\s]+", comp_id.lower())
    tribes: list[str] = []
    for token in tokens:
        mapped = TRIBE_TOKEN_MAP.get(token)
        if mapped and mapped not in tribes:
            tribes.append(mapped)

    if not tribes:
        # Fallback: infer from card asset names if a direct tribe token wasn't present.
        joined_names = " ".join(card.get("name", "").lower() for card in cards[:8])
        for token, mapped in TRIBE_TOKEN_MAP.items():
            if token in joined_names and mapped not in tribes:
                tribes.append(mapped)

    return tribes[:2]


def build_render_url(card_id: str | None, language: str) -> str | None:
    if not card_id:
        return None
    return f"{RENDER_BASE_URL}/{language}/256x/{card_id}.png"


def to_int(value: Any) -> int | None:
    if value in (None, ""):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def normalize_cards(
    cards: list[dict[str, Any]],
    primary_tribe: str | None,
    language: str,
) -> list[dict[str, Any]]:
    prioritized = sorted(
        cards,
        key=lambda card: (
            {"CORE": 0, "ADDON": 1, "RECOMMENDED": 2}.get(card.get("status", ""), 3),
            -int(card.get("finalBoardWeight", 0) or 0),
            card.get("name", ""),
        ),
    )
    normalized: list[dict[str, Any]] = []
    for index, card in enumerate(prioritized, start=1):
        status = card.get("status", "")
        final_board_weight = to_int(card.get("finalBoardWeight"))
        phase, star = STATUS_PHASE_MAP.get(status, ("补位", min(6, 2 + index // 2)))
        card_id = card.get("cardId")
        normalized.append(
            {
                "id": index,
                "name": card.get("name", card.get("cardId", "Unknown Card")),
                "star": star,
                "phase": phase,
                "status_raw": status or None,
                "final_board_weight": final_board_weight,
                "card_id": card_id,
                "image_url": build_render_url(card_id, language),
                "image_asset": TRIBE_ASSET_MAP.get(primary_tribe or "", None),
            }
        )
    return normalized


def to_overview(name: str, tip_text: str | None, when_to_commit: str | None, language: str) -> str:
    parts = [name]
    if when_to_commit:
        if language == "zhCN":
            parts.append(f"成型信号: {when_to_commit}")
        else:
            parts.append(f"Commit signal: {when_to_commit}")
    if tip_text:
        parts.append(tip_text)
    return " | ".join(parts)


def default_early_strategy(when_to_commit: str | None, language: str) -> str:
    if when_to_commit:
        if language == "zhCN":
            return f"看到 {when_to_commit} 再明确转型。"
        return f"Commit when you see {when_to_commit}."
    if language == "zhCN":
        return "先观察经济件和第一张真正的主核，再决定是否全力转型。"
    return "Scout for economy and the first real payoff card before fully pivoting."


def default_late_strategy(language: str) -> str:
    if language == "zhCN":
        return "围绕主核补全终局阵型，并优先保护最关键的成长位。"
    return "Complete the final board around your key scaling engine and protect the carry slot."


def build_positioning_hints(required_tribes: list[str]) -> list[dict[str, Any]]:
    backline_label = "成长核心" if required_tribes else "功能核心"
    return [
        {"slot": 1, "label": "先手位", "note": "优先放清盾、送头或开场功能单位。"},
        {"slot": 7, "label": backline_label, "note": "尾位保护主核，避免被先手直接换掉。"},
    ]


def convert(
    source_urls: SourceUrls,
    version_label: str | None = None,
    language: str = "enUS",
    translations: dict[str, Any] | None = None,
) -> dict[str, Any]:
    raw_comps = load_json(source_urls.strategies)
    locale = load_json(source_urls.locale)
    localized_names = locale.get("bgs-comp", {})
    translations = translations or {}

    comps: list[dict[str, Any]] = []
    for raw in raw_comps:
        comp_id = (raw.get("compId") or "").strip()
        if not comp_id:
            continue
        translation = translations.get(comp_id, {})
        name = translation.get("name") or localized_names.get(comp_id) or raw.get("name") or comp_id
        tips = raw.get("tips") or []
        first_tip = tips[0] if tips else {}
        tip_text = translation.get("tip") or first_tip.get("tip")
        when_to_commit = translation.get("whenToCommit") or first_tip.get("whenToCommit")
        difficulty = raw.get("difficulty", "")
        source_patch_number = to_int(raw.get("patchNumber"))
        power_level = (raw.get("powerLevel") or "").strip() or None
        required_tribes = infer_required_tribes(comp_id, raw.get("cards", []))
        primary_tribe = required_tribes[0] if required_tribes else None
        overview = to_overview(name, tip_text, when_to_commit, language)
        early_strategy = translation.get("earlyStrategy") or default_early_strategy(when_to_commit, language)
        late_strategy = translation.get("lateStrategy") or tip_text or default_late_strategy(language)

        comps.append(
            {
                "id": comp_id,
                "name": name,
                "tier": POWER_TO_TIER.get(raw.get("powerLevel", ""), "T2"),
                "difficulty": DIFFICULTY_TO_LABEL.get(difficulty, "中"),
                "power_level": power_level,
                "required_tribes": required_tribes,
                "allowed_anomalies": ["无畸变"],
                "recommended_mode": "BOTH",
                "when_to_commit": when_to_commit,
                "source_patch_number": source_patch_number,
                "overview": overview,
                "early_strategy": early_strategy,
                "late_strategy": late_strategy,
                "upgrade_turns": DIFFICULTY_TO_TURNS.get(difficulty, DIFFICULTY_TO_TURNS[""]),
                "positioning_hints": build_positioning_hints(required_tribes),
                "key_minions": normalize_cards(raw.get("cards", []), primary_tribe, language),
            }
        )

    version = version_label or "2026.04.07-import"
    return {
        "version": version,
        "comps": comps,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert external BG strategy JSON into BGTactician schema.")
    parser.add_argument("--strategies", required=True, help="Local path or URL for battleground strategies JSON.")
    parser.add_argument("--locale", required=True, help="Local path or URL for locale JSON.")
    parser.add_argument("--output", required=True, help="Output JSON path.")
    parser.add_argument("--version", default=None, help="Optional explicit version label.")
    parser.add_argument("--language", default="enUS", help="Display language for generated text, e.g. enUS or zhCN.")
    parser.add_argument("--translations", default=None, help="Optional JSON file with per-comp translated text overrides.")
    args = parser.parse_args()

    manual_translations = load_json(args.translations) if args.translations else None

    result = convert(
        SourceUrls(strategies=args.strategies, locale=args.locale),
        version_label=args.version,
        language=args.language,
        translations=manual_translations,
    )

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
