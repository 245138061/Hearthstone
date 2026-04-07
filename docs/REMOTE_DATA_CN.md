# 远程数据发布说明

本文档说明如何为 BGTactician 搭建一套可持续维护的阵容数据发布链路：

- GitHub Actions 定时拉取上游数据
- 转换为项目可用的 `strategies.json`
- 生成 `manifest.json`
- 发布到 GitHub Pages
- 供安卓客户端在线检查更新

## 目录结构

当前相关文件：

- [`.github/workflows/publish-strategy-data.yml`](/home/jerry/work/BGTactician/.github/workflows/publish-strategy-data.yml)
- [`tools/build_pages_bundle.py`](/home/jerry/work/BGTactician/tools/build_pages_bundle.py)
- [`tools/import_external_strategies.py`](/home/jerry/work/BGTactician/tools/import_external_strategies.py)
- [`tools/strategy_translations_zhCN.json`](/home/jerry/work/BGTactician/tools/strategy_translations_zhCN.json)

## 工作流说明

GitHub Actions 工作流会在两个场景执行：

1. 手动触发 `workflow_dispatch`
2. 每天定时执行一次

当前定时配置：

```yaml
schedule:
  - cron: "15 2 * * *"
```

这是 UTC 时间每天 `02:15`，换算成中国时间是每天 `10:15`。

## 数据来源

默认上游来源如下：

- 阵容数据  
  `https://static.zerotoheroes.com/hearthstone/data/battlegrounds-strategies/bgs-comps-strategies.gz.json`
- 英文语言包  
  `https://static.firestoneapp.com/data/i18n/enUS.json?v=1196-main`
- 中文语言包  
  `https://static.firestoneapp.com/data/i18n/zhCN.json?v=1196-main`

其中：

- `bgs-comps-strategies` 提供阵容、核心牌、强度、难度、英文攻略提示
- `zhCN.json` 提供阵容名中文翻译
- `strategy_translations_zhCN.json` 负责补齐目前项目里手工整理的中文运营说明

## 构建产物

工作流会生成一个静态站点目录，主要包含以下文件：

### `manifest.json`

示例结构：

```json
{
  "manifest_format": "bgtactician.pages.v1",
  "schema_version": 1,
  "channel": "stable",
  "version": "2026.04.07",
  "updated_at": "2026-04-07T07:14:38Z",
  "default_locale": "zhCN",
  "files": {
    "zhCN": {
      "path": "strategies.json",
      "url": "./strategies.json",
      "catalog_version": "2026.04.07-firestone-zhCN",
      "sha256": "..."
    },
    "enUS": {
      "path": "strategies.enUS.json",
      "url": "./strategies.enUS.json",
      "catalog_version": "2026.04.07-firestone-enUS",
      "sha256": "..."
    }
  }
}
```

客户端应优先请求这个文件，而不是直接请求 `strategies.json`。

原因：

- 先拿到版本号和哈希
- 小文件更适合频繁检查
- 可以根据 `default_locale` 和 `files` 字段做多语言切换
- 可以在客户端做 `sha256` 校验，避免坏数据直接覆盖缓存

### `strategies.json`

默认中文策略库，供国内用户直接使用。

### `strategies.enUS.json`

英文备用策略库，可作为调试或多语言兜底数据源。

### `index.html`

一个简单的静态说明页，便于手工查看当前发布版本。

## 本地调试

可以直接在本地执行：

```bash
python tools/build_pages_bundle.py --output-dir site
```

如果你已经提前下载好了上游 JSON，也可以指定本地文件：

```bash
python tools/build_pages_bundle.py \
  --output-dir site \
  --strategies /tmp/bgtactician-import/bgs-comps-strategies.json \
  --locale-zh /tmp/bgtactician-import/zhCN.json \
  --locale-en /tmp/bgtactician-import/enUS.json
```

生成后目录中会包含：

- `site/manifest.json`
- `site/strategies.json`
- `site/strategies.enUS.json`
- `site/index.html`

## 启用 GitHub Pages

在 GitHub 仓库中执行：

1. 打开 `Settings`
2. 进入 `Pages`
3. 找到 `Build and deployment`
4. 将 `Source` 设置为 `GitHub Actions`

启用后，工作流中的 `deploy-pages` 步骤会自动发布站点。

## 推荐客户端更新流程

安卓端建议按下面顺序更新：

1. 启动时先读本地缓存
2. 后台请求 `manifest.json`
3. 比较远程 `version`
4. 如有更新，再下载对应语言的 `strategies.json`
5. 校验下载内容的 `sha256`
6. 校验 JSON schema
7. 校验通过后原子替换本地缓存
8. 如果失败，继续使用旧缓存或 APK 内置数据

建议顺序：

- 远程缓存
- 本地缓存
- APK 内置数据

## 风险与建议

### 1. 不要只依赖 GitHub Pages

如果目标用户主要在中国大陆，GitHub Pages 可能会慢、超时或阶段性不可用。

更稳的做法：

- GitHub Actions 继续负责构建
- 发布结果同步到国内对象存储 CDN
- GitHub Pages 作为备用源

### 2. 不要让客户端接受任意 URL

生产环境不建议保留“用户随便填写 JSON 地址然后覆盖本地数据”的逻辑。

建议改成：

- 固定 `manifest.json` 域名
- 只允许 `https`
- 可选地做域名白名单

### 3. 永远保留 APK 内置兜底数据

远程数据再稳定，也不应该成为唯一数据源。

### 4. 发布前最好做校验

工作流中至少应该保证：

- 阵容数量不是 0
- 每条阵容都有 `id`
- 每条阵容都有 `key_minions`
- `manifest.json` 中的哈希和实际文件一致

## 后续可扩展方向

后面如果要继续增强，可以考虑：

1. 同时发布 `stable` 和 `beta` 两个频道
2. 增加 `schema_version` 升级策略
3. 在 `manifest.json` 中加入 `min_app_version`
4. 增加多 CDN 主备地址
5. 将卡牌中文名映射也纳入自动构建链路
