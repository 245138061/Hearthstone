#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from build_bgs_card_metadata import (
    DEFAULT_CARDS_EN_URL,
    DEFAULT_CARDS_ZH_URL,
    build_battleground_card_metadata,
)
from import_external_strategies import SourceUrls, convert, load_json


DEFAULT_STRATEGIES_URL = "https://static.zerotoheroes.com/hearthstone/data/battlegrounds-strategies/bgs-comps-strategies.gz.json"
DEFAULT_LOCALE_EN_URL = "https://static.firestoneapp.com/data/i18n/enUS.json?v=1196-main"
DEFAULT_LOCALE_ZH_URL = "https://static.firestoneapp.com/data/i18n/zhCN.json?v=1196-main"
DEFAULT_CARD_RULES_URL = "https://static.firestoneapp.com/data/cards/card-rules.gz.json"
DEFAULT_CARD_STATS_URL = "https://static.zerotoheroes.com/api/bgs/card-stats/mmr-100/all-time/overview-from-hourly.gz.json"
DEFAULT_HERO_STATS_URL = "https://static.zerotoheroes.com/api/bgs/hero-stats/mmr-100/all-time/overview-from-hourly.gz.json"
DEFAULT_CARD_NAMES_ZH_URL = "https://api.hearthstonejson.com/v1/latest/zhCN/cards.json"
DEFAULT_CARD_METADATA_EN_URL = DEFAULT_CARDS_EN_URL
DEFAULT_CARD_METADATA_ZH_URL = DEFAULT_CARDS_ZH_URL
DEFAULT_TRANSLATIONS_ZH = Path(__file__).with_name("strategy_translations_zhCN.json")
DEFAULT_FALLBACK_ZH_CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/assets/strategies_zerotoheroes_zhCN.json"
DEFAULT_FALLBACK_EN_CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/assets/strategies_zerotoheroes_enUS.json"

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

ASCII_WORD_RE = re.compile(r"[A-Za-z]{3,}")
REQUIRED_SITE_FILES = (
    "manifest.json",
    "strategies.json",
    "strategies.enUS.json",
    "card-rules.json",
    "card-stats.json",
    "hero-stats.json",
    "bgs-card-metadata.json",
    ".nojekyll",
    "index.html",
)
EXPECTED_CATALOG_FILES = {
    "zhCN": "strategies.json",
    "enUS": "strategies.enUS.json",
}
EXPECTED_SUPPORT_FILES = {
    "cardRules": "card-rules.json",
    "cardStats": "card-stats.json",
    "heroStats": "hero-stats.json",
    "cardMetadata": "bgs-card-metadata.json",
}


