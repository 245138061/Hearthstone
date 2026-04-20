from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from verify_hero_data_sources import merge_hero_name_index, verify_hero_data_sources


def write_json(path: Path, data: object) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class VerifyHeroDataSourcesTest(unittest.TestCase):

    def test_merge_hero_name_index_enriches_missing_hero_from_metadata(self) -> None:
        merged = merge_hero_name_index(
            hero_name_index={
                "version": "test",
                "heroes": [
                    {
                        "heroCardId": "OLD_HERO",
                        "name": "Old Hero",
                        "localizedName": "老英雄",
                        "aliases": ["Old Hero", "老英雄"],
                    }
                ],
            },
            card_metadata={
                "cards": {
                    "NEW_HERO": {
                        "name": "New Hero",
                        "localized_name": "新英雄",
                        "type": "HERO",
                    }
                }
            },
        )

        merged_ids = {hero["heroCardId"] for hero in merged["heroes"]}
        self.assertEqual({"OLD_HERO", "NEW_HERO"}, merged_ids)

    def test_verify_hero_data_sources_accepts_metadata_backfilled_hero(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_json(
                root / "hero-stats.json",
                {"heroStats": [{"heroCardId": "NEW_HERO", "averagePosition": 4.0}]},
            )
            write_json(
                root / "bgs-card-metadata.json",
                {
                    "cards": {
                        "NEW_HERO": {
                            "name": "New Hero",
                            "localized_name": "新英雄",
                            "type": "HERO",
                        }
                    }
                },
            )
            write_json(
                root / "bgs_hero_name_index.json",
                {"version": "test", "heroes": []},
            )

            verify_hero_data_sources(
                hero_stats_path=root / "hero-stats.json",
                card_metadata_path=root / "bgs-card-metadata.json",
                hero_name_index_path=root / "bgs_hero_name_index.json",
            )

    def test_verify_hero_data_sources_rejects_uncovered_hero(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_json(
                root / "hero-stats.json",
                {"heroStats": [{"heroCardId": "MISSING_HERO", "averagePosition": 4.0}]},
            )
            write_json(
                root / "bgs-card-metadata.json",
                {"cards": {}},
            )
            write_json(
                root / "bgs_hero_name_index.json",
                {"version": "test", "heroes": []},
            )

            with self.assertRaisesRegex(ValueError, "MISSING_HERO"):
                verify_hero_data_sources(
                    hero_stats_path=root / "hero-stats.json",
                    card_metadata_path=root / "bgs-card-metadata.json",
                    hero_name_index_path=root / "bgs_hero_name_index.json",
                )


if __name__ == "__main__":
    unittest.main()
