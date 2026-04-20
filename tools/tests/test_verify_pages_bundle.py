from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from build_pages_bundle import verify_bundle, sha256_text


def make_catalog(locale: str) -> dict[str, object]:
    if locale == "zhCN":
        name = "\u9f99\u961f"
        overview = "\u4e3b\u6253\u6210\u957f"
        early_strategy = "\u5148\u62ff\u7ecf\u6d4e\u724c"
        late_strategy = "\u56f4\u7ed5\u4e3b\u6838\u6210\u578b"
        label = "\u5148\u624b\u4f4d"
        note = "\u53ef\u4ee5\u653e\u529f\u80fd\u5355\u4f4d"
        minion_name = "\u8bd7\u5fc3\u9f99\u7532"
    else:
        name = "Dragon Board"
        overview = "Scale around the carry"
        early_strategy = "Take economy first"
        late_strategy = "Protect the scaling slot"
        label = "Frontline"
        note = "Place utility minions first"
        minion_name = "Poet"

    return {
        "version": f"2026.04.20-{locale}",
        "comps": [
            {
                "id": f"test_{locale.lower()}",
                "name": name,
                "tier": "T1",
                "difficulty": "\u4e2d" if locale == "zhCN" else "Medium",
                "required_tribes": ["Dragon"],
                "allowed_anomalies": ["\u65e0\u7578\u53d8"] if locale == "zhCN" else ["None"],
                "recommended_mode": "BOTH",
                "overview": overview,
                "early_strategy": early_strategy,
                "late_strategy": late_strategy,
                "upgrade_turns": ["3"],
                "positioning_hints": [
                    {
                        "slot": 1,
                        "label": label,
                        "note": note,
                    }
                ],
                "key_minions": [
                    {
                        "id": 1,
                        "name": minion_name,
                        "star": 5,
                        "phase": "\u4e3b\u6838" if locale == "zhCN" else "Core",
                    }
                ],
            }
        ],
    }


def write_json(path: Path, data: object) -> tuple[int, str]:
    raw = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    path.write_text(raw, encoding="utf-8")
    return len(raw.encode("utf-8")), sha256_text(raw)


class VerifyPagesBundleTest(unittest.TestCase):
    def build_site(self, root: Path) -> None:
        strategies_size, strategies_sha = write_json(root / "strategies.json", make_catalog("zhCN"))
        strategies_en_size, strategies_en_sha = write_json(root / "strategies.enUS.json", make_catalog("enUS"))
        card_rules_size, card_rules_sha = write_json(
            root / "card-rules.json",
            {"TEST_CARD": {"bgsMinionTypesRules": {"needTypesInLobby": ["Dragon"]}}},
        )
        card_stats_size, card_stats_sha = write_json(
            root / "card-stats.json",
            {"cardStats": [{"cardId": "TEST_CARD", "totalPlayed": 1}]},
        )
        hero_stats_size, hero_stats_sha = write_json(
            root / "hero-stats.json",
            {"heroStats": [{"heroCardId": "TEST_HERO", "averagePosition": 4.0}]},
        )
        card_metadata_size, card_metadata_sha = write_json(
            root / "bgs-card-metadata.json",
            {"version": "2026.04.20-meta", "cards": {"TEST_CARD": {"name": "Test", "type": "MINION"}}},
        )

        manifest = {
            "manifest_format": "bgtactician.pages.v1",
            "schema_version": 1,
            "channel": "stable",
            "version": "2026.04.20",
            "updated_at": "2026-04-20T00:00:00Z",
            "default_locale": "zhCN",
            "files": {
                "zhCN": {
                    "path": "strategies.json",
                    "url": "./strategies.json",
                    "catalog_version": "2026.04.20-zhCN",
                    "sha256": strategies_sha,
                    "size_bytes": strategies_size,
                },
                "enUS": {
                    "path": "strategies.enUS.json",
                    "url": "./strategies.enUS.json",
                    "catalog_version": "2026.04.20-enUS",
                    "sha256": strategies_en_sha,
                    "size_bytes": strategies_en_size,
                },
            },
            "support_files": {
                "cardRules": {
                    "path": "card-rules.json",
                    "url": "./card-rules.json",
                    "catalog_version": "2026.04.20-cardRules",
                    "sha256": card_rules_sha,
                    "size_bytes": card_rules_size,
                },
                "cardStats": {
                    "path": "card-stats.json",
                    "url": "./card-stats.json",
                    "catalog_version": "2026.04.20-cardStats",
                    "sha256": card_stats_sha,
                    "size_bytes": card_stats_size,
                },
                "heroStats": {
                    "path": "hero-stats.json",
                    "url": "./hero-stats.json",
                    "catalog_version": "2026.04.20-heroStats",
                    "sha256": hero_stats_sha,
                    "size_bytes": hero_stats_size,
                },
                "cardMetadata": {
                    "path": "bgs-card-metadata.json",
                    "url": "./bgs-card-metadata.json",
                    "catalog_version": "2026.04.20-cardMetadata",
                    "sha256": card_metadata_sha,
                    "size_bytes": card_metadata_size,
                },
            },
            "sources": {
                "strategies": {
                    "primary": "https://example.test/strategies.json",
                    "zhCN": "https://example.test/zhCN.json",
                    "enUS": "https://example.test/enUS.json",
                }
            },
        }
        write_json(root / "manifest.json", manifest)
        (root / ".nojekyll").write_text("", encoding="utf-8")
        (root / "index.html").write_text("<html></html>\n", encoding="utf-8")

    def test_verify_bundle_accepts_valid_site(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            site_dir = Path(temp_dir)
            self.build_site(site_dir)
            verify_bundle(site_dir)

    def test_verify_bundle_rejects_manifest_hash_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            site_dir = Path(temp_dir)
            self.build_site(site_dir)
            manifest_path = site_dir / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"]["zhCN"]["sha256"] = "broken"
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "catalog:zhCN sha256 mismatch"):
                verify_bundle(site_dir)


if __name__ == "__main__":
    unittest.main()