def sha256_text(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def write_json(path: Path, data: dict[str, Any]) -> tuple[int, str]:
    raw = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    path.write_text(raw, encoding="utf-8")
    return len(raw.encode("utf-8")), sha256_text(raw)


def read_json_file(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_catalog(
    catalog: dict[str, Any],
    locale: str,
    *,
    strict_zh_localization: bool = False,
) -> None:
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

    if locale == "zhCN":
        violations = collect_zh_catalog_localization_violations(comps)
        if violations:
            sample = "\n".join(violations[:12])
            extra = len(violations) - min(len(violations), 12)
            suffix = f"\n... and {extra} more" if extra > 0 else ""
            message = (
                "zhCN catalog still contains untranslated English content.\n"
                f"{sample}{suffix}"
            )
            if strict_zh_localization:
                raise ValueError(message)
            print(f"[warn] {message}", file=sys.stderr)


def collect_zh_catalog_localization_violations(comps: list[dict[str, Any]]) -> list[str]:
    violations: list[str] = []

    for comp in comps:
        comp_id = str(comp.get("id") or "<unknown>")
        for field in ("overview", "early_strategy", "late_strategy", "when_to_commit"):
            value = str(comp.get(field) or "").strip()
            if value and contains_ascii_word(value):
                violations.append(f"{comp_id}.{field}: {value}")

        for minion in comp.get("key_minions", []):
            name = str(minion.get("name") or "").strip()
            if name and contains_ascii_word(name):
                card_id = str(minion.get("card_id") or "<unknown>")
                violations.append(f"{comp_id}.key_minions[{card_id}]: {name}")

    return violations


def contains_ascii_word(value: str) -> bool:
    return ASCII_WORD_RE.search(value) is not None


def load_catalog_fallback(
    path: Path,
    locale: str,
    *,
    strict_zh_localization: bool = False,
) -> dict[str, Any]:
    catalog = load_json(str(path))
    if not isinstance(catalog, dict):
        raise ValueError(f"{locale} fallback catalog is invalid: {path}")
    validate_catalog(catalog, locale, strict_zh_localization=strict_zh_localization)
    return catalog


def build_catalog_with_fallback(
    source_urls: SourceUrls,
    version_label: str,
    language: str,
    fallback_path: Path,
    translations: dict[str, Any] | None = None,
    *,
    strict_zh_localization: bool = False,
) -> tuple[dict[str, Any], str, bool]:
    try:
        catalog = convert(
            source_urls,
            version_label=version_label,
            language=language,
            translations=translations,
        )
        validate_catalog(
            catalog,
            language,
            strict_zh_localization=strict_zh_localization,
        )
        return catalog, source_urls.strategies, False
    except Exception as error:
        if not fallback_path.exists():
            raise
        print(
            f"[warn] failed to build {language} catalog from upstream, using fallback {fallback_path}: {error}",
            file=sys.stderr,
        )
        catalog = load_catalog_fallback(
            fallback_path,
            language,
            strict_zh_localization=strict_zh_localization,
        )
        return catalog, str(fallback_path), True


def build_bundle(
    output_dir: Path,
    strategies_source: str,
    locale_en_source: str,
    locale_zh_source: str,
    card_rules_source: str,
    card_stats_source: str,
    hero_stats_source: str,
    card_names_zh_source: str,
    card_metadata_en_source: str,
    card_metadata_zh_source: str,
    translations_zh_source: str,
    fallback_zh_catalog: str,
    fallback_en_catalog: str,
    strict_zh_localization: bool = False,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc)
    base_version = timestamp.strftime("%Y.%m.%d")

    zh_catalog, zh_catalog_source, zh_used_fallback = build_catalog_with_fallback(
        SourceUrls(
            strategies=strategies_source,
            locale=locale_zh_source,
            card_names=card_names_zh_source,
            card_metadata=card_metadata_zh_source,
        ),
        version_label=f"{base_version}-firestone-zhCN",
        language="zhCN",
        fallback_path=Path(fallback_zh_catalog),
        translations=load_json(translations_zh_source),
        strict_zh_localization=strict_zh_localization,
    )
    en_catalog, en_catalog_source, en_used_fallback = build_catalog_with_fallback(
        SourceUrls(
            strategies=strategies_source,
            locale=locale_en_source,
            card_metadata=card_metadata_en_source,
        ),
        version_label=f"{base_version}-firestone-enUS",
        language="enUS",
        fallback_path=Path(fallback_en_catalog),
        strict_zh_localization=strict_zh_localization,
    )

    zh_size, zh_sha = write_json(output_dir / "strategies.json", zh_catalog)
    en_size, en_sha = write_json(output_dir / "strategies.enUS.json", en_catalog)
    card_rules = load_json(card_rules_source)
    if not isinstance(card_rules, dict) or not card_rules:
        raise ValueError("card rules payload is empty")
    card_rules_size, card_rules_sha = write_json(output_dir / "card-rules.json", card_rules)
    card_stats = load_json(card_stats_source)
    if not isinstance(card_stats, dict) or not isinstance(card_stats.get("cardStats"), list) or not card_stats["cardStats"]:
        raise ValueError("card stats payload is empty")
    card_stats_size, card_stats_sha = write_json(output_dir / "card-stats.json", card_stats)
    hero_stats = load_json(hero_stats_source)
    if not isinstance(hero_stats, dict) or not isinstance(hero_stats.get("heroStats"), list) or not hero_stats["heroStats"]:
        raise ValueError("hero stats payload is empty")
    hero_stats_size, hero_stats_sha = write_json(output_dir / "hero-stats.json", hero_stats)
    card_metadata = build_battleground_card_metadata(
        cards_en_source=card_metadata_en_source,
        cards_zh_source=card_metadata_zh_source,
        version_label=f"{base_version}-hsjson-bgs-card-metadata",
    )
    cards = card_metadata.get("cards")
    if not isinstance(cards, dict) or not cards:
        raise ValueError("card metadata payload is empty")
    card_metadata_size, card_metadata_sha = write_json(output_dir / "bgs-card-metadata.json", card_metadata)

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
            },
            "cardStats": {
                "path": "card-stats.json",
                "url": "./card-stats.json",
                "catalog_version": f"{base_version}-zerotoheroes-card-stats",
                "sha256": card_stats_sha,
                "size_bytes": card_stats_size,
            },
            "heroStats": {
                "path": "hero-stats.json",
                "url": "./hero-stats.json",
                "catalog_version": f"{base_version}-zerotoheroes-hero-stats",
                "sha256": hero_stats_sha,
                "size_bytes": hero_stats_size,
            },
            "cardMetadata": {
                "path": "bgs-card-metadata.json",
                "url": "./bgs-card-metadata.json",
                "catalog_version": card_metadata["version"],
                "sha256": card_metadata_sha,
                "size_bytes": card_metadata_size,
            }
        },
        "sources": {
            "strategies": {
                "primary": strategies_source,
                "zhCN": zh_catalog_source,
                "enUS": en_catalog_source,
            },
            "card_rules": card_rules_source,
            "card_stats": card_stats_source,
            "hero_stats": hero_stats_source,
            "card_metadata": {
                "enUS": card_metadata_en_source,
                "zhCN": card_metadata_zh_source,
            },
            "locales": {
                "zhCN": locale_zh_source,
                "enUS": locale_en_source,
            },
        },
    }
    if zh_used_fallback or en_used_fallback:
        manifest["build_notes"] = {
            "strategy_catalog_fallback": {
                "zhCN": zh_used_fallback,
                "enUS": en_used_fallback,
            }
        }
    write_json(output_dir / "manifest.json", manifest)

    (output_dir / ".nojekyll").write_text("", encoding="utf-8")
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
        <p><strong>Card stats</strong>: <a href="./card-stats.json">card-stats.json</a></p>
        <p><strong>Hero stats</strong>: <a href="./hero-stats.json">hero-stats.json</a></p>
        <p><strong>BG card metadata</strong>: <a href="./bgs-card-metadata.json">bgs-card-metadata.json</a></p>
        <p><strong>Version</strong>: <code>{manifest["version"]}</code></p>
        <p><strong>Schema</strong>: <code>{manifest["schema_version"]}</code></p>
      </div>
    </main>
  </body>
