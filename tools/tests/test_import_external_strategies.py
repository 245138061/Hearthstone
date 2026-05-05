from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import import_external_strategies
from import_external_strategies import SourceUrls, convert


def write_json(path: Path, data: object) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class ImportExternalStrategiesTest(unittest.TestCase):

    def test_convert_skips_comp_without_key_minions(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            strategies_path = root / "strategies.json"
            locale_path = root / "locale.json"
            card_metadata_path = root / "cards.json"

            write_json(
                strategies_path,
                [
                    {
                        "compId": "pirate_exodia",
                        "name": "#N/A",
                        "patchNumber": 241135,
                        "cards": [],
                        "difficulty": "#N/A",
                        "powerLevel": "#N/A",
                        "tips": [
                            {
                                "tip": "Example tip",
                                "whenToCommit": "Example signal",
                            }
                        ],
                    },
                    {
                        "compId": "dragon_test",
                        "name": "Dragon Test",
                        "patchNumber": 241135,
                        "cards": [
                            {
                                "name": "Test Dragon",
                                "cardId": "TEST_MINION",
                                "status": "CORE",
                                "finalBoardWeight": 1,
                            }
                        ],
                        "difficulty": "Easy",
                        "powerLevel": "A",
                        "tips": [
                            {
                                "tip": "Scale it",
                                "whenToCommit": "Find Test Dragon",
                            }
                        ],
                    },
                ],
            )
            write_json(locale_path, {"bgs-comp": {"dragon_test": "测试龙"}})
            write_json(
                card_metadata_path,
                [
                    {
                        "id": "TEST_MINION",
                        "name": "Test Dragon",
                        "type": "MINION",
                        "techLevel": 5,
                    }
                ],
            )

            result = convert(
                SourceUrls(
                    strategies=str(strategies_path),
                    locale=str(locale_path),
                    card_metadata=str(card_metadata_path),
                ),
                version_label="test",
                language="enUS",
            )

            self.assertEqual(["dragon_test"], [comp["id"] for comp in result["comps"]])
            self.assertEqual(
                [{"comp_id": "pirate_exodia", "reason": "no_key_minions"}],
                [issue.__dict__ for issue in import_external_strategies.LAST_CONVERSION_ISSUES],
            )


if __name__ == "__main__":
    unittest.main()
