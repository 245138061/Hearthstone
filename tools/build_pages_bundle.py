#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from import_external_strategies import SourceUrls, convert, load_json


DEFAULT_STRATEGIES_URL = "https://static.zerotoheroes.com/hearthstone/data/battlegrounds-strategies/bgs-comps-strategies.gz.json"
DEFAULT_LOCALE_EN_URL = "https://static.firestoneapp.com/data/i18n/enUS.json?v=1196-main"
DEFAULT_LOCALE_ZH_URL = "https://static.firestoneapp.com/data/i18n/zhCN.json?v=1196-main"
DEFAULT_CARD_RULES_URL = "https://static.firestoneapp.com/data/cards/card-rules.gz.json"
DEFAULT_TRANSLATIONS_ZH = Path(__file__).with_name("strategy_translations_zhCN.json")

REQUIRED_COMP_FIELDS = {
    "id",
    "name",
    "tier",
    "difficulty",
    "required_tribes",
    "allowed_anomalies",
    "recommended_mode",
    "overview",
    "early_strategy",
    "late_strategy",
    "upgrade_turns",
    "positioning_hints",
    "key_minions",
}


def sha256_text(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def write_json(path: Path, data: dict[str, Any]) -> tuple[int, str]:
    raw = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    path.write_text(raw)
    return len(raw.encode("utf-8")), sha256_text(raw)


def validate_catalog(catalog: dict[str, Any], locale: str) -> None:
    comps = catalog.get("comps")
    if not isinstance(comps, list) or not comps:
        raise ValueError(f"{locale} catalog has no comps")

    for comp in comps:
        missing = REQUIRED_COMP_FIELDS - set(comp.keys())
        if missing:
            raise ValueError(f"{locale} comp {comp.get('id')} missing fields: {sorted(missing)}")
        if not comp["id"]:
            raise ValueError(f"{locale} comp contains empty id")
        if not comp["key_minions"]:
            raise ValueError(f"{locale} comp {comp['id']} has no key minions")


def build_bundle(
    output_dir: Path,
    strategies_source: str,
    locale_en_source: str,
    locale_zh_source: str,
    card_rules_source: str,
    translations_zh_source: str,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc)
    base_version = timestamp.strftime("%Y.%m.%d")

    zh_catalog = convert(
        SourceUrls(strategies=strategies_source, locale=locale_zh_source),
        version_label=f"{base_version}-firestone-zhCN",
        language="zhCN",
        translations=load_json(translations_zh_source),
    )
    en_catalog = convert(
        SourceUrls(strategies=strategies_source, locale=locale_en_source),
        version_label=f"{base_version}-firestone-enUS",
        language="enUS",
    )

    validate_catalog(zh_catalog, "zhCN")
    validate_catalog(en_catalog, "enUS")

    zh_size, zh_sha = write_json(output_dir / "strategies.json", zh_catalog)
    en_size, en_sha = write_json(output_dir / "strategies.enUS.json", en_catalog)
    card_rules = load_json(card_rules_source)
    if not isinstance(card_rules, dict) or not card_rules:
        raise ValueError("card rules payload is empty")
    card_rules_size, card_rules_sha = write_json(output_dir / "card-rules.json", card_rules)

    manifest = {
        "manifest_format": "bgtactician.pages.v1",
        "schema_version": 1,
        "channel": "stable",
        "version": base_version,
        "updated_at": timestamp.isoformat().replace("+00:00", "Z"),
        "default_locale": "zhCN",
        "files": {
            "zhCN": {
                "path": "strategies.json",
                "url": "./strategies.json",
                "catalog_version": zh_catalog["version"],
                "sha256": zh_sha,
                "size_bytes": zh_size,
            },
            "enUS": {
                "path": "strategies.enUS.json",
                "url": "./strategies.enUS.json",
                "catalog_version": en_catalog["version"],
                "sha256": en_sha,
                "size_bytes": en_size,
            },
        },
        "support_files": {
            "cardRules": {
                "path": "card-rules.json",
                "url": "./card-rules.json",
                "catalog_version": f"{base_version}-firestone-card-rules",
                "sha256": card_rules_sha,
                "size_bytes": card_rules_size,
            }
        },
        "sources": {
            "strategies": strategies_source,
            "card_rules": card_rules_source,
            "locales": {
                "zhCN": locale_zh_source,
                "enUS": locale_en_source,
            },
        },
    }
    write_json(output_dir / "manifest.json", manifest)

    (output_dir / ".nojekyll").write_text("")
    (output_dir / "index.html").write_text(
        f"""<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>BGTactician Data Feed</title>
    <style>
      :root {{
        color-scheme: light;
        --bg: #0c1722;
        --panel: #122334;
        --text: #e8f0f7;
        --muted: #9fb1c4;
        --accent: #7ed3ff;
      }}
      body {{
        margin: 0;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        background: radial-gradient(circle at top, #173149, var(--bg));
        color: var(--text);
      }}
      main {{
        max-width: 760px;
        margin: 0 auto;
        padding: 48px 20px 64px;
      }}
      .card {{
        background: rgba(18, 35, 52, 0.92);
        border: 1px solid rgba(126, 211, 255, 0.16);
        border-radius: 18px;
        padding: 20px;
        margin-top: 16px;
      }}
      a {{
        color: var(--accent);
      }}
      code {{
        color: #c6e6ff;
      }}
      p {{
        color: var(--muted);
        line-height: 1.6;
      }}
    </style>
  </head>
  <body>
    <main>
      <h1>BGTactician Data Feed</h1>
      <p>Updated at {manifest["updated_at"]}. This site is generated by GitHub Actions and serves the remote Battlegrounds strategy feed.</p>
      <div class="card">
        <p><strong>Manifest</strong>: <a href="./manifest.json">manifest.json</a></p>
        <p><strong>Default zhCN catalog</strong>: <a href="./strategies.json">strategies.json</a></p>
        <p><strong>Fallback enUS catalog</strong>: <a href="./strategies.enUS.json">strategies.enUS.json</a></p>
        <p><strong>Card rules</strong>: <a href="./card-rules.json">card-rules.json</a></p>
        <p><strong>Version</strong>: <code>{manifest["version"]}</code></p>
        <p><strong>Schema</strong>: <code>{manifest["schema_version"]}</code></p>
      </div>
    </main>
  </body>
</html>
""",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the GitHub Pages bundle for BGTactician strategy data.")
    parser.add_argument("--output-dir", required=True, help="Output directory for the static site bundle.")
    parser.add_argument("--strategies", default=DEFAULT_STRATEGIES_URL, help="Strategies JSON URL or local file.")
    parser.add_argument("--locale-en", default=DEFAULT_LOCALE_EN_URL, help="English locale JSON URL or local file.")
    parser.add_argument("--locale-zh", default=DEFAULT_LOCALE_ZH_URL, help="Chinese locale JSON URL or local file.")
    parser.add_argument("--card-rules", default=DEFAULT_CARD_RULES_URL, help="Firestone card rules URL or local file.")
    parser.add_argument(
        "--translations-zh",
        default=str(DEFAULT_TRANSLATIONS_ZH),
        help="Optional manual zhCN text overrides file.",
    )
    args = parser.parse_args()

    build_bundle(
        output_dir=Path(args.output_dir),
        strategies_source=args.strategies,
        locale_en_source=args.locale_en,
        locale_zh_source=args.locale_zh,
        card_rules_source=args.card_rules,
        translations_zh_source=args.translations_zh,
    )


if __name__ == "__main__":
    main()