</html>
""",
        encoding="utf-8",
    )
    verify_bundle(output_dir)


def verify_bundle(output_dir: Path) -> None:
    missing_files = [name for name in REQUIRED_SITE_FILES if not (output_dir / name).exists()]
    if missing_files:
        raise ValueError(f"bundle is missing required files: {missing_files}")

    manifest_path = output_dir / "manifest.json"
    manifest = read_json_file(manifest_path)

    if manifest.get("manifest_format") != "bgtactician.pages.v1":
        raise ValueError("manifest_format is invalid")
    if manifest.get("schema_version") != 1:
        raise ValueError("schema_version is invalid")

    files = manifest.get("files")
    if not isinstance(files, dict):
        raise ValueError("manifest files section is invalid")
    for locale, expected_path in EXPECTED_CATALOG_FILES.items():
        entry = files.get(locale)
        if not isinstance(entry, dict):
            raise ValueError(f"manifest missing locale entry: {locale}")
        verify_manifest_entry(
            output_dir=output_dir,
            entry=entry,
            expected_path=expected_path,
            label=f"catalog:{locale}",
        )
        validate_catalog(read_json_file(output_dir / expected_path), locale)

    support_files = manifest.get("support_files")
    if not isinstance(support_files, dict):
        raise ValueError("manifest support_files section is invalid")
    for resource_key, expected_path in EXPECTED_SUPPORT_FILES.items():
        entry = support_files.get(resource_key)
        if not isinstance(entry, dict):
            raise ValueError(f"manifest missing support file entry: {resource_key}")
        verify_manifest_entry(
            output_dir=output_dir,
            entry=entry,
            expected_path=expected_path,
            label=f"support:{resource_key}",
        )
        validate_support_payload(resource_key, read_json_file(output_dir / expected_path))

    sources = manifest.get("sources")
    if not isinstance(sources, dict) or "strategies" not in sources:
        raise ValueError("manifest sources section is invalid")


def verify_manifest_entry(
    *,
    output_dir: Path,
    entry: dict[str, Any],
    expected_path: str,
    label: str,
) -> None:
    path = str(entry.get("path") or "").strip()
    if path != expected_path:
        raise ValueError(f"{label} path mismatch: expected {expected_path}, got {path or '<empty>'}")

    url = str(entry.get("url") or "").strip()
    if url != f"./{expected_path}":
        raise ValueError(f"{label} url mismatch: expected ./{expected_path}, got {url or '<empty>'}")

    target = output_dir / expected_path
    raw = target.read_text(encoding="utf-8")
    actual_size = len(raw.encode("utf-8"))
    actual_sha = sha256_text(raw)

    if entry.get("size_bytes") != actual_size:
        raise ValueError(f"{label} size_bytes mismatch")
    if entry.get("sha256") != actual_sha:
        raise ValueError(f"{label} sha256 mismatch")
    if not str(entry.get("catalog_version") or "").strip():
        raise ValueError(f"{label} catalog_version is empty")


def validate_support_payload(resource_key: str, payload: Any) -> None:
    if resource_key == "cardRules":
        if not isinstance(payload, dict) or not payload:
            raise ValueError("cardRules payload is empty")
        return

    if resource_key == "cardStats":
        card_stats = payload.get("cardStats") if isinstance(payload, dict) else None
        if not isinstance(card_stats, list) or not card_stats:
            raise ValueError("cardStats payload is empty")
        return

    if resource_key == "heroStats":
        hero_stats = payload.get("heroStats") if isinstance(payload, dict) else None
        if not isinstance(hero_stats, list) or not hero_stats:
            raise ValueError("heroStats payload is empty")
        return

    if resource_key == "cardMetadata":
        cards = payload.get("cards") if isinstance(payload, dict) else None
        if not isinstance(cards, dict) or not cards:
            raise ValueError("cardMetadata payload is empty")
        return

    raise ValueError(f"unknown support payload key: {resource_key}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the GitHub Pages bundle for BGTactician strategy data.")
    parser.add_argument("--output-dir", required=True, help="Output directory for the static site bundle.")
    parser.add_argument("--strategies", default=DEFAULT_STRATEGIES_URL, help="Strategies JSON URL or local file.")
    parser.add_argument("--locale-en", default=DEFAULT_LOCALE_EN_URL, help="English locale JSON URL or local file.")
    parser.add_argument("--locale-zh", default=DEFAULT_LOCALE_ZH_URL, help="Chinese locale JSON URL or local file.")
    parser.add_argument("--card-rules", default=DEFAULT_CARD_RULES_URL, help="Firestone card rules URL or local file.")
    parser.add_argument("--card-stats", default=DEFAULT_CARD_STATS_URL, help="Card stats JSON URL or local file.")
    parser.add_argument("--hero-stats", default=DEFAULT_HERO_STATS_URL, help="Hero stats JSON URL or local file.")
    parser.add_argument("--card-names-zh", default=DEFAULT_CARD_NAMES_ZH_URL, help="zhCN card names JSON URL or local file.")
    parser.add_argument("--card-metadata-en", default=DEFAULT_CARD_METADATA_EN_URL, help="enUS cards JSON URL or local file.")
    parser.add_argument("--card-metadata-zh", default=DEFAULT_CARD_METADATA_ZH_URL, help="zhCN cards JSON URL or local file.")
    parser.add_argument(
        "--fallback-zh-catalog",
        default=str(DEFAULT_FALLBACK_ZH_CATALOG),
        help="Fallback zhCN strategy catalog used when upstream conversion fails.",
    )
    parser.add_argument(
        "--fallback-en-catalog",
        default=str(DEFAULT_FALLBACK_EN_CATALOG),
        help="Fallback enUS strategy catalog used when upstream conversion fails.",
    )
    parser.add_argument(
        "--translations-zh",
        default=str(DEFAULT_TRANSLATIONS_ZH),
        help="Optional manual zhCN text overrides file.",
    )
    parser.add_argument(
        "--strict-zh-localization",
        action="store_true",
        help="Fail the build when zhCN strategy text still contains untranslated English content.",
    )
    args = parser.parse_args()

    build_bundle(
        output_dir=Path(args.output_dir),
        strategies_source=args.strategies,
        locale_en_source=args.locale_en,
        locale_zh_source=args.locale_zh,
        card_rules_source=args.card_rules,
        card_stats_source=args.card_stats,
        hero_stats_source=args.hero_stats,
        card_names_zh_source=args.card_names_zh,
        card_metadata_en_source=args.card_metadata_en,
        card_metadata_zh_source=args.card_metadata_zh,
        translations_zh_source=args.translations_zh,
        fallback_zh_catalog=args.fallback_zh_catalog,
        fallback_en_catalog=args.fallback_en_catalog,
        strict_zh_localization=args.strict_zh_localization,
    )


if __name__ == "__main__":
    main()
