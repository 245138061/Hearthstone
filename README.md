# BGTactician

[中文说明](./README_CN.md)

BGTactician is an Android floating companion for Hearthstone Battlegrounds. This scaffold includes:

- A foreground overlay service based on `WindowManager` and `TYPE_APPLICATION_OVERLAY`
- A Jetpack Compose control panel for tribe selection, anomaly selection, duos toggle, recommendations, and strategy detail
- A richer strategy detail view with star-grouped key minions, upgrade tempo timeline, and a 7-slot positioning board
- Offline strategy data in `app/src/main/assets/strategies.json`
- A simple recommendation engine that filters builds by tribes, anomaly, and play mode
- Persistence for last-used filters and floating bubble position
- A manual online JSON update entry point with local cache fallback
- Overlay interaction controls, including opacity adjustment and notification-based passthrough recovery
- A GitHub Actions pipeline that builds `manifest.json` and remote strategy catalogs for GitHub Pages

## Prerequisites

- Android Studio with Android SDK for API 36
- JDK 17
- Overlay permission enabled on the device

## Project Notes

- The project has been verified with JDK 17 and Android SDK 36, and `./gradlew assembleDebug` succeeds in the prepared workspace.
- The default built-in catalog now points to the imported `strategies_zerotoheroes_zhCN.json` asset.
- Sample minion thumbnails now use packaged tribe-themed SVG assets under `app/src/main/assets/minions/tribes/`. You can replace them with real art by keeping the same `image_asset` mapping pattern in `strategies.json`.
- When passthrough mode is enabled, the floating bubble becomes non-touchable until restored from the foreground notification action.

## Remote Data Publishing

- Workflow: `.github/workflows/publish-strategy-data.yml`
- Bundle builder: `tools/build_pages_bundle.py`
- Importer: `tools/import_external_strategies.py`
- zhCN copy overrides: `tools/strategy_translations_zhCN.json`
- Chinese guide: `docs/REMOTE_DATA_CN.md`

The workflow runs every day and can also be triggered manually. It pulls the latest upstream Battlegrounds strategy JSON, generates:

- `manifest.json`
- `strategies.json` for `zhCN`
- `strategies.enUS.json` as a fallback feed

It then publishes them to GitHub Pages.

If the upstream strategy conversion fails temporarily, the bundle builder falls back to the bundled `strategies_zerotoheroes_zhCN.json` / `strategies_zerotoheroes_enUS.json` assets so the Pages deployment can still complete.

To give the Android app a fixed production update source, set a Gradle property before building:

```bash
echo 'BGT_REMOTE_MANIFEST_URL=https://<your-pages-host>/manifest.json' >> ~/.gradle/gradle.properties
```

Without that property, the app still works with built-in data and supports a manual manifest override in the debug UI.

Current live example:

```text
https://245138061.github.io/Hearthstone/manifest.json
```

Before the workflow can deploy, set your repository Pages source to `GitHub Actions` in:

- `Settings -> Pages -> Build and deployment -> Source -> GitHub Actions`

You can test the bundle builder locally with:

```bash
python tools/build_pages_bundle.py --output-dir site
```

For offline testing with already-downloaded files:

```bash
python tools/build_pages_bundle.py \
  --output-dir site \
  --strategies /tmp/bgtactician-import/bgs-comps-strategies.json \
  --locale-zh /tmp/bgtactician-import/zhCN.json \
  --locale-en /tmp/bgtactician-import/enUS.json
```

## Suggested Next Steps

1. Open the project in Android Studio and let it install the Android SDK / JDK 17 if needed.
2. Run `./gradlew assembleDebug` after Android Studio or your local environment provides JDK 17 and the required SDK packages.
3. Point the Android client at your `manifest.json` instead of downloading arbitrary JSON URLs.
4. Add hash validation and domain pinning before trusting remote data in production.
5. Replace placeholder minion art with optimized WebP assets.
